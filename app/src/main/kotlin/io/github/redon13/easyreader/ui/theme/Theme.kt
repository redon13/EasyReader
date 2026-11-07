package io.github.redon13.easyreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val Scheme = darkColorScheme(
    primary = EasyColors.Accent,
    onPrimary = EasyColors.OnAccent,
    primaryContainer = EasyColors.HeroCard,
    onPrimaryContainer = EasyColors.Text,
    secondary = EasyColors.Accent,
    onSecondary = EasyColors.OnAccent,
    background = EasyColors.Background,
    onBackground = EasyColors.Text,
    surface = EasyColors.Background,
    onSurface = EasyColors.Text,
    surfaceVariant = EasyColors.SurfaceHigh,
    onSurfaceVariant = EasyColors.TextMuted,
    surfaceContainerLowest = EasyColors.Background,
    surfaceContainerLow = EasyColors.Surface,
    surfaceContainer = EasyColors.Surface,
    surfaceContainerHigh = EasyColors.SurfaceHigh,
    surfaceContainerHighest = EasyColors.SurfaceHigh,
    outline = EasyColors.Outline,
    outlineVariant = EasyColors.Divider,
    error = EasyColors.Error,
)

private val EasyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** EasyReader is always dark navy with an amber accent (fixed by design). */
@Composable
fun EasyReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = EasyTypography, shapes = EasyShapes, content = content)
}
