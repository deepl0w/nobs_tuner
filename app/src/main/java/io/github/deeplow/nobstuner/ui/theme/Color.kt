package io.github.deeplow.nobstuner.ui.theme

import androidx.compose.ui.graphics.Color

// Warm brass/amber primaries — an instrument-case palette rather than a stock
// Material purple, and dark enough in both schemes to carry white text.
val AmberLight = Color(0xFF7C5800)
val AmberLightContainer = Color(0xFFFFDEA3)
val AmberDark = Color(0xFFF2BF48)
val AmberDarkContainer = Color(0xFF5E4200)

val SlateLight = Color(0xFF4F5B62)
val SlateLightContainer = Color(0xFFD5E3EB)
val SlateDark = Color(0xFFB7C8D2)
val SlateDarkContainer = Color(0xFF374850)

val SurfaceLight = Color(0xFFFFFBF2)
val SurfaceDark = Color(0xFF15130E)

/** Semantic colours for the tuning meter, kept out of the Material scheme. */
object TuneColors {
    val inTuneLight = Color(0xFF1B7A3D)
    val inTuneDark = Color(0xFF6FD894)
    val closeLight = Color(0xFFB07400)
    val closeDark = Color(0xFFF0C048)
    val offLight = Color(0xFFB3261E)
    val offDark = Color(0xFFF2846C)
    val idleLight = Color(0xFF8A8176)
    val idleDark = Color(0xFF6B6459)
}
