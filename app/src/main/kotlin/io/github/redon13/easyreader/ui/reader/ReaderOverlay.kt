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
import io.github.redon13.easyreader.data.ReaderTheme
import io.github.redon13.easyreader.data.ReaderTypeface
import io.github.redon13.easyreader.ui.theme.Alegreya
import io.github.redon13.easyreader.ui.theme.AlegreyaSans
import io.github.redon13.easyreader.ui.theme.EasyColors
import org.readium.r2.shared.publication.Link
import kotlin.math.roundToInt

private fun ReaderTheme.bg() = Color(background)
private fun ReaderTheme.fg() = Color(text)
private fun ReaderTheme.muted() = Color(secondary)

/** Top bar (back, title, contents, Aa) plus the Aa and contents panels. */
@Composable
fun ReaderTopOverlay(vm: ReaderViewModel, onBack: () -> Unit, onGo: (Link) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.barsVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showAa by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
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
            if (vm.toc.isNotEmpty()) {
                IconButton(onClick = { showToc = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, "Contents", tint = theme.fg())
                }
            }
            TextButton(onClick = { showAa = true }) {
                Text("Aa", fontFamily = Alegreya, fontSize = 22.sp, color = theme.fg())
            }
        }
    }

    if (showAa) AaSheet(vm, onDismiss = { showAa = false })
    if (showToc) TocSheet(vm, onGo = { showToc = false; vm.hideBars(); onGo(it) }, onDismiss = { showToc = false })
}

/** Bottom bar: chapter, thin progress bar and %. */
@Composable
fun ReaderBottomBar(vm: ReaderViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.barsVisible.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locator by vm.locator.collectAsStateWithLifecycle()
    val bars by vm.systemBars.collectAsStateWithLifecycle()
    val bottomInset = with(LocalDensity.current) { bars.second.toDp() }
    if (state != ReaderState.Ready) return

    val theme = settings.theme
    val progress = (locator?.locations?.totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(theme.bg().copy(alpha = 0.96f))
                .padding(bottom = bottomInset)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                color = EasyColors.Accent,
                trackColor = theme.muted().copy(alpha = 0.25f),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    locator?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.muted(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = theme.muted())
            }
        }
    }
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
