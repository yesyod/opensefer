package app.opensefer.core.domain

import app.opensefer.core.model.DownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps saved books fully on the device: downloads every passage of a book into the offline cache,
 * a few at a time, and marks the book [offline][app.opensefer.core.model.LibraryBook.offline] once all
 * of it is there. [startAutoDownloads] does this automatically for every saved book up to
 * [AUTO_DOWNLOAD_MAX_PASSAGES] passages (larger works — a whole Shulchan Arukh volume — wait for an
 * explicit [download]). Gentle on Sefaria: [PARALLEL] requests at most, one book at a time, and a
 * download stops at the first sign of being offline (it resumes on the next launch or tap).
 */
class BookDownloader(
    private val text: TextRepository,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    /** Books being downloaded right now, by title. */
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val lock = Mutex()
    private val queue = ArrayDeque<String>()
    private var worker: Job? = null
    private var autoStarted = false

    /** Queues [bookTitle] for a full offline download (no‑op if it's already queued or running). */
    fun download(bookTitle: String) {
        scope.launch {
            lock.withLock {
                if (bookTitle in queue || bookTitle in _progress.value) return@launch
                queue.addLast(bookTitle)
                if (worker == null) worker = scope.launch { drain() }
            }
        }
    }

    /**
     * Once per process: after the library has loaded, auto‑download every saved book that isn't fully
     * offline yet — and every book saved later. Runs after [startDelayMillis] so the first screen's own
     * requests go first.
     */
    fun startAutoDownloads(startDelayMillis: Long = AUTO_START_DELAY_MS) {
        if (autoStarted) return
        autoStarted = true
        scope.launch {
            library.loaded.first { it }
            delay(startDelayMillis)
            library.books
                .map { books -> books.filterNot { it.offline }.map { it.title }.toSet() }
                .distinctUntilChanged()
                .collect { titles -> titles.forEach { title -> prepare(title) } }
        }
    }

    /** Completes a saved book's cover details, and downloads it if it's small enough to do so unasked. */
    private suspend fun prepare(title: String) {
        val contents = text.getContents(title).getOrNull() ?: return
        val details = contents.details
        library.updateDetails(title, details.category, details.heCategory, details.heAuthor)
        if (contents.leaves.size <= AUTO_DOWNLOAD_MAX_PASSAGES) download(title)
    }

    /** Works through the queue; clears [worker] under the lock when done, so no queued book is ever stranded. */
    private suspend fun drain() {
        while (true) {
            val next = lock.withLock { queue.removeFirstOrNull().also { if (it == null) worker = null } } ?: return
            downloadNow(next)
        }
    }

    private suspend fun downloadNow(bookTitle: String) {
        val leaves = text.getContents(bookTitle).getOrNull()?.leaves ?: return
        var done = 0
        var complete = true
        _progress.update { it + (bookTitle to DownloadProgress(0, leaves.size)) }
        try {
            for (chunk in leaves.chunked(PARALLEL)) {
                val results = coroutineScope {
                    chunk.map { leaf -> async { text.cacheForOffline(leaf.tref) } }.awaitAll()
                }
                done += results.count { it.isSuccess }
                _progress.update { it + (bookTitle to DownloadProgress(done, leaves.size)) }
                if (results.any { it.exceptionOrNull() is DataError.Offline }) {
                    complete = false
                    break // no connection — don't hammer; the next launch (or tap) resumes from the cache
                }
                if (results.any { it.isFailure }) complete = false
            }
            if (complete) library.setOffline(bookTitle, true)
        } finally {
            _progress.update { it - bookTitle }
        }
    }

    companion object {
        /** Saved books up to this many passages download automatically (a Talmud tractate, a Tanakh book). */
        const val AUTO_DOWNLOAD_MAX_PASSAGES = 300
        private const val PARALLEL = 3
        private const val AUTO_START_DELAY_MS = 2_000L
    }
}
