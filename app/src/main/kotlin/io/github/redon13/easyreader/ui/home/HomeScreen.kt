package io.github.redon13.easyreader.ui.home

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.calibre.ReadingPosition
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.calibre.shelves
import io.github.redon13.easyreader.calibre.RequiredColumn
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.data.browse.SmartShelfFilter
import io.github.redon13.easyreader.ui.browse.BookCoverImage
import io.github.redon13.easyreader.ui.browse.BookListRow
import io.github.redon13.easyreader.ui.browse.BookTile
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.browse.FiltersSheet
import io.github.redon13.easyreader.ui.browse.GridViewIcon
import io.github.redon13.easyreader.ui.browse.ListKind
import io.github.redon13.easyreader.ui.browse.ReadingProgressBar
import io.github.redon13.easyreader.ui.browse.ShelfCard
import io.github.redon13.easyreader.ui.browse.SortSheet
import io.github.redon13.easyreader.ui.reader.ReaderActivity
import io.github.redon13.easyreader.ui.theme.EasyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: BrowseViewModel,
    onOpenBook: (Int) -> Unit,
    onSeeAll: (ListKind) -> Unit,
    onOpenSettings: () -> Unit,
    onUpload: () -> Unit,
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recentlyAdded.collectAsStateWithLifecycle()
    val all by vm.allBooks.collectAsStateWithLifecycle()
    val listView by vm.listView.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val reading by vm.continueReading.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openReader: (Int) -> Unit = { id -> ReaderActivity.open(context, vm.session.libraryId, id) }
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val lastSynced by vm.lastSynced.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var showFilters by remember { mutableStateOf(false) }
    var filtersTab by remember { mutableStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    var showColumnHelp by remember { mutableStateOf(false) }

    // Back clears the search and filters before leaving the app.
    BackHandler(enabled = query.isNarrowed) { vm.clearSearchAndFilters() }

    Scaffold(
        containerColor = EasyColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Library", style = MaterialTheme.typography.titleLarge)
                        if (libraries.size > 1) {
                            Text(vm.session.libraryName, style = MaterialTheme.typography.labelSmall, color = EasyColors.TextMuted)
                        }
                    }
                },
                navigationIcon = { HomeMenu(vm, libraries, onOpenSettings) },
                actions = {
                    IconButton(onClick = onUpload) { Icon(Icons.Default.Add, contentDescription = "Upload books") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = EasyColors.Background,
                    scrolledContainerColor = EasyColors.Background,
                ),
            )
        },
    ) { padding ->
        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = vm::sync,
            state = refreshState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = syncing,
                    containerColor = EasyColors.Surface,
                    color = EasyColors.Accent,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val library = books
            when {
                library == null -> Unit
                library.isEmpty() -> EmptyLibrary(syncing, columns, onColumnHelp = { showColumnHelp = true })
                else -> LazyVerticalGrid(
                    columns = if (query.isNarrowed && listView) GridCells.Fixed(1) else GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (query.isNarrowed && listView) 4.dp else 20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    reading.firstOrNull()?.takeIf { !query.isNarrowed }?.let { (book, position) ->
                        fullWidth("hero") { ContinueCard(book, position, vm, onClick = { openReader(book.id) }) }
                    }
                    fullWidth("controls") {
                        Controls(
                            vm = vm,
                            filterCount = query.filters.size + (if (query.smartShelf != null) 1 else 0),
                            listView = listView,
                            onFilters = { filtersTab = 0; showFilters = true },
                            onSort = { showSort = true },
                        )
                    }
                    columns?.takeUnless { it.allPresent }?.let { check ->
                        fullWidth("columns") { ColumnBanner(check, onHelp = { showColumnHelp = true }) }
                    }
                    if (query.isNarrowed) {
                        resultsSection(vm, query.filters, query.smartShelf, results, listView, positions, onOpenBook)
                    } else {
                        if (reading.size > 1) {
                            fullWidth("reading") {
                                CoverRow("Continue reading", null, reading.map { it.first }, vm, positions, openReader) {
                                    onSeeAll(ListKind.READING)
                                }
                            }
                        }
                        fullWidth("recent") {
                            CoverRow("Recently added", null, recent, vm, positions, onOpenBook) { onSeeAll(ListKind.RECENT) }
                        }
                        if (shelves.isNotEmpty()) {
                            fullWidth("shelves") {
                                ShelvesRow(
                                    vm,
                                    recent,
                                    onOpen = { name ->
                                        vm.clearSmartShelf()
                                        vm.setFilter(FilterCategory.SHELVES, name)
                                    },
                                    onSeeAll = { filtersTab = 1; showFilters = true },
                                )
                            }
                        }
                        if (favourites.isNotEmpty()) {
                            fullWidth("favourites") {
                                CoverRow("Favourites", null, favourites, vm, positions, onOpenBook) { onSeeAll(ListKind.FAVOURITES) }
                            }
                        }
                        if (unread.isNotEmpty()) {
                            fullWidth("unread") {
                                CoverRow("Unread", null, unread, vm, positions, onOpenBook) { onSeeAll(ListKind.UNREAD) }
                            }
                        }
                        fullWidth("all") {
                            CoverRow("All books", sort.label, all, vm, positions, onOpenBook) { onSeeAll(ListKind.ALL) }
                        }
                        fullWidth("summary") { SummaryLine(library.size, lastSynced) }
                    }
                }
            }
        }
    }

    if (showFilters) FiltersSheet(vm, onDismiss = { showFilters = false }, initialTab = filtersTab)
    if (showSort) SortSheet(vm, onDismiss = { showSort = false })
    val check = columns
    if (showColumnHelp && check != null) ColumnHelpDialog(check, onDismiss = { showColumnHelp = false })
}

