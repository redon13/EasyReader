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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.calibre.RequiredColumn
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.ui.browse.BookListRow
import io.github.redon13.easyreader.ui.browse.BookTile
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.browse.FiltersSheet
import io.github.redon13.easyreader.ui.browse.GridViewIcon
import io.github.redon13.easyreader.ui.browse.ListKind
import io.github.redon13.easyreader.ui.browse.SortSheet
import io.github.redon13.easyreader.ui.theme.EasyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: BrowseViewModel,
    onOpenBook: (Int) -> Unit,
    onSeeAll: (ListKind) -> Unit,
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recentlyAdded.collectAsStateWithLifecycle()
    val all by vm.allBooks.collectAsStateWithLifecycle()
    val listView by vm.listView.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val lastSynced by vm.lastSynced.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var showFilters by remember { mutableStateOf(false) }
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
                navigationIcon = { HomeMenu(vm, libraries) },
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
                    fullWidth("controls") {
                        Controls(
                            vm = vm,
                            filterCount = query.filters.size,
                            listView = listView,
                            onFilters = { showFilters = true },
                            onSort = { showSort = true },
                        )
                    }
                    columns?.takeUnless { it.allPresent }?.let { check ->
                        fullWidth("columns") { ColumnBanner(check, onHelp = { showColumnHelp = true }) }
                    }
                    if (query.isNarrowed) {
                        resultsSection(vm, query.filters, results, listView, onOpenBook)
                    } else {
                        fullWidth("recent") {
                            CoverRow("Recently added", null, recent, vm, onOpenBook) { onSeeAll(ListKind.RECENT) }
                        }
                        fullWidth("all") {
                            CoverRow("All books", sort.label, all, vm, onOpenBook) { onSeeAll(ListKind.ALL) }
                        }
                        fullWidth("summary") { SummaryLine(library.size, lastSynced) }
                    }
                }
            }
        }
    }

    if (showFilters) FiltersSheet(vm, onDismiss = { showFilters = false })
    if (showSort) SortSheet(vm, onDismiss = { showSort = false })
    val check = columns
    if (showColumnHelp && check != null) ColumnHelpDialog(check, onDismiss = { showColumnHelp = false })
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyGridScope.resultsSection(
    vm: BrowseViewModel,
    filters: Map<FilterCategory, String>,
    results: List<CalibreBook>,
    listView: Boolean,
    onOpenBook: (Int) -> Unit,
) {
    fullWidth("active") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filters.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (listView) BookListRow(book, vm.coverUrl(book), onClick = { onOpenBook(book.id) })
        else BookTile(book, vm.coverUrl(book), onClick = { onOpenBook(book.id) })
    }
}

@Composable
private fun Controls(
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
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text(
                    if (filterCount > 0) "Filters & shelves ($filterCount)" else "Filters & shelves",
                    color = EasyColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                )
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
    onOpenBook: (Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EasyColors.TextMuted)
                }
            }
            TextButton(onClick = onSeeAll) {
                Text("See all", color = EasyColors.Accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books.take(ROW_LENGTH), key = { it.id }) { book ->
                BookTile(book, vm.coverUrl(book), onClick = { onOpenBook(book.id) }, modifier = Modifier.width(112.dp))
            }
        }
    }
}

private const val ROW_LENGTH = 20

@Composable
private fun HomeMenu(vm: BrowseViewModel, libraries: Map<String, String>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Sync now") }, onClick = { open = false; vm.sync() })
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
private fun ColumnBanner(check: ColumnCheck, onHelp: () -> Unit) {
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
private fun ColumnHelpDialog(check: ColumnCheck, onDismiss: () -> Unit) {
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
