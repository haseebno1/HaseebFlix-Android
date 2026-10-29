@file:OptIn(ExperimentalTextApi::class)

package com.haseebflix.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.haseebflix.app.R

val Montserrat = FontFamily(
    listOf(500, 600, 700, 800).map {
        Font(R.font.montserrat, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    }
)
val Inter = FontFamily(
    listOf(400, 500, 600, 700).map {
        Font(R.font.inter, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    }
)

private fun m(w: Int, size: Int, line: Int, track: Float = 0f) =
    TextStyle(fontFamily = Montserrat, fontWeight = FontWeight(w), fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.em)

private fun i(w: Int, size: Int, line: Int, track: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = FontWeight(w), fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.em)

/** Mapped from DESIGN.md typography tokens. */
val HfTypography = Typography(
    displayLarge = m(800, 40, 48, -0.02f),        // display-hero
    displayMedium = m(800, 30, 36, -0.02f),       // display-hero-mobile
    displaySmall = m(800, 26, 32, -0.02f),
    headlineLarge = m(700, 26, 32, -0.02f),       // headline-lg
    headlineMedium = m(700, 20, 26, -0.01f),      // headline-md
    headlineSmall = m(600, 16, 22),               // headline-sm
    titleLarge = m(700, 18, 24),
    titleMedium = i(600, 15, 20),                 // title-medium
    titleSmall = i(600, 14, 18),
    bodyLarge = i(400, 15, 22),                   // body-large
    bodyMedium = i(400, 13, 18),                  // body-medium
    bodySmall = i(400, 12, 16),
    labelLarge = i(600, 14, 18),                  // label-large
    labelMedium = i(500, 12, 16),                 // label-medium
    labelSmall = i(600, 11, 14),                  // label-small (11sp floor)
)

val HfShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
