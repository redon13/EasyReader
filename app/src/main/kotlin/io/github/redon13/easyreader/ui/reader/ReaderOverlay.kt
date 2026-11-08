package io.github.redon13.easyreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.data.PageTurn
import io.github.redon13.easyreader.data.ReaderTheme
import io.github.redon13.easyreader.data.ReaderTypeface
import io.github.redon13.easyreader.ui.theme.Alegreya
import io.github.redon13.easyreader.ui.theme.AlegreyaSans
import io.github.redon13.easyreader.ui.theme.EasyColors
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import kotlin.math.roundToInt

private fun ReaderTheme.bg() = Color(background)
private fun ReaderTheme.fg() = Color(text)
private fun ReaderTheme.muted() = Color(secondary)

/** Top bar (back, title, Aa) plus the Aa panel. */
@Composable
fun ReaderTopOverlay(vm: ReaderViewModel, onBack: () -> Unit, onSearchResult: (Locator) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.barsVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showAa by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val bars by vm.systemBars.collectAsStateWithLifecycle()
    val topInset = with(LocalDensity.current) { bars.first.toDp() }
    if (state != ReaderState.Ready) return

    val theme = settings.theme
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(theme.bg().copy(alpha = 0.96f))
                .padding(top = topInset)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.fg()) }
            Text(
                vm.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.fg(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (vm.canAddToCalibre) {
                val adding by vm.addingToCalibre.collectAsStateWithLifecycle()
                TextButton(onClick = vm::addToCalibre, enabled = !adding) {
                    Text(if (adding) "Adding…" else "Add to Calibre", color = EasyColors.Accent)
                }
            }
            if (vm.canSearch) {
                IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "Search in book", tint = theme.fg()) }
            }
            TextButton(onClick = { showAa = true }) {
                Text("Aa", fontFamily = Alegreya, fontSize = 22.sp, color = theme.fg())
            }
        }
    }

    if (showAa) AaSheet(vm, onDismiss = { showAa = false })
    if (showSearch) {
        SearchSheet(
            vm,
            onPick = { showSearch = false; vm.hideBars(); onSearchResult(it) },
            onDismiss = { showSearch = false },
        )
    }
}

/** Search inside the book: type words, see each place they appear, tap to go there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(vm: ReaderViewModel, onPick: (Locator) -> Unit, onDismiss: () -> Unit) {
    val hits by vm.searchHits.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search in this book", color = EasyColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = EasyColors.TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = EasyColors.SheetChip,
                    unfocusedContainerColor = EasyColors.SheetChip,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = EasyColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    searching -> "Searching… ${hits.size} found"
                    hits.isEmpty() && query.isNotBlank() -> "Press search on the keyboard to look."
                    hits.isNotEmpty() -> if (hits.size == 1) "1 result" else "${hits.size} results"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                itemsIndexed(hits) { _, hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(hit.locator) }
                            .padding(vertical = 10.dp),
                    ) {
                        hit.chapter?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = EasyColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            buildAnnotatedString {
                                append("…" + hit.before)
                                withStyle(SpanStyle(color = EasyColors.Accent, fontWeight = FontWeight.Bold)) { append(hit.match) }
                                append(hit.after + "…")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom of the screen. With the bars showing: contents button, a slider to
 * move through the book, and "page / total" (like Play Books). Otherwise a
 * small footer: "19 pages left in chapter · 40%".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBottomBar(vm: ReaderViewModel, onGo: (Link) -> Unit, onGoToPage: (Locator) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.barsVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locator by vm.locator.collectAsStateWithLifecycle()
    val bars by vm.systemBars.collectAsStateWithLifecycle()
    val bottomInset = with(LocalDensity.current) { bars.second.toDp() }
    var showToc by remember { mutableStateOf(false) }
    if (state != ReaderState.Ready) return

    val theme = settings.theme
    val percent = ((locator?.locations?.totalProgression ?: 0.0) * 100).roundToInt()
    val info = vm.pageInfo(locator)

    // While dragging the slider, the page number follows the finger; the jump happens on release.
    var dragging by remember { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxWidth()) {
        // Footer, always there underneath the page.
        Box(
            Modifier.fillMaxWidth().height(ReaderActivity.FOOTER_HEIGHT_DP.dp).background(theme.bg()),
            contentAlignment = Alignment.Center,
        ) {
            val text = when {
                info == null -> "$percent%"
                vm.isPdf -> "Page ${info.page} of ${info.total} · $percent%"
                info.leftInChapter == 0 -> "Last page in chapter · $percent%"
                info.leftInChapter == 1 -> "1 page left in chapter · $percent%"
                else -> "${info.leftInChapter} pages left in chapter · $percent%"
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = theme.muted())
        }

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(theme.bg().copy(alpha = 0.97f))
                    .padding(bottom = bottomInset)
                    .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    locator?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.muted(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showToc = true }, enabled = vm.toc.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.List, "Contents", tint = theme.fg())
                    }
                    if (info != null && info.total > 1) {
                        val shownPage = dragging?.let { it.roundToInt() + 1 } ?: info.page
                        Slider(
                            value = dragging ?: (info.page - 1).toFloat(),
                            onValueChange = { dragging = it },
                            onValueChangeFinished = {
                                dragging?.let { v -> vm.pages.getOrNull(v.roundToInt())?.let(onGoToPage) }
                                dragging = null
                            },
                            valueRange = 0f..(info.total - 1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = EasyColors.Accent,
                                activeTrackColor = EasyColors.Accent,
                                inactiveTrackColor = theme.muted().copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(
                            "$shownPage / ${info.total}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.fg(),
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(min = 72.dp),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                        Text("$percent%", style = MaterialTheme.typography.bodyMedium, color = theme.fg())
                    }
                }
            }
        }
    }

    if (showToc) TocSheet(vm, onGo = { showToc = false; vm.hideBars(); onGo(it) }, onDismiss = { showToc = false })
}

/** Full-screen "Downloading…" / "Opening…" / problem screen, shown until the book is ready. */
@Composable
fun ReaderStatusScreen(vm: ReaderViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(EasyColors.Background).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            when (val s = state) {
                is ReaderState.Loading -> {
                    if (vm.title.isNotBlank()) {
                        Text(vm.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    val p = s.progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            color = EasyColors.Accent,
                            trackColor = EasyColors.SurfaceHigh,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Text("${s.message} ${(p * 100).roundToInt()}%", color = EasyColors.TextMuted)
                    } else {
                        CircularProgressIndicator(color = EasyColors.Accent)
                        Text(s.message, color = EasyColors.TextMuted)
                    }
                }
                is ReaderState.Failed -> {
                    Text("Couldn't open the book", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, color = EasyColors.TextMuted, textAlign = TextAlign.Center)
                    Button(
                        onClick = onClose,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EasyColors.Accent, contentColor = EasyColors.OnAccent),
                    ) { Text("Back") }
                }
                else -> Unit
            }
        }
    }
}

