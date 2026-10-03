package io.github.deeplow.nobstuner.ui.components

import androidx.annotation.DrawableRes
import io.github.deeplow.nobstuner.R
import io.github.deeplow.nobstuner.model.InstrumentFamily

/**
 * Picture for each instrument family.
 *
 * Kept in the UI layer so the model stays free of Android resource ids.
 */
@DrawableRes
fun InstrumentFamily.iconRes(): Int = when (this) {
    InstrumentFamily.GUITAR -> R.drawable.ic_instrument_guitar
    InstrumentFamily.BASS -> R.drawable.ic_instrument_bass
    InstrumentFamily.UKULELE -> R.drawable.ic_instrument_ukulele
    InstrumentFamily.BANJO -> R.drawable.ic_instrument_banjo
    InstrumentFamily.MANDOLIN -> R.drawable.ic_instrument_mandolin
    InstrumentFamily.ORCHESTRAL -> R.drawable.ic_instrument_orchestral
    InstrumentFamily.OTHER -> R.drawable.ic_instrument_other
}

/** Icon for chromatic mode, which belongs to no instrument in particular. */
@DrawableRes
fun chromaticIconRes(): Int = R.drawable.ic_instrument_other
