package io.github.redon13.easyreader.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.redon13.easyreader.calibre.CalibreBook
import io.github.redon13.easyreader.calibre.isFavourite
import io.github.redon13.easyreader.calibre.isFinished
import io.github.redon13.easyreader.ui.theme.EasyColors

/** Whether covers show reading progress (the "Progress bar on covers" setting). */
val LocalShowCoverProgress = compositionLocalOf { true }

/** The book shown in the tablet's details pane, outlined in amber in the grid. */
val LocalSelectedBookId = compositionLocalOf<Int?> { null }

/** A book cover (2:3) with the title shown underneath while it loads or if there's no cover. */
@Composable
fun BookCoverImage(book: CalibreBook, coverUrl: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(EasyColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!small) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                color = EasyColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A thin amber bar showing how far through a book the owner is (0–1). */
@Composable
fun ReadingProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = EasyColors.Accent,
        trackColor = EasyColors.SurfaceHigh,
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = modifier.height(3.dp).clip(RoundedCornerShape(2.dp)),
    )
}

/** One book in a grid or row: cover, reading progress, title and author. */
@Composable
fun BookTile(
    book: CalibreBook,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onToggleFavourite: (() -> Unit)? = null,
) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box {
            val selected = LocalSelectedBookId.current == book.id
            BookCoverImage(
                book,
                coverUrl,
                Modifier
                    .fillMaxWidth()
                    .then(if (selected) Modifier.border(3.dp, EasyColors.Accent, RoundedCornerShape(10.dp)) else Modifier),
            )
            if (book.isFinished) FinishedBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            if (onToggleFavourite != null) {
                FavouriteButton(book.isFavourite, onToggleFavourite, Modifier.align(Alignment.TopEnd))
            }
        }
        // Finished books (green tick) don't show a progress bar.
        if (progress != null && LocalShowCoverProgress.current && !book.isFinished) {
            Spacer(Modifier.height(6.dp))
            ReadingProgressBar(progress, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.authors.isNotEmpty()) {
            Text(
                book.authors.joinToString(" & "),
                style = MaterialTheme.typography.bodySmall,
                color = EasyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Heart in the corner of a cover: tap to add to or remove from favourites. */
@Composable
fun FavouriteButton(favourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(40.dp)) {
        Box(
            Modifier.size(30.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                tint = if (favourite) EasyColors.Accent else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Small ✓ on the cover of a finished book. */
@Composable
fun FinishedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).background(EasyColors.Success, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Check, contentDescription = "Finished", tint = EasyColors.OnAccent, modifier = Modifier.size(16.dp))
    }
}

/** One book in list view: small cover, title, author, series and formats. */
@Composable
fun BookListRow(
    book: CalibreBook,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCoverImage(book, coverUrl, Modifier.width(56.dp), small = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.authors.isNotEmpty()) {
                Text(
                    book.authors.joinToString(" & "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EasyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val extra = listOfNotNull(
                if (book.isFinished) "Finished" else null,
                seriesText(book),
                book.formats.joinToString(" · ").ifEmpty { null },
                progress?.takeIf { !book.isFinished }?.let { "${(it * 100).toInt()}% read" },
            )
            if (extra.isNotEmpty()) {
                Text(
                    extra.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = EasyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Dune #2", or null if the book isn't in a series. */
fun seriesText(book: CalibreBook): String? = book.series?.let { s ->
    val i = book.seriesIndex
    if (i == null) s else "$s #${if (i == Math.floor(i)) i.toLong().toString() else i.toString()}"
}

/** A simple four-square "grid view" icon (not included in the basic icon set). */
val GridViewIcon: ImageVector by lazy {
    ImageVector.Builder("GridView", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            for ((x, y) in listOf(4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f)) {
                moveTo(x + 1f, y)
                horizontalLineTo(x + 6f)
                quadTo(x + 7f, y, x + 7f, y + 1f)
                verticalLineTo(y + 6f)
                quadTo(x + 7f, y + 7f, x + 6f, y + 7f)
                horizontalLineTo(x + 1f)
                quadTo(x, y + 7f, x, y + 6f)
                verticalLineTo(y + 1f)
                quadTo(x, y, x + 1f, y)
                close()
            }
        }
    }.build()
}
