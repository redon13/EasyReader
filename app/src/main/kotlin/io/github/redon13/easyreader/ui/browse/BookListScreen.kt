package io.github.redon13.easyreader.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.ui.theme.EasyColors

/** Which "See all" list to show. */
enum class ListKind(val title: String) {
    READING("Continue reading"),
    RECENT("Recently added"),
    FAVOURITES("Favourites"),
    UNREAD("Unread"),
    ALL("All books"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(vm: BrowseViewModel, kind: ListKind, onBack: () -> Unit, onOpenBook: (Int) -> Unit) {
    val reading by vm.continueReading.collectAsStateWithLifecycle()
    val recent by vm.recentlyAdded.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val all by vm.allBooks.collectAsStateWithLifecycle()
    val books = when (kind) {
        ListKind.READING -> reading.map { it.first }
        ListKind.RECENT -> recent
        ListKind.FAVOURITES -> favourites
        ListKind.UNREAD -> unread
        ListKind.ALL -> all
    }
    val listView by vm.listView.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    var showSort by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EasyColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(kind.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (kind == ListKind.ALL) {
                        TextButton(onClick = { showSort = true }) { Text("Sort", color = EasyColors.Accent) }
                    }
                    IconButton(onClick = { vm.setListView(!listView) }) {
                        if (listView) Icon(GridViewIcon, "Show as grid") else Icon(Icons.Default.List, "Show as list")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EasyColors.Background,
                    scrolledContainerColor = EasyColors.Background,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = if (listView) GridCells.Fixed(1) else GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(if (listView) 4.dp else 20.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val count = if (books.size == 1) "1 book" else "${books.size} books"
                Text(
                    if (kind == ListKind.ALL) "$count · ${sort.label}" else count,
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                )
            }
            items(books, key = { it.id }) { book ->
                val progress = positions[book.id]?.progress?.toFloat()
                if (listView) BookListRow(book, vm.coverUrl(book), onClick = { onOpenBook(book.id) }, progress = progress)
                else BookTile(
                    book, vm.coverUrl(book), onClick = { onOpenBook(book.id) }, progress = progress,
                    onToggleFavourite = { vm.toggleFavourite(book) },
                )
            }
        }
    }

    if (showSort) SortSheet(vm, onDismiss = { showSort = false })
}
