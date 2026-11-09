package io.github.redon13.easyreader.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.ui.browse.BookListRow
import io.github.redon13.easyreader.ui.browse.BookTile
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.browse.FiltersSheet
import io.github.redon13.easyreader.ui.browse.ListKind
import io.github.redon13.easyreader.ui.browse.ShelfCard
import io.github.redon13.easyreader.ui.browse.SortSheet
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.ui.theme.EasyColors

/**
 * Tablet, landscape: books on the left (search, filters, a row of list
 * choices and a cover grid), and the selected book's details on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletLibrary(
    vm: BrowseViewModel,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onUpload: () -> Unit,
    details: @Composable (Int) -> Unit,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val listView by vm.listView.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val reading by vm.continueReading.collectAsStateWithLifecycle()
    val recent by vm.recentlyAdded.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val all by vm.allBooks.collectAsStateWithLifecycle()

    var kind by rememberSaveable { mutableStateOf(ListKind.ALL) }
    // The "Shelves" choice shows shelf cards instead of books.
    var shelvesChosen by rememberSaveable { mutableStateOf(false) }
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showColumnHelp by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = query.isNarrowed) { vm.clearSearchAndFilters() }

    val shown = when {
        query.isNarrowed -> results
        kind == ListKind.READING -> reading.map { it.first }
        kind == ListKind.RECENT -> recent
        kind == ListKind.FAVOURITES -> favourites
        kind == ListKind.UNREAD -> unread
        else -> all
    }

    Row(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = EasyColors.Background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("My Library", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = { HomeMenu(vm, libraries, onOpenSettings) },
                    actions = { IconButton(onClick = onUpload) { Icon(Icons.Default.Add, contentDescription = "Upload books") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = EasyColors.Background),
                )
            },
            modifier = Modifier.weight(1f),
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = vm::sync,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = if (listView) GridCells.Fixed(1) else GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (listView) 4.dp else 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    fullWidth("controls") {
                        Controls(
                            vm = vm,
                            filterCount = query.filters.size + (if (query.smartShelf != null) 1 else 0),
                            listView = listView,
                            onFilters = { showFilters = true },
                            onSort = { showSort = true },
                        )
                    }
                    columns?.takeUnless { it.allPresent }?.let { check ->
                        fullWidth("columns") { ColumnBanner(check, onHelp = { showColumnHelp = true }) }
                    }
                    if (query.isNarrowed) {
                        resultsSection(vm, query.filters, query.smartShelf, results, listView, positions, onSelect)
                    } else {
                        fullWidth("kinds") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // null stands for the "Shelves" choice.
                                items(listOf(ListKind.ALL, ListKind.READING, ListKind.RECENT, null, ListKind.FAVOURITES, ListKind.UNREAD)) { k ->
                                    FilterChip(
                                        selected = if (k == null) shelvesChosen else !shelvesChosen && kind == k,
                                        onClick = {
                                            if (k == null) {
                                                shelvesChosen = true
                                            } else {
                                                shelvesChosen = false
                                                kind = k
                                            }
                                        },
                                        label = { Text(k?.title ?: "Shelves") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = EasyColors.SurfaceHigh,
                                            labelColor = EasyColors.Text,
                                            selectedContainerColor = EasyColors.Accent,
                                            selectedLabelColor = EasyColors.OnAccent,
                                        ),
                                        border = null,
                                    )
                                }
                            }
                        }
                        if (shelvesChosen) {
                            if (shelves.isEmpty()) {
                                fullWidth("noshelves") {
                                    Text(
                                        "No shelves yet. Use Add to shelf on a book's page, or Filters & shelves → My shelves.",
                                        color = EasyColors.TextMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 48.dp),
                                    )
                                }
                            }
                            items(shelves, key = { "shelf:" + it.value }) { shelf ->
                                ShelfCard(
                                    name = shelf.value,
                                    count = shelf.count,
                                    covers = shelfCovers(vm, recent, shelf.value),
                                    onClick = {
                                        vm.clearSmartShelf()
                                        vm.setFilter(FilterCategory.SHELVES, shelf.value)
                                    },
                                )
                            }
                        } else if (shown.isEmpty()) {
                            fullWidth("empty") {
                                Text(
                                    "Nothing here yet.",
                                    color = EasyColors.TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 48.dp),
                                )
                            }
                        }
                        if (!shelvesChosen) items(shown, key = { it.id }) { book ->
                            val progress = positions[book.id]?.progress?.toFloat()
                            if (listView) BookListRow(book, vm.coverUrl(book), onClick = { onSelect(book.id) }, progress = progress)
                            else BookTile(
                                book, vm.coverUrl(book), onClick = { onSelect(book.id) }, progress = progress,
                                onToggleFavourite = { vm.toggleFavourite(book) },
                            )
                        }
                    }
                }
            }
        }

        VerticalDivider(color = EasyColors.Divider)

        // Details pane (420 dp, as in the design).
        Box(Modifier.width(420.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (selectedId != null) {
                details(selectedId)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Choose a book", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Its details will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EasyColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showFilters) FiltersSheet(vm, onDismiss = { showFilters = false })
    if (showSort) SortSheet(vm, onDismiss = { showSort = false })
    val check = columns
    if (showColumnHelp && check != null) ColumnHelpDialog(check, onDismiss = { showColumnHelp = false })
}