internal fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyGridScope.resultsSection(
    vm: BrowseViewModel,
    filters: Map<FilterCategory, String>,
    smart: SmartShelfFilter?,
    results: List<CalibreBook>,
    listView: Boolean,
    positions: Map<Int, ReadingPosition>,
    onOpenBook: (Int) -> Unit,
) {
    fullWidth("active") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filters.isNotEmpty() || smart != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (smart != null) {
                        InputChip(
                            selected = true,
                            onClick = vm::clearSmartShelf,
                            label = { Text("Smart shelf: ${smart.name}") },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp)) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = EasyColors.SurfaceHigh,
                                selectedLabelColor = EasyColors.Text,
                                selectedTrailingIconColor = EasyColors.TextMuted,
                            ),
                            border = null,
                        )
                    }
                    filters.forEach { (category, value) ->
                        val shown = if (category == FilterCategory.LANGUAGES) Browse.languageName(value) else value
                        InputChip(
                            selected = true,
                            onClick = { vm.setFilter(category, null) },
                            label = { Text("${category.label}: $shown") },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp)) },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = EasyColors.SurfaceHigh,
                                selectedLabelColor = EasyColors.Text,
                                selectedTrailingIconColor = EasyColors.TextMuted,
                            ),
                            border = null,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (results.size == 1) "1 book" else "${results.size} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearSearchAndFilters) {
                    Text("Clear all", color = EasyColors.Accent)
                }
            }
        }
    }
    if (results.isEmpty()) {
        fullWidth("none") {
            Text(
                "No books match. Try fewer words or remove a filter.",
                style = MaterialTheme.typography.bodyLarge,
                color = EasyColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
        }
    }
    items(results, key = { it.id }) { book ->
        val progress = positions[book.id]?.progress?.toFloat()
        if (listView) BookListRow(book, vm.coverUrl(book), onClick = { onOpenBook(book.id) }, progress = progress)
        else BookTile(
            book, vm.coverUrl(book), onClick = { onOpenBook(book.id) }, progress = progress,
            onToggleFavourite = { vm.toggleFavourite(book) },
        )
    }
}