/** Reading settings: text size, typeface and background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AaSheet(vm: ReaderViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (vm.isPdf) {
                Text(
                    "PDF pages are shown as they were made, so text size, typeface and background don't change them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EasyColors.TextMuted,
                )
            }

            Text("Text size", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { vm.updateSettings { it.copy(fontSize = (it.fontSize - 0.1).coerceAtLeast(0.7)) } },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("A", fontSize = 14.sp, color = EasyColors.Text) }
                Text(
                    "${(settings.fontSize * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { vm.updateSettings { it.copy(fontSize = (it.fontSize + 0.1).coerceAtMost(2.5)) } },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("A", fontSize = 22.sp, color = EasyColors.Text) }
            }

            Text("Typeface", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTypeface.entries.forEach { face ->
                    FilterChip(
                        selected = settings.typeface == face,
                        onClick = { vm.updateSettings { it.copy(typeface = face) } },
                        label = {
                            Text(
                                face.label,
                                fontFamily = when (face) {
                                    ReaderTypeface.ALEGREYA -> Alegreya
                                    ReaderTypeface.ALEGREYA_SANS -> AlegreyaSans
                                    ReaderTypeface.ORIGINAL -> FontFamily.Default
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = EasyColors.SheetChip,
                            labelColor = EasyColors.Text,
                            selectedContainerColor = EasyColors.Accent,
                            selectedLabelColor = EasyColors.OnAccent,
                        ),
                        border = null,
                    )
                }
            }

            Text("Page turn", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageTurn.entries.forEach { turn ->
                    FilterChip(
                        selected = settings.pageTurn == turn,
                        onClick = { vm.updateSettings { it.copy(pageTurn = turn) } },
                        label = { Text(turn.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = EasyColors.SheetChip,
                            labelColor = EasyColors.Text,
                            selectedContainerColor = EasyColors.Accent,
                            selectedLabelColor = EasyColors.OnAccent,
                        ),
                        border = null,
                    )
                }
            }

            Text("Background", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    val chosen = settings.theme == theme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.updateSettings { it.copy(theme = theme) } },
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(theme.bg())
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) EasyColors.Accent else EasyColors.Outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aa", fontFamily = Alegreya, fontSize = 22.sp, color = theme.fg())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            theme.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chosen) EasyColors.Accent else EasyColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Table of contents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(vm: ReaderViewModel, onGo: (Link) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EasyColors.Sheet,
        contentColor = EasyColors.Text,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Text(
            "Contents",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.heightIn(max = 560.dp).padding(bottom = 16.dp)) {
            itemsIndexed(vm.toc) { _, entry ->
                Text(
                    entry.title,
                    style = if (entry.depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = if (entry.depth == 0) EasyColors.Text else EasyColors.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGo(entry.link) }
                        .padding(start = (20 + entry.depth * 18).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
