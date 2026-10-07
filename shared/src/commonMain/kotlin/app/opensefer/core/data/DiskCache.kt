package app.opensefer.core.data

import app.opensefer.core.domain.OfflineStorage
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import kotlin.random.Random

/**
 * Platform directory for re‑downloadable content: `Context.cacheDir` on Android, `NSCachesDirectory`
 * on iOS. The OS may purge it under storage pressure — everything keeps working (it's re‑fetched).
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
) : OfflineStorage {

    /** The cached value for [key], or null when absent, unreadable, or older than [maxAgeMillis]. */
    suspend fun read(namespace: String, key: String, maxAgeMillis: Long? = null): String? = withContext(io) {
        val path = pathOf(namespace, key)
        runCatching {
            val metadata = fileSystem.metadataOrNull(path) ?: return@runCatching null
            val modified = metadata.lastModifiedAtMillis
            val fresh = maxAgeMillis == null || modified == null || clock() - modified <= maxAgeMillis
            if (fresh) fileSystem.read(path) { readUtf8() } else null
        }.getOrNull()
    }

    /** Stores [value] under [key]; failures (disk full, permissions) are silently ignored. */
    suspend fun write(namespace: String, key: String, value: String) {
        withContext(io) {
            runCatching {
                val path = pathOf(namespace, key)
                val dir = path.parent ?: return@runCatching
                fileSystem.createDirectories(dir)
                val temp = dir / "${path.name}.${Random.nextLong().toULong()}.tmp"
                fileSystem.write(temp) { writeUtf8(value) }
                fileSystem.atomicMove(temp, path)
            }
        }
    }

    suspend fun contains(namespace: String, key: String): Boolean = withContext(io) {
        runCatching { fileSystem.exists(pathOf(namespace, key)) }.getOrDefault(false)
    }

    /** Total bytes on disk — shown next to the "clear saved texts" action. */
    override suspend fun sizeBytes(): Long = withContext(io) {
        runCatching {
            if (!fileSystem.exists(root)) return@runCatching 0L
            fileSystem.listRecursively(root).sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }
        }.getOrDefault(0L)
    }

    override suspend fun clear() {
        withContext(io) { runCatching { fileSystem.deleteRecursively(root) } }
    }

    private fun pathOf(namespace: String, key: String): Path =
        root / namespace / "${key.encodeUtf8().sha256().hex()}.json"
}
