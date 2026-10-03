package io.github.deeplow.stringtune.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the window looks like right now, in the terms the screens actually care
 * about. Derived from the real measured size rather than the device type, so a
 * phone in landscape, a tablet in portrait and a freeform multi-window box all
 * get sensible treatment.
 */
@Immutable
data class WindowShape(
    val width: Dp,
    val height: Dp,
) {
    /** Material's compact / medium / expanded width buckets. */
    val isCompactWidth: Boolean get() = width < 600.dp
    val isExpandedWidth: Boolean get() = width >= 840.dp

    /** Too short to stack a dial, a readout and a row of strings. */
    val isShort: Boolean get() = height < 520.dp

    /** Enough height for the dial and readout, but not with room to spare. */
    val isTightHeight: Boolean get() = height < 700.dp

    /**
     * Put the controls beside the dial instead of under it. True for any
     * landscape-ish window that is short, and for genuinely large screens where
     * a single centred column would waste most of the display.
     */
    val prefersSideBySide: Boolean
        get() = (width > height && isShort) || width >= 840.dp

    /**
     * How wide the reading column is allowed to get. Past this a tuner stops
     * being easier to read and just becomes a very large dial.
     */
    val readingMaxWidth: Dp get() = if (isCompactWidth) 440.dp else 460.dp
}

/**
 * Centres [content] and stops it stretching past [maxWidth].
 *
 * Long lines of settings and list rows spanning a 10" tablet are hard to scan;
 * every screen funnels its body through this.
 */
@Composable
fun ContentPane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}
