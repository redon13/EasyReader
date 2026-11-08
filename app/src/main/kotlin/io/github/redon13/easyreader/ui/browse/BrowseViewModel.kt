package io.github.redon13.easyreader.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.redon13.easyreader.AppContainer
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.CalibreBookParser
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.calibre.ReadingPosition
import io.github.redon13.easyreader.calibre.isFavourite
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.calibre.serverPosition
import io.github.redon13.easyreader.calibre.shelves
import io.github.redon13.easyreader.data.Session
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.BrowseQuery
import io.github.redon13.easyreader.data.browse.CategoryValue
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.data.browse.SmartShelfFilter
import io.github.redon13.easyreader.data.browse.SortField
import io.github.redon13.easyreader.data.browse.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the library screens share: the books, the current search/filters/sort, and syncing. */
class BrowseViewModel(container: AppContainer, val session: Session) : ViewModel() {
    private val repo = container.library
    private val sessions = container.sessions
    private val settings = container.settings
    private val downloads = container.downloads
    private val changes = container.changes

    /** All books in the library, or null until the stored copy has been read. */
    val books: StateFlow<List<CalibreBook>?> = repo.books(session.libraryId)
        .map { list -> list.mapNotNull { runCatching { CalibreBookParser.parse(it.id, it.rawJson) }.getOrNull() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Search text, kept as Compose state so typing stays smooth. */
    var search by mutableStateOf("")

    private val filters = MutableStateFlow<Map<FilterCategory, String>>(emptyMap())
    private val smartShelf = MutableStateFlow<SmartShelfFilter?>(null)

    val sort: StateFlow<SortOrder> = settings.sortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder())

    val listView: StateFlow<Boolean> = settings.listView
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val query: StateFlow<BrowseQuery> = combine(snapshotFlow { search }, filters, sort, smartShelf) { s, f, o, smart ->
        BrowseQuery(s, f, o, smart)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowseQuery())

    /** Books matching the search and filters, in the chosen order. */
    val results: StateFlow<List<CalibreBook>> = combine(books, query) { b, q -> Browse.apply(b.orEmpty(), q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Newest additions first, whatever the chosen sort. */
    val recentlyAdded: StateFlow<List<CalibreBook>> = books
        .map { Browse.sort(it.orEmpty(), SortOrder(SortField.DATE_ADDED, descending = true)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The whole library in the chosen order. */
    val allBooks: StateFlow<List<CalibreBook>> = combine(books, sort) { b, o -> Browse.sort(b.orEmpty(), o) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The latest reading position for each book: from Calibre (as of the last
     * sync) or from this device, whichever is newer.
     */
    val positions: StateFlow<Map<Int, ReadingPosition>> =
        combine(books, container.positions.localPositions(session.libraryId)) { b, local ->
            val out = HashMap<Int, ReadingPosition>()
            b.orEmpty().forEach { book -> book.serverPosition?.let { out[book.id] = it } }
            local.forEach { (id, p) -> if (p.isNewerThan(out[id])) out[id] = p }
            out as Map<Int, ReadingPosition>
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Books started but not finished, most recently read first. */
    val continueReading: StateFlow<List<Pair<CalibreBook, ReadingPosition>>> = combine(books, positions) { b, p ->
        b.orEmpty()
            .filter { !it.isFinished }
            .mapNotNull { book -> p[book.id]?.takeIf { it.progress > 0.0 && it.progress < 0.995 }?.let { book to it } }
            .sortedByDescending { it.second.timestamp }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Favourite books, most recently added first. */
    val favourites: StateFlow<List<CalibreBook>> = recentlyAdded
        .map { list -> list.filter { it.isFavourite } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Books not started and not finished, most recently added first. */
    val unread: StateFlow<List<CalibreBook>> = combine(recentlyAdded, positions) { list, p ->
        list.filter { !it.isFinished && (p[it.id]?.progress ?: 0.0) <= 0.0 }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The owner's shelves with how many books are on each, A to Z (including empty ones made on this device). */
    val shelves: StateFlow<List<CategoryValue>> = combine(books, settings.emptyShelves) { b, empty ->
        val counts = Browse.values(b.orEmpty(), FilterCategory.SHELVES).associate { it.value to it.count }
        (counts.keys + empty).distinctBy { it.lowercase() }
            .map { CategoryValue(it, counts[it] ?: 0) }
            .sortedBy { Browse.normalise(it.value) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Calibre's virtual libraries and saved searches. */
    val smartShelves: StateFlow<List<SmartShelfFilter>> = repo.smartShelves(session.libraryId)
        .map { list -> list.map { e -> SmartShelfFilter(e.name, e.bookIds.split(',').mapNotNull { it.toIntOrNull() }.toSet()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleFavourite(book: CalibreBook) {
        viewModelScope.launch { changes.setFavourite(session.libraryId, book.id, !book.isFavourite) }
    }

    fun setFinished(book: CalibreBook, finished: Boolean) {
        viewModelScope.launch { changes.setFinished(session.libraryId, book.id, finished) }
    }

    /** Puts the book on exactly these shelves. */
    fun setShelves(book: CalibreBook, shelves: List<String>) {
        viewModelScope.launch {
            changes.setShelves(session.libraryId, book.id, shelves)
            // A shelf with a book on it is kept by Calibre from now on.
            settings.updateEmptyShelves { empty -> empty.filterNot { e -> shelves.any { it.equals(e, ignoreCase = true) } }.toSet() }
        }
    }

    fun createShelf(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || shelves.value.any { it.value.equals(clean, ignoreCase = true) }) return
        viewModelScope.launch { settings.updateEmptyShelves { it + clean } }
    }

    fun renameShelf(old: String, new: String) {
        val clean = new.trim()
        if (clean.isEmpty() || clean == old) return
        viewModelScope.launch {
            books.value.orEmpty().filter { b -> b.shelves.any { it == old } }.forEach { b ->
                changes.setShelves(session.libraryId, b.id, b.shelves.map { if (it == old) clean else it })
            }
            settings.updateEmptyShelves { empty -> if (old in empty) empty - old + clean else empty }
            if (filters.value[FilterCategory.SHELVES] == old) setFilter(FilterCategory.SHELVES, clean)
        }
    }

    /** Removes the shelf label from every book. The books stay in the library. */
    fun deleteShelf(name: String) {
        viewModelScope.launch {
            books.value.orEmpty().filter { b -> b.shelves.any { it == name } }.forEach { b ->
                changes.setShelves(session.libraryId, b.id, b.shelves.filter { it != name })
            }
            settings.updateEmptyShelves { it - name }
            if (filters.value[FilterCategory.SHELVES] == name) setFilter(FilterCategory.SHELVES, null)
        }
    }

    fun showSmartShelf(shelf: SmartShelfFilter) {
        search = ""
        filters.value = emptyMap()
        smartShelf.value = shelf
    }

    fun clearSmartShelf() {
        smartShelf.value = null
    }

    /** Ids of books whose files are on this device. */
    val downloaded: StateFlow<Set<Int>> = downloads.downloadedIds(session.libraryId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun removeDownload(bookId: Int) {
        viewModelScope.launch { downloads.remove(session.libraryId, bookId) }
    }

    val syncing: StateFlow<Boolean> = repo.syncing
    val libraries: StateFlow<Map<String, String>> = repo.libraries
    val columns: StateFlow<ColumnCheck?> = repo.columns
    val lastSynced: StateFlow<Long?> = repo.lastSynced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    init {
        sync()
    }

    fun book(id: Int): CalibreBook? = books.value?.firstOrNull { it.id == id }

    /**
     * Values for one filter category, counted among the books that match
     * everything else currently chosen (so picking one never leads to nothing).
     */
    fun categoryValues(category: FilterCategory): List<CategoryValue> {
        val q = query.value
        val base = Browse.apply(books.value.orEmpty(), q.copy(filters = q.filters - category))
        return Browse.values(base, category)
    }

    /** Chooses (or with null, clears) the filter for one category. */
    fun setFilter(category: FilterCategory, value: String?) {
        filters.value = if (value == null) filters.value - category else filters.value + (category to value)
    }

    /** Shows only the books with this value, e.g. everything by one author. */
    fun showOnly(category: FilterCategory, value: String) {
        search = ""
        smartShelf.value = null
        filters.value = mapOf(category to value)
    }

    fun clearSearchAndFilters() {
        search = ""
        smartShelf.value = null
        filters.value = emptyMap()
    }

    fun setSort(order: SortOrder) {
        viewModelScope.launch { settings.saveSortOrder(order) }
    }

    fun setListView(list: Boolean) {
        viewModelScope.launch { settings.saveListView(list) }
    }

    fun sync() {
        viewModelScope.launch {
            repo.sync(session)?.let { _messages.tryEmit("Couldn't sync: $it") }
        }
    }

    fun coverUrl(book: CalibreBook): String =
        session.client.thumbnailUrl(session.libraryId, book.id, book.lastModified)

    fun switchLibrary(id: String, name: String) {
        viewModelScope.launch { sessions.switchLibrary(id, name) }
    }

    fun signOut() {
        viewModelScope.launch { sessions.signOut() }
    }
}
