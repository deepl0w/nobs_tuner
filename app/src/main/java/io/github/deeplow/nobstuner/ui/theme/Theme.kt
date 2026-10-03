package io.github.deeplow.nobstuner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.deeplow.nobstuner.data.ThemeMode

/** Status colours for the meter, resolved once per theme rather than per frame. */
data class TuneColorScheme(
    val inTune: Color,
    val close: Color,
    val off: Color,
    val idle: Color,
)

val LocalTuneColors: ProvidableCompositionLocal<TuneColorScheme> = staticCompositionLocalOf {
    TuneColorScheme(
        inTune = TuneColors.inTuneLight,
        close = TuneColors.closeLight,
        off = TuneColors.offLight,
        idle = TuneColors.idleLight,
    )
}

private val LightScheme = lightColorScheme(
    primary = AmberLight,
    onPrimary = Color.White,
    primaryContainer = AmberLightContainer,
    onPrimaryContainer = Color(0xFF271900),
    secondary = SlateLight,
    secondaryContainer = SlateLightContainer,
    onSecondaryContainer = Color(0xFF0B1A20),
    background = SurfaceLight,
    surface = SurfaceLight,
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4E4639),
    outline = Color(0xFF807667),
)

private val DarkScheme = darkColorScheme(
    primary = AmberDark,
    onPrimary = Color(0xFF412D00),
    primaryContainer = AmberDarkContainer,
    onPrimaryContainer = Color(0xFFFFDEA3),
    secondary = SlateDark,
    secondaryContainer = SlateDarkContainer,
    onSecondaryContainer = Color(0xFFD5E3EB),
    background = SurfaceDark,
    surface = SurfaceDark,
    onSurface = Color(0xFFE9E1D8),
    surfaceVariant = Color(0xFF4E4639),
    onSurfaceVariant = Color(0xFFD1C5B4),
    outline = Color(0xFF9A9080),
)

@Composable
fun NobsTunerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    val tuneColors = if (dark) {
        TuneColorScheme(
            inTune = TuneColors.inTuneDark,
            close = TuneColors.closeDark,
            off = TuneColors.offDark,
            idle = TuneColors.idleDark,
        )
    } else {
        TuneColorScheme(
            inTune = TuneColors.inTuneLight,
            close = TuneColors.closeLight,
            off = TuneColors.offLight,
            idle = TuneColors.idleLight,
        )
    }

    CompositionLocalProvider(LocalTuneColors provides tuneColors) {
        MaterialTheme(colorScheme = colorScheme, typography = NobsTunerTypography, content = content)
    }
}
