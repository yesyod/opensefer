package app.opensefer.core.data

import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * Platform directory for the stored texts: `Context.noBackupFilesDir` on Android, Application
 * Support (excluded from backups) on iOS — persistent, so the OS never silently deletes a book the
 * user saved for offline reading (ADR 0004).
 */
internal expect fun diskCacheDir(): String

/**
 * A small file‑backed `key → JSON` cache — the engine behind "every book you open is saved on the
 * device". One file per entry at `<root>/<namespace>/<sha256(key)>.json`. Writes land in a uniquely
 * named temp file and are atomically moved into place, so a crash mid‑write can never leave a torn
 * entry and concurrent writers of one key can't interleave. All IO runs on [io].
 *
 * The cache is an optimisation, never a source of failure: every IO error reads as a miss.
 */
class DiskCache(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = ::getTimeMillis,
) {

    /**
     * The cached value for [key], or null when absent, unreadable, or older than [maxAgeMillis]
     * (an entry "from the future" — the clock was moved back — counts as old too).
     */
    suspend fun read(namespace: String, key: String, maxAgeMillis: Long? = null): String? = withContext(io) {
        val path = pathOf(namespace, key)
        runCatching {
            val metadata = fileSystem.metadataOrNull(path) ?: return@runCatching null
            val modified = metadata.lastModifiedAtMillis
            val fresh = maxAgeMillis == null || modified == null || clock() - modified in 0..maxAgeMillis
            if (fresh) fileSystem.read(path) { readUtf8() } else null
        }.getOrNull()
    }

    /** Stores [value] under [key]; false when it couldn't be written (disk full, permissions). */
    suspend fun write(namespace: String, key: String, value: String): Boolean = withContext(io) {
        val path = pathOf(namespace, key)
        val dir = path.parent ?: return@withContext false
        val temp = dir / "${path.name}.${Random.nextLong().toULong()}$TEMP_SUFFIX"
        runCatching {
            fileSystem.createDirectories(dir)
            fileSystem.write(temp) { writeUtf8(value) }
            fileSystem.atomicMove(temp, path)
        }.onFailure {
            runCatching { fileSystem.delete(temp) } // don't leave half a file using up scarce space
        }.isSuccess
    }

    /** True when a non‑empty entry is stored for [key] (whatever its age). */
    suspend fun contains(namespace: String, key: String): Boolean = withContext(io) {
        runCatching { (fileSystem.metadataOrNull(pathOf(namespace, key))?.size ?: 0L) > 0L }.getOrDefault(false)
    }

    /** Total bytes on disk — shown next to the "free up space" action. */
    suspend fun sizeBytes(): Long = withContext(io) {
        runCatching {
            if (!fileSystem.exists(root)) return@runCatching 0L
            fileSystem.listRecursively(root).sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }
        }.getOrDefault(0L)
    }

    /** Deletes every entry except [keep] (namespace → keys), and any temp file left by a failed write. */
    suspend fun retainOnly(keep: Map<String, Set<String>>) {
        withContext(io) {
            val namespaces = runCatching { fileSystem.list(root) }.getOrDefault(emptyList())
            for (dir in namespaces) {
                val kept = keep[dir.name].orEmpty().mapTo(HashSet(), ::fileName)
                if (kept.isEmpty()) {
                    runCatching { fileSystem.deleteRecursively(dir) }
                    continue
                }
                val files = runCatching { fileSystem.list(dir) }.getOrDefault(emptyList())
                files.filterNot { it.name in kept }.forEach { runCatching { fileSystem.delete(it) } }
            }
        }
    }

    suspend fun clear() {
        withContext(io) { runCatching { fileSystem.deleteRecursively(root) } }
    }

    private fun pathOf(namespace: String, key: String): Path = root / namespace / fileName(key)

    private fun fileName(key: String): String = "${key.encodeUtf8().sha256().hex()}.json"

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }
}
