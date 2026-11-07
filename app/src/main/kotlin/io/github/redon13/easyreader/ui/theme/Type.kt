package io.github.redon13.easyreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.redon13.easyreader.R

/** Alegreya (serif) for headings and book titles. Bundled as one variable-weight font. */
@OptIn(ExperimentalTextApi::class)
val Alegreya = FontFamily(
    listOf(400, 500, 700, 800).map { w ->
        Font(
            R.font.alegreya,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

/** Alegreya Sans for everything else. */
val AlegreyaSans = FontFamily(
    Font(R.font.alegreya_sans_regular, FontWeight.Normal),
    Font(R.font.alegreya_sans_medium, FontWeight.Medium),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

private fun serif(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = size + 6) =
    TextStyle(fontFamily = Alegreya, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6) =
    TextStyle(fontFamily = AlegreyaSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val EasyTypography = Typography(
    displaySmall = serif(32, FontWeight.ExtraBold, 38),
    headlineMedium = serif(28, FontWeight.ExtraBold, 34),
    headlineSmall = serif(24, FontWeight.Bold, 30),
    titleLarge = serif(22, FontWeight.Bold, 28),
    titleMedium = serif(18, FontWeight.Bold, 24),
    titleSmall = serif(15, FontWeight.Bold, 19),
    bodyLarge = sans(17),
    bodyMedium = sans(16),
    bodySmall = sans(14, line = 18),
    labelLarge = sans(16, FontWeight.Bold),
    labelMedium = sans(14, FontWeight.Medium),
    labelSmall = sans(13, FontWeight.Medium),
)