@Composable
internal fun Controls(
    vm: BrowseViewModel,
    filterCount: Int,
    listView: Boolean,
    onFilters: () -> Unit,
    onSort: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = vm.search,
            onValueChange = { vm.search = it },
            placeholder = { Text("Search title, author, series, tag…", color = EasyColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = EasyColors.TextMuted) },
            trailingIcon = {
                if (vm.search.isNotEmpty()) {
                    IconButton(onClick = { vm.search = "" }) { Icon(Icons.Default.Clear, "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = EasyColors.SurfaceHigh,
                unfocusedContainerColor = EasyColors.SurfaceHigh,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = EasyColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onFilters,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (filterCount > 0) EasyColors.Accent else EasyColors.Outline),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text(
                    "Filters & shelves",
                    color = EasyColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // How many filters are on, as a small amber badge (always fits on one line).
                if (filterCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(22.dp).background(EasyColors.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(filterCount.toString(), color = EasyColors.OnAccent, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedButton(
                onClick = onSort,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EasyColors.Outline),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Sort", color = EasyColors.Text, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = EasyColors.Text)
            }
            IconButton(
                onClick = { vm.setListView(!listView) },
                modifier = Modifier.size(48.dp).background(EasyColors.SurfaceHigh, RoundedCornerShape(14.dp)),
            ) {
                if (listView) Icon(GridViewIcon, "Show as grid", tint = EasyColors.Text)
                else Icon(Icons.Default.List, "Show as list", tint = EasyColors.Text)
            }
        }
    }
}

@Composable
private fun CoverRow(
    title: String,
    subtitle: String?,
    books: List<CalibreBook>,
    vm: BrowseViewModel,
    positions: Map<Int, ReadingPosition>,
    onOpenBook: (Int) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    val listView by vm.listView.collectAsStateWithLifecycle()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted)
                }
            }
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) {
                    Text("See all", color = EasyColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (listView) {
            // List view: the first few books as a list; "See all" shows the rest.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                books.take(LIST_LENGTH).forEach { book ->
                    BookListRow(
                        book,
                        vm.coverUrl(book),
                        onClick = { onOpenBook(book.id) },
                        progress = positions[book.id]?.progress?.toFloat(),
                    )
                }
            }
        } else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books.take(ROW_LENGTH), key = { it.id }) { book ->
                BookTile(
                    book,
                    vm.coverUrl(book),
                    onClick = { onOpenBook(book.id) },
                    modifier = Modifier.width(112.dp),
                    progress = positions[book.id]?.progress?.toFloat()?.takeIf { !book.isFinished },
                    onToggleFavourite = { vm.toggleFavourite(book) },
                )
            }
        }
    }
}

private const val ROW_LENGTH = 20
private const val LIST_LENGTH = 5

/** The "Shelves" row: a card for each of the owner's shelves. */
@Composable
private fun ShelvesRow(vm: BrowseViewModel, recent: List<CalibreBook>, onOpen: (String) -> Unit, onSeeAll: () -> Unit) {
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shelves", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) {
                Text("See all", color = EasyColors.Accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shelves, key = { it.value }) { shelf ->
                ShelfCard(
                    name = shelf.value,
                    count = shelf.count,
                    covers = shelfCovers(vm, recent, shelf.value),
                    onClick = { onOpen(shelf.value) },
                )
            }
        }
    }
}

/** The first few books on a shelf (most recently added first), with their cover addresses. */
internal fun shelfCovers(vm: BrowseViewModel, recent: List<CalibreBook>, shelf: String): List<Pair<CalibreBook, String>> =
    recent.asSequence().filter { b -> b.shelves.any { it == shelf } }.take(3).map { it to vm.coverUrl(it) }.toList()

