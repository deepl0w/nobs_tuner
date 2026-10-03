package io.github.deeplow.nobstuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val NobsTunerTypography = Default.copy(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Medium),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Tabular figures for anything that updates many times a second — without them
 * the frequency readout jitters sideways as digit widths change.
 */
val MonoNumeric = TextStyle(fontFamily = FontFamily.Monospace)
