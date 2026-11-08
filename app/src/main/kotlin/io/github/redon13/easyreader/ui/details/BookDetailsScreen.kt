package io.github.redon13.easyreader.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.CustomColumnValue
import io.github.redon13.easyreader.calibre.RequiredColumn
import io.github.redon13.easyreader.calibre.isFavourite
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.data.Downloads
import io.github.redon13.easyreader.data.browse.Browse
import io.github.redon13.easyreader.data.browse.FilterCategory
import io.github.redon13.easyreader.ui.browse.AddToShelfSheet
import io.github.redon13.easyreader.ui.browse.BookCoverImage
import io.github.redon13.easyreader.ui.browse.BrowseViewModel
import io.github.redon13.easyreader.ui.browse.ReadingProgressBar
import io.github.redon13.easyreader.ui.reader.ReaderActivity
import io.github.redon13.easyreader.ui.browse.seriesText
import io.github.redon13.easyreader.ui.theme.EasyColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Everything Calibre knows about one book. View only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    vm: BrowseViewModel,
    bookId: Int,
    onBack: () -> Unit,
    onShowOnly: (FilterCategory, String) -> Unit,
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val book = books?.firstOrNull { it.id == bookId }

    Scaffold(
        containerColor = EasyColors.Background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { vm.toggleFavourite(book) }) {
                            Icon(
                                if (book.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (book.isFavourite) "Remove from favourites" else "Add to favourites",
                                tint = if (book.isFavourite) EasyColors.Accent else EasyColors.Text,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EasyColors.Background),
            )
        },
    ) { padding ->
        if (book == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (books != null) {
                    Text("This book is no longer in the library.", color = EasyColors.TextMuted)
                }
            }
        } else {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Details(vm, book, onShowOnly, Modifier.widthIn(max = 640.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    vm: BrowseViewModel,
    book: CalibreBook,
    onShowOnly: (FilterCategory, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Cover beside title, author, rating and formats.
        Row {
            BookCoverImage(book, vm.coverUrl(book), Modifier.width(120.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                book.authors.forEach { author ->
                    Text(
                        author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = EasyColors.Accent,
                        modifier = Modifier.clickable { onShowOnly(FilterCategory.AUTHORS, author) },
                    )
                }
                book.rating?.let { Text(stars(it), style = MaterialTheme.typography.bodyLarge, color = EasyColors.Accent) }
                val read = vm.positions.collectAsStateWithLifecycle().value[book.id]?.progress
                val line = listOfNotNull(
                    read?.takeIf { it > 0 }?.let { "${(it * 100).toInt()}% read" },
                    book.formats.joinToString(" · ").ifEmpty { null },
                )
                if (line.isNotEmpty()) {
                    Text(line.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = EasyColors.TextMuted)
                }
            }
        }

        ReadingButtons(vm, book)

        InfoPanel(book, onShowOnly)

        if (book.tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tags", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.tags.forEach { tag ->
                        AssistChip(
                            onClick = { onShowOnly(FilterCategory.TAGS, tag) },
                            label = { Text(tag) },
                            shape = RoundedCornerShape(14.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = EasyColors.SurfaceHigh, labelColor = EasyColors.Text),
                            border = null,
                        )
                    }
                }
            }
        }

        book.comments?.let { html ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Description", style = MaterialTheme.typography.titleMedium)
                Text(AnnotatedString.fromHtml(html), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Read / Continue reading, Remove download, and (greyed out until Stage 4) shelf and finished. */
@Composable
private fun ReadingButtons(vm: BrowseViewModel, book: CalibreBook) {
    val context = LocalContext.current
    val positions by vm.positions.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val progress = positions[book.id]?.progress
    val format = Downloads.bestFormat(book)
    val label = when {
        format == null -> "No file to read"
        format !in Downloads.READABLE -> "Open in another app"
        book.isFinished -> "Read again"
        progress != null && progress > 0.0 -> "Continue reading · ${(progress * 100).toInt()}%"
        else -> "Read"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { ReaderActivity.open(context, vm.session.libraryId, book.id) },
            enabled = format != null,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EasyColors.Accent,
                contentColor = EasyColors.OnAccent,
                disabledContainerColor = EasyColors.SurfaceHigh,
                disabledContentColor = EasyColors.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
        if (progress != null && progress > 0.0) {
            ReadingProgressBar(progress.toFloat(), Modifier.fillMaxWidth())
        }
        var showShelves by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Add to shelf", Modifier.weight(1f)) { showShelves = true }
            SmallButton(if (book.isFinished) "Mark as unread" else "Mark as finished", Modifier.weight(1f)) {
                vm.setFinished(book, !book.isFinished)
            }
        }
        if (showShelves) AddToShelfSheet(vm, book, onDismiss = { showShelves = false })
        if (book.id in downloaded) {
            OutlinedButton(
                onClick = { vm.removeDownload(book.id) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, EasyColors.Outline),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Remove download", color = EasyColors.Text) }
        }
    }
}

@Composable
private fun SmallButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, EasyColors.Outline),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, color = EasyColors.Text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** The rounded panel of details: series, language, publisher, dates and custom columns. */
@Composable
private fun InfoPanel(book: CalibreBook, onShowOnly: (FilterCategory, String) -> Unit) {
    val rows = buildList<Triple<String, String, (() -> Unit)?>> {
        seriesText(book)?.let { add(Triple("Series", it) { onShowOnly(FilterCategory.SERIES, book.series!!) }) }
        book.languages.forEach { code ->
            add(Triple("Language", Browse.languageName(code)) { onShowOnly(FilterCategory.LANGUAGES, code) })
        }
        book.publisher?.let { add(Triple("Publisher", it) { onShowOnly(FilterCategory.PUBLISHERS, it) }) }
        book.pubdate?.let { add(Triple("Published", formatDate(it), null)) }
        book.timestamp?.let { add(Triple("Added", formatDate(it), null)) }
        book.customColumn(RequiredColumn.SHELVES.lookupName)?.values?.takeIf { it.isNotEmpty() }?.let {
            add(Triple("My shelves", it.joinToString(", "), null))
        }
        book.identifiers["isbn"]?.let { add(Triple("ISBN", it, null)) }
        // Any other custom columns from Calibre (EasyReader's own are shown elsewhere).
        book.customColumns
            .filter { !it.lookupName.startsWith("#er_") && it.values.isNotEmpty() }
            .forEach { add(Triple(it.name, customValue(it), null)) }
    }
    if (rows.isEmpty()) return

    Surface(color = EasyColors.Surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            rows.forEachIndexed { i, (label, value, onClick) ->
                if (i > 0) HorizontalDivider(color = EasyColors.Divider)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                        .padding(vertical = 12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = EasyColors.TextMuted, modifier = Modifier.width(120.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (onClick != null) EasyColors.Accent else EasyColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun customValue(c: CustomColumnValue): String = when (c.datatype) {
    "datetime" -> c.values.joinToString(", ") { formatDate(it) }
    "rating" -> c.values.joinToString(", ") { v -> v.toDoubleOrNull()?.let(::stars) ?: v }
    "comments" -> c.values.joinToString("\n") { AnnotatedString.fromHtml(it).text.trim() }
    else -> c.values.joinToString(", ")
}

private fun stars(rating: Double): String {
    val full = rating.toInt().coerceIn(0, 5)
    val half = rating - full >= 0.5
    return "★".repeat(full) + (if (half) "½" else "") + "☆".repeat((5 - full - if (half) 1 else 0).coerceAtLeast(0))
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.UK)

/** "5 March 2024" from Calibre's ISO date/time. */
private fun formatDate(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).toLocalDate().format(dateFormat) }.getOrDefault(iso.take(10))