/** The "Continue reading" card: the book read most recently, one tap to carry on. */
@Composable
private fun ContinueCard(book: CalibreBook, position: ReadingPosition, vm: BrowseViewModel, onClick: () -> Unit) {
    val chapter = remember(position.locator) {
        position.locator?.let { runCatching { org.json.JSONObject(it).optString("title") }.getOrNull() }?.takeIf { it.isNotBlank() }
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EasyColors.HeroCard, contentColor = EasyColors.Text),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCoverImage(book, vm.coverUrl(book), Modifier.width(72.dp), small = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Continue reading", style = MaterialTheme.typography.labelMedium, color = EasyColors.Accent)
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotEmpty()) {
                    Text(book.authors.joinToString(" & "), style = MaterialTheme.typography.bodyMedium, color = EasyColors.TextMuted, maxLines = 1)
                }
                Text(
                    listOfNotNull(chapter, "${(position.progress * 100).toInt()}%").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ReadingProgressBar(position.progress.toFloat(), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun HomeMenu(vm: BrowseViewModel, libraries: Map<String, String>, onOpenSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Sync now") }, onClick = { open = false; vm.sync() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onOpenSettings() })
            if (libraries.size > 1) {
                HorizontalDivider(color = EasyColors.Divider)
                Text(
                    "Libraries",
                    style = MaterialTheme.typography.labelSmall,
                    color = EasyColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                libraries.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        leadingIcon = {
                            if (id == vm.session.libraryId) Icon(Icons.Default.Check, null, tint = EasyColors.Accent)
                            else Spacer(Modifier.size(24.dp))
                        },
                        onClick = { open = false; vm.switchLibrary(id, name) },
                    )
                }
            }
            HorizontalDivider(color = EasyColors.Divider)
            DropdownMenuItem(text = { Text("Sign out") }, onClick = { open = false; vm.signOut() })
        }
    }
}

@Composable
private fun SummaryLine(count: Int, lastSynced: Long?) {
    val context = LocalContext.current
    val synced = lastSynced?.let {
        val time = DateFormat.getTimeFormat(context).format(it)
        if (DateUtils.isToday(it)) "Last synced $time"
        else "Last synced ${DateFormat.getMediumDateFormat(context).format(it)}, $time"
    }
    Text(
        listOfNotNull(if (count == 1) "1 book in your library" else "$count books in your library", synced).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = EasyColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyLibrary(syncing: Boolean, columns: ColumnCheck?, onColumnHelp: () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty screen.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        columns?.takeUnless { it.allPresent }?.let {
            ColumnBanner(it, onHelp = onColumnHelp)
            Spacer(Modifier.height(24.dp))
        }
        Spacer(Modifier.height(80.dp))
        if (syncing) {
            CircularProgressIndicator(color = EasyColors.Accent)
            Spacer(Modifier.height(16.dp))
            Text("Loading your library…", style = MaterialTheme.typography.bodyLarge, color = EasyColors.TextMuted)
        } else {
            Text("No books yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Books you add to this Calibre library will show up here. Pull down to check again.",
                style = MaterialTheme.typography.bodyMedium,
                color = EasyColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Tells the owner which of EasyReader's custom columns Calibre is missing. */
@Composable
internal fun ColumnBanner(check: ColumnCheck, onHelp: () -> Unit) {
    val names = (check.missing + check.wrongType).joinToString(", ") { it.lookupName }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EasyColors.HeroCard, contentColor = EasyColors.Text),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = EasyColors.Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Calibre needs a few columns", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "EasyReader saves shelves, favourites and your reading place in Calibre. Please add: $names",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onHelp) {
                    Text("Show me how", color = EasyColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ColumnHelpDialog(check: ColumnCheck, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EasyColors.Surface,
        title = { Text("Add these columns in Calibre") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "On the PC, open Calibre and go to Preferences → Add your own columns → Add custom column. " +
                        "Add each column below, then restart Calibre and pull down on the library here to check again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                check.missing.forEach { ColumnHelpRow(it, fix = false) }
                check.wrongType.forEach { ColumnHelpRow(it, fix = true) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = EasyColors.Accent) } },
    )
}

@Composable
private fun ColumnHelpRow(column: RequiredColumn, fix: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(column.heading, style = MaterialTheme.typography.titleSmall, color = EasyColors.Accent)
        Text("Lookup name: ${column.lookupName.removePrefix("#")}", style = MaterialTheme.typography.bodySmall)
        Text("Column heading: ${column.heading}", style = MaterialTheme.typography.bodySmall)
        Text("Column type: ${column.calibreTypeName}", style = MaterialTheme.typography.bodySmall)
        if (fix) {
            Text(
                "This column exists but has the wrong type. Delete it and add it again with the type above.",
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.Error,
            )
        }
    }
}
