package app.opensefer.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opensefer.core.domain.BookmarkRepository
import app.opensefer.core.domain.LibraryRepository
import app.opensefer.core.domain.ReadingLanguage
import app.opensefer.core.domain.ReadingPreferences
import app.opensefer.core.domain.ReadingPreferencesRepository
import app.opensefer.core.domain.ReadingTheme
import app.opensefer.core.domain.TextRepository
import app.opensefer.core.model.BookContents
import app.opensefer.core.model.Bookmark
import app.opensefer.core.model.LibraryBook
import app.opensefer.core.model.ReadingItem
import app.opensefer.core.model.ReadingPosition
import app.opensefer.core.model.TocLeaf
import app.opensefer.core.model.bookmarkId
import app.opensefer.core.model.toReadingItems
import app.opensefer.ui.UiStrings
import app.opensefer.ui.userMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * State for the **continuous** reader: the whole book as one flat list of [rows] — one per segment —
 * with each passage's text loaded as it comes near the screen. One object in, intents out
 * (BLUEPRINT §10.1).
 *
 * `@Immutable`: every field is a `val` of an immutable type, so Compose may treat the whole state as
 * stable and skip recomposition when the instance is unchanged.
 */
@Immutable
data class ReaderUiState(
    val bookTitle: String,
    val heBookTitle: String,
    val contents: BookContents? = null,
    val rows: List<ReaderRow> = emptyList(),
    val preferences: ReadingPreferences = ReadingPreferences(),
    val loading: Boolean = true,
    val error: String? = null,
    /** A one‑shot "scroll here" (initial restore, a jump); the screen reports it back when done. */
    val scrollRequest: ScrollRequest? = null,
    val saved: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(), // this book's, newest first
    val bookmarkedIds: Set<String> = emptySet(),
    val selection: List<SegmentRef> = emptyList(), // in reading order; non‑empty = selection mode
    val current: SegmentRef? = null, // the segment at the top of the screen…
    val currentBookmarkId: String? = null, // …and the id a bookmark on it would have
    val progress: Float = 0f,
    val sheet: ReaderSheet? = null,
    val message: String? = null, // a one‑shot snackbar
)

data class ScrollRequest(val index: Int, val offset: Int, val id: Int)

enum class ReaderSheet { Contents, Bookmarks, Display }

