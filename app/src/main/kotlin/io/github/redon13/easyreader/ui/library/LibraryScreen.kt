package io.github.redon13.easyreader.ui.library

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.redon13.easyreader.AppContainer
import io.github.redon13.easyreader.calibre.ColumnCheck
import io.github.redon13.easyreader.calibre.RequiredColumn
import io.github.redon13.easyreader.data.Session
import io.github.redon13.easyreader.ui.theme.EasyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(container: AppContainer, session: Session) {
    // A fresh screen state for each sign-in and library, so an old login is never reused.
    val vm = viewModel(key = "library-${session.libraryId}-${System.identityHashCode(session.client)}") {
        LibraryViewModel(container.library, container.sessions, session)
    }
    val books by vm.books.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val libraries by vm.libraries.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val lastSynced by vm.lastSynced.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var menuOpen by remember { mutableStateOf(false) }
    var showColumnHelp by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EasyColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Library", style = MaterialTheme.typography.titleLarge)
                        if (libraries.size > 1) {
                            Text(session.libraryName, style = MaterialTheme.typography.labelSmall, color = EasyColors.TextMuted)
                        }
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sync now") },
                                onClick = { menuOpen = false; vm.sync() },
                            )
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
                                            if (id == session.libraryId) Icon(Icons.Default.Check, null, tint = EasyColors.Accent)
                                            else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = { menuOpen = false; vm.switchLibrary(id, name) },
                                    )
                                }
                            }
                            HorizontalDivider(color = EasyColors.Divider)
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = { menuOpen = false; vm.signOut() },
                            )
                        }
                    }
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
            val list = books
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(syncing, columns, onColumnHelp = { showColumnHelp = true })
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            columns?.takeUnless { it.allPresent }?.let {
                                ColumnBanner(it, onHelp = { showColumnHelp = true })
                            }
                            SummaryLine(list.size, lastSynced)
                        }
                    }
                    items(list, key = { it.id }) { book ->
                        BookTile(book, vm.coverUrl(book))
                    }
                }
            }
        }
    }

    val check = columns
    if (showColumnHelp && check != null) {
        ColumnHelpDialog(check, onDismiss = { showColumnHelp = false })
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
        listOfNotNull(if (count == 1) "1 book" else "$count books", synced).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = EasyColors.TextMuted,
    )
}

@Composable
private fun EmptyState(syncing: Boolean, columns: ColumnCheck?, onColumnHelp: () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty screen.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = EasyColors.Accent) }
        },
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
