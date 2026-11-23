package io.github.redon13.easyreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.redon13.easyreader.data.ReaderTheme
import io.github.redon13.easyreader.ui.theme.EasyColors

private fun icon(name: String, path: String) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

internal val HeadphonesIcon = icon(
    "Headphones",
    "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z",
)
private val PauseIcon = icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
private val NextIcon = icon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
private val PreviousIcon = icon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")

/** The read-aloud controls: previous, play/pause, next, speed, which voice, and close. */
@Composable
fun ReadAloudBar(ui: ReadAloudUi, theme: ReaderTheme, controller: ReadAloudController) {
    val fg = Color(theme.text)
    val muted = Color(theme.secondary)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(theme.background))
            .background(muted.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::previous) { Icon(PreviousIcon, "Previous paragraph", tint = fg) }
            FilledIconButton(
                onClick = controller::playPause,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = EasyColors.Accent, contentColor = Color.Black),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(if (ui.playing) PauseIcon else Icons.Default.PlayArrow, if (ui.playing) "Pause" else "Play")
            }
            IconButton(onClick = controller::next) { Icon(NextIcon, "Next paragraph", tint = fg) }
            TextButton(onClick = controller::nextSpeed) {
                Text(speedLabel(ui.speed), color = fg, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                ui.voice,
                color = muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = controller::stop) { Icon(Icons.Default.Close, "Stop reading aloud", tint = fg) }
        }
        ui.notice?.let {
            Text(
                it,
                color = muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

private fun speedLabel(speed: Double): String {
    val text = if (speed % 1.0 == 0.0) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "$text×"
}