@OptIn(FlowPreview::class)
@Suppress("TooManyFunctions") // one small intent per reader action (UDF); splitting would only scatter them
class ReaderViewModel(
    private val textRepository: TextRepository,
    private val preferencesRepository: ReadingPreferencesRepository,
    private val libraryRepository: LibraryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookTitle: String,
    private val heBookTitle: String,
    private val startTref: String? = null,
    private val startSegment: Int = 0,
    private val computation: CoroutineDispatcher = Dispatchers.Default, // injectable for tests
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(
            bookTitle = bookTitle,
            heBookTitle = heBookTitle,
            preferences = preferencesRepository.preferences.value,
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    // Structure + loaded passages. Touched only on the main thread (viewModelScope), so no locking.
    private var items: List<ReadingItem> = emptyList()
    private var leaves: List<TocLeaf> = emptyList()
    private var passageIndex: Map<String, Int> = emptyMap()
    private val passages = mutableMapOf<Int, PassageState>()
    private val loads = mutableMapOf<Int, Job>()
    private var rebuildScheduled = false

    // Position: reported continuously by the screen, written to the library once scrolling settles.
    private var restored = false
    private var scrollRequestId = 0
    private val pendingPosition = MutableStateFlow<ReadingPosition?>(null)
    private var lastSaved: ReadingPosition? = null

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs -> _state.update { it.copy(preferences = prefs) } }
        }
        viewModelScope.launch {
            libraryRepository.books.collect { books ->
                _state.update { it.copy(saved = books.any { book -> book.title == bookTitle }) }
            }
        }
        viewModelScope.launch {
            bookmarkRepository.bookmarks.collect { all ->
                val mine = all.filter { it.bookTitle == bookTitle }
                _state.update { it.copy(bookmarks = mine, bookmarkedIds = mine.mapTo(HashSet()) { b -> b.id }) }
            }
        }
        viewModelScope.launch { pendingPosition.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect(::save) }
        loadContents()
    }

    fun loadContents() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            textRepository.getContents(bookTitle).fold(
                onSuccess = { open(it) },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    /** Builds the structure, loads the start passage (and its neighbours), then reveals it in place. */
    private suspend fun open(contents: BookContents) {
        val flattened = withContext(computation) { contents.toReadingItems() }
        if (contents.leaves.isEmpty()) {
            _state.update { it.copy(loading = false, error = UiStrings.ERROR_BOOK) }
            return
        }
        items = flattened
        leaves = contents.leaves
        passageIndex = leaves.withIndex().associate { (i, leaf) -> leaf.tref to i }
        fillLibraryDetails(contents)

        val start = resolveStart()
        val startPassage = passageIndex[start.tref] ?: 0
        listOf(startPassage, startPassage - 1, startPassage + 1).mapNotNull { ensure(it) }.joinAll()
        val rows = rebuildNow()
        val target = rows.rowIndexOf(leaves[startPassage].tref, start.segment, atPassageStart = start.offset == 0)
        _state.update {
            it.copy(
                contents = contents,
                loading = false,
                scrollRequest = ScrollRequest(target, start.offset, ++scrollRequestId),
            )
        }
    }

    private class Start(val tref: String, val segment: Int, val offset: Int)

    private fun resolveStart(): Start {
        if (startTref != null && startTref in passageIndex) return Start(startTref, startSegment, 0)
        val book = libraryRepository.books.value.firstOrNull { it.title == bookTitle }
        val resume = book?.lastTref?.takeIf { it in passageIndex }
        return if (book != null && resume != null) {
            Start(resume, book.lastSegment, book.lastOffset)
        } else {
            Start(leaves.first().tref, 0, 0)
        }
    }

    /** Keeps a saved book's cover metadata (category, author) filled in. */
    private fun fillLibraryDetails(contents: BookContents) {
        val book = libraryRepository.books.value.firstOrNull { it.title == bookTitle } ?: return
        val details = contents.details
        val missing = (book.category == null && details.category != null) ||
            (book.heAuthor == null && details.heAuthor != null)
        if (missing) libraryRepository.updateDetails(bookTitle, details.category, details.heCategory, details.heAuthor)
    }

    /** Starts loading passage [p] unless it's loaded, loading, or (without [retry]) failed. */
    private fun ensure(p: Int, retry: Boolean = false): Job? {
        val inFlight = loads[p]?.takeIf { it.isActive }
        val skip = p !in leaves.indices || inFlight != null || when (passages[p]) {
            is PassageState.Loaded -> true
            is PassageState.Failed -> !retry
            PassageState.Loading, null -> false
        }
        if (skip) return inFlight
        passages[p] = PassageState.Loading
        return viewModelScope.launch {
            val result = textRepository.getText(leaves[p].tref)
            passages[p] = result.fold(
                onSuccess = { PassageState.Loaded(it) },
                onFailure = { PassageState.Failed(it.userMessage()) },
            )
            scheduleRebuild()
        }.also { loads[p] = it }
    }

    /** Coalesces the rebuilds of passages that finish together into one. */
    private fun scheduleRebuild() {
        if (rebuildScheduled) return
        rebuildScheduled = true
        viewModelScope.launch {
            yield()
            rebuildScheduled = false
            rebuildNow()
        }
    }

    private fun rebuildNow(): List<ReaderRow> =
        buildReaderRows(items, passageIndex, passages).also { rows -> _state.update { it.copy(rows = rows) } }

    /** The screen shows rows [firstRow]..[lastRow]: load those passages plus [PRELOAD] on each side. */
    fun onVisibleRange(firstRow: Int, lastRow: Int) {
        if (!restored) return // the first layout is at the top of the book, not where we're going
        val rows = _state.value.rows
        val first = rows.passageNear(firstRow) ?: return
        val last = rows.passageNear(lastRow) ?: first
        for (p in (first - PRELOAD)..(last + PRELOAD)) ensure(p)
    }

    private fun List<ReaderRow>.passageNear(index: Int): Int? =
        (index.coerceIn(0, lastIndex) until size).firstNotNullOfOrNull { i -> this[i].passage.takeIf { it >= 0 } }

    /** The first visible row is [firstRow], scrolled [offset] px into it. */
    fun onScrolled(firstRow: Int, offset: Int) {
        if (!restored) return // don't save the top of the book before the saved place is restored
        val rows = _state.value.rows
        val row = rows.getOrNull(firstRow) ?: return
        val top = row as? SegmentRow ?: rows.segmentAtOrBefore(firstRow) ?: return
        val ref = SegmentRef(top.passage, top.segment.index)
        if (_state.value.current != ref) {
            _state.update {
                it.copy(
                    current = ref,
                    currentBookmarkId = bookmarkId(top.leaf.tref, top.segment.index),
                    progress = progressOf(top),
                )
            }
        }
        pendingPosition.value = ReadingPosition(
            tref = top.leaf.tref,
            segment = top.segment.index,
            label = placeLabel(top.leaf, top.segment, _state.value.contents?.details?.heSegmentName),
            progress = progressOf(top),
            offset = if (row is SegmentRow) offset else 0,
        )
    }

    private fun progressOf(row: SegmentRow): Float {
        val chapter = (passages[row.passage] as? PassageState.Loaded)?.chapter
        val within = chapter?.segments?.indexOfFirst { it.index == row.segment.index }
            ?.takeIf { it >= 0 }
            ?.let { it.toFloat() / chapter.segments.size } ?: 0f
        return ((row.passage + within) / leaves.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    fun onScrollRequestHandled(id: Int) {
        _state.update { if (it.scrollRequest?.id == id) it.copy(scrollRequest = null) else it }
        restored = true
    }

    /** Writes the latest position now (the app is going to the background, or the screen closes). */
    fun flushPosition() {
        pendingPosition.value?.let(::save)
    }

    private fun save(position: ReadingPosition) {
        if (position == lastSaved) return
        lastSaved = position
        libraryRepository.updatePosition(bookTitle, position)
    }

    override fun onCleared() {
        flushPosition()
    }

    /** Scrolls to [tref] (a contents entry or a bookmark), loading it first if needed. */
    fun jumpTo(tref: String, segment: Int = 0) {
        val p = passageIndex[tref] ?: return
        _state.update { it.copy(sheet = null, selection = emptyList()) }
        viewModelScope.launch {
            ensure(p - 1)
            ensure(p, retry = true)?.join()
            val rows = rebuildNow()
            val index = rows.rowIndexOf(tref, segment)
            _state.update { it.copy(scrollRequest = ScrollRequest(index, 0, ++scrollRequestId)) }
        }
    }

    fun retry(passage: Int) {
        ensure(passage, retry = true)
        rebuildNow()
    }

    // Selection mode: tap verse numbers to pick segments, then copy (with the source) or bookmark.
    fun toggleSelection(ref: SegmentRef) = _state.update { s ->
        val selection = if (ref in s.selection) {
            s.selection - ref
        } else {
            (s.selection + ref).sortedWith(compareBy(SegmentRef::passage, SegmentRef::segment))
        }
        s.copy(selection = selection)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptyList()) }

    /** The selected segments as clipboard text (with a source line); clears the selection. */
    fun copySelection(): String? {
        val s = _state.value
        val parts = s.selection.groupBy { it.passage }.mapNotNull { (p, refs) ->
            val chapter = (passages[p] as? PassageState.Loaded)?.chapter ?: return@mapNotNull null
            val wanted = refs.mapTo(HashSet()) { it.segment }
            CopyPart(leaves[p], chapter, chapter.segments.filter { it.index in wanted })
        }.filter { it.segments.isNotEmpty() }
        if (parts.isEmpty()) return null
        _state.update { it.copy(selection = emptyList(), message = UiStrings.COPIED) }
        return buildCopyText(heBookTitle, parts, s.preferences.language, s.preferences.showNikud)
    }

    fun bookmarkSelection() {
        val first = _state.value.selection.firstOrNull() ?: return
        clearSelection()
        toggleBookmark(first)
    }

    /** Bookmarks the segment at the top of the screen (or removes its bookmark). */
    fun toggleBookmarkHere() {
        _state.value.current?.let(::toggleBookmark)
    }

    fun toggleBookmark(ref: SegmentRef) {
        val leaf = leaves.getOrNull(ref.passage) ?: return
        val chapter = (passages[ref.passage] as? PassageState.Loaded)?.chapter ?: return
        val segment = chapter.segments.firstOrNull { it.index == ref.segment } ?: return
        val id = bookmarkId(leaf.tref, segment.index)
        if (id in _state.value.bookmarkedIds) {
            bookmarkRepository.remove(id)
            _state.update { it.copy(message = UiStrings.BOOKMARK_REMOVED) }
        } else {
            bookmarkRepository.add(
                Bookmark(
                    bookTitle = bookTitle,
                    heBookTitle = heBookTitle,
                    tref = leaf.tref,
                    segment = segment.index,
                    label = placeLabel(leaf, segment, _state.value.contents?.details?.heSegmentName),
                    snippet = segment.snippet(),
                ),
            )
            _state.update { it.copy(message = UiStrings.BOOKMARK_ADDED) }
        }
    }

    fun removeBookmark(id: String) = bookmarkRepository.remove(id)

    /** Saves the book to the library (remembering the current place), or removes it. */
    fun toggleSaved() {
        if (_state.value.saved) {
            libraryRepository.remove(bookTitle)
            _state.update { it.copy(message = UiStrings.REMOVED_FROM_LIBRARY) }
        } else {
            val details = _state.value.contents?.details
            libraryRepository.add(
                LibraryBook(
                    title = bookTitle,
                    heTitle = heBookTitle,
                    category = details?.category,
                    heCategory = details?.heCategory,
                    heAuthor = details?.heAuthor,
                ),
            )
            lastSaved = null
            flushPosition() // so "continue reading" picks up right here
            _state.update { it.copy(message = UiStrings.SAVED_TO_LIBRARY) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // Reading preferences
    fun setFontScale(scale: Float) = preferencesRepository.setFontScale(scale)
    fun setTheme(theme: ReadingTheme) = preferencesRepository.setTheme(theme)
    fun setLanguage(language: ReadingLanguage) = preferencesRepository.setLanguage(language)
    fun toggleNikud() = preferencesRepository.setShowNikud(!_state.value.preferences.showNikud)

    // Sheets (reader UI state, not navigation)
    fun openSheet(sheet: ReaderSheet) = _state.update { it.copy(sheet = sheet) }
    fun closeSheet() = _state.update { it.copy(sheet = null) }

    private companion object {
        const val PRELOAD = 2 // passages loaded ahead of / behind the screen, so they're ready before they're seen
        const val SAVE_DEBOUNCE_MS = 600L
    }
}
