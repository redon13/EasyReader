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
import io.github.redon13.easyreader.data.Session
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.BrowseQuery
import io.github.redon13.easyreader.data.browse.CategoryValue
import io.github.redon13.easyreader.data.browse.FilterCategory
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

    /** All books in the library, or null until the stored copy has been read. */
    val books: StateFlow<List<CalibreBook>?> = repo.books(session.libraryId)
        .map { list -> list.mapNotNull { runCatching { CalibreBookParser.parse(it.id, it.rawJson) }.getOrNull() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Search text, kept as Compose state so typing stays smooth. */
    var search by mutableStateOf("")

    private val filters = MutableStateFlow<Map<FilterCategory, String>>(emptyMap())

    val sort: StateFlow<SortOrder> = settings.sortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder())

    val listView: StateFlow<Boolean> = settings.listView
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val query: StateFlow<BrowseQuery> = combine(snapshotFlow { search }, filters, sort) { s, f, o ->
        BrowseQuery(s, f, o)
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
        filters.value = mapOf(category to value)
    }

    fun clearSearchAndFilters() {
        search = ""
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
