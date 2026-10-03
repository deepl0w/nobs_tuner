package io.github.deeplow.nobstuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.ui.theme.LocalTuneColors
import io.github.deeplow.nobstuner.ui.theme.MonoNumeric
import kotlin.math.abs
import kotlin.math.roundToInt

/** Full-scale deviation shown by every style, in cents either side of centre. */
internal const val METER_RANGE_CENTS = 50f

/**
 * Draws the deviation in whichever idiom the user picked.
 *
 * All four take the same inputs and occupy the same box, so switching style
 * never reshuffles the rest of the screen.
 */
@Composable
fun TunerMeter(
    style: DisplayStyle,
    cents: Double?,
    toleranceCents: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    when (style) {
        DisplayStyle.NEEDLE -> TuningMeter(cents, toleranceCents, modifier, maxWidth)
        DisplayStyle.BAR -> BarMeter(cents, toleranceCents, modifier, maxWidth)
        DisplayStyle.STROBE -> StrobeMeter(cents, toleranceCents, modifier, maxWidth)
        DisplayStyle.DIGITAL -> DigitalMeter(cents, toleranceCents, modifier, maxWidth)
    }
}

/** Colour for a deviation: in tune, nearly there, or well out. */
@Composable
internal fun statusColorFor(cents: Double?, toleranceCents: Int): Color {
    val tuneColors = LocalTuneColors.current
    return when {
        cents == null -> tuneColors.idle
        abs(cents) <= toleranceCents -> tuneColors.inTune
        abs(cents) <= 15.0 -> tuneColors.close
        else -> tuneColors.off
    }
}

internal fun describe(cents: Double?, toleranceCents: Int): String = when {
    cents == null -> "No note detected"
    abs(cents) <= toleranceCents -> "In tune"
    cents < 0 -> "${abs(cents).roundToInt()} cents flat"
    else -> "${cents.roundToInt()} cents sharp"
}

private fun Modifier.meterBox(maxWidth: Dp, ratio: Float): Modifier =
    this.then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
        .fillMaxWidth()
        .aspectRatio(ratio)

// ---- Bar ----------------------------------------------------------------

/**
 * A straight scale with a sliding marker. The most compact of the four, and the
 * easiest to read out of the corner of your eye while both hands are busy.
 */
@Composable
fun BarMeter(
    cents: Double?,
    toleranceCents: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val tuneColors = LocalTuneColors.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val target = statusColorFor(cents, toleranceCents)

    val clamped = (cents ?: 0.0)
        .coerceIn(-METER_RANGE_CENTS.toDouble(), METER_RANGE_CENTS.toDouble())
        .toFloat()
    val position by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "barPosition",
    )
    val markerColor by animateColorAsState(target, label = "barColor")
    val description = describe(cents, toleranceCents)

    Canvas(modifier.meterBox(maxWidth, 3.4f).semantics { contentDescription = description }) {
        val barHeight = 18.dp.toPx()
        val centreY = size.height * 0.56f
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val usable = right - left

        drawRoundRectLine(left, right, centreY, barHeight, track)

        // Acceptance band around the middle.
        val halfBand = (toleranceCents / METER_RANGE_CENTS) * (usable / 2f)
        val middle = (left + right) / 2f
        drawRoundRectLine(
            middle - halfBand, middle + halfBand, centreY, barHeight,
            if (cents != null) tuneColors.inTune else tuneColors.inTune.copy(alpha = 0.35f),
        )

        // Ticks every ten cents, longer at the quarter points.
        var tick = -50
        while (tick <= 50) {
            val major = tick % 25 == 0
            val x = middle + (tick / METER_RANGE_CENTS) * (usable / 2f)
            val half = if (major) 16.dp.toPx() else 11.dp.toPx()
            drawLine(
                color = outline.copy(alpha = if (major) 0.9f else 0.45f),
                start = Offset(x, centreY - half),
                end = Offset(x, centreY + half),
                strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            tick += 10
        }

        val markerX = middle + (position / METER_RANGE_CENTS) * (usable / 2f)
        val markerHalf = (barHeight + 18.dp.toPx()) / 2f
        drawLine(
            color = markerColor,
            start = Offset(markerX, centreY - markerHalf),
            end = Offset(markerX, centreY + markerHalf),
            strokeWidth = 9.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawRoundRectLine(
    startX: Float,
    endX: Float,
    centreY: Float,
    thickness: Float,
    color: Color,
) {
    drawLine(
        color = color,
        start = Offset(startX + thickness / 2f, centreY),
        end = Offset(endX - thickness / 2f, centreY),
        strokeWidth = thickness,
        cap = StrokeCap.Round,
    )
}

// ---- Strobe -------------------------------------------------------------

/**
 * Scrolling stripes, after the strobe discs of mechanical tuners.
 *
 * The bands drift right when the note is sharp and left when it is flat, faster
 * the further out it is, and freeze when it is right. Three bands of different
 * stripe widths give both a coarse and a fine reading at once — the narrow band
 * is still visibly crawling when the wide one looks stopped.
 */
@Composable
fun StrobeMeter(
    cents: Double?,
    toleranceCents: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val status = statusColorFor(cents, toleranceCents)
    val stripeColor by animateColorAsState(
        if (cents == null) status.copy(alpha = 0.22f) else status,
        label = "strobeColor",
    )

    // Phase is integrated over real time rather than animated to a target: the
    // point of a strobe is the rate of drift, not where the stripes happen to be.
    var phase by remember { mutableFloatStateOf(0f) }
    val drift = rememberUpdatedState(cents ?: 0.0)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val seconds = (now - last) / 1_000_000_000f
                    phase += (drift.value.toFloat()) * STROBE_PIXELS_PER_CENT_PER_SECOND * seconds
                }
                last = now
            }
        }
    }

    val description = describe(cents, toleranceCents)
    Canvas(modifier.meterBox(maxWidth, 2.6f).semantics { contentDescription = description }) {
        val rows = 3
        val gap = 8.dp.toPx()
        val rowHeight = (size.height - gap * (rows - 1)) / rows
        val widths = listOf(30.dp.toPx(), 21.dp.toPx(), 14.dp.toPx())

        widths.forEachIndexed { index, stripeWidth ->
            val top = index * (rowHeight + gap)
            drawRect(color = track, topLeft = Offset(0f, top), size = Size(size.width, rowHeight))
            clipRect(0f, top, size.width, top + rowHeight) {
                val period = stripeWidth * 2f
                // Scale the drift by stripe width so the narrow bands beat faster,
                // which is what makes a real strobe readable at fine offsets.
                val offset = ((phase * (30.dp.toPx() / stripeWidth)) % period + period) % period
                var x = -period + offset
                while (x < size.width + period) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(x, top),
                        size = Size(stripeWidth, rowHeight),
                    )
                    x += period
                }
            }
        }
    }
}

private const val STROBE_PIXELS_PER_CENT_PER_SECOND = 1.6f

// ---- Digital ------------------------------------------------------------

/**
 * The cents figure in large type over a row of lights, for players who would
 * rather read a number than judge an angle.
 */
@Composable
fun DigitalMeter(
    cents: Double?,
    toleranceCents: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val tuneColors = LocalTuneColors.current
    val off = MaterialTheme.colorScheme.surfaceVariant
    val status = statusColorFor(cents, toleranceCents)
    val lit by animateColorAsState(status, label = "digitalColor")
    val description = describe(cents, toleranceCents)

    Column(
        modifier = modifier
            .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                cents == null -> "–"
                abs(cents) < 0.5 -> "0"
                else -> "%+d".format(cents.roundToInt())
            },
            style = MaterialTheme.typography.displayLarge.merge(MonoNumeric),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (cents == null) tuneColors.idle else lit,
        )
        Text(
            text = "CENTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f),
        ) {
            val count = 21                      // ten either side of centre
            val spacing = 4.dp.toPx()
            val cellWidth = (size.width - spacing * (count - 1)) / count
            val centreIndex = count / 2
            // Which lights are on: everything between the middle and the reading.
            val position = cents?.let {
                (it / METER_RANGE_CENTS * centreIndex).coerceIn(
                    -centreIndex.toDouble(), centreIndex.toDouble(),
                ).roundToInt()
            }
            for (i in 0 until count) {
                val delta = i - centreIndex
                val on = when {
                    position == null -> false
                    position == 0 -> delta == 0
                    position > 0 -> delta in 1..position
                    else -> delta in position..-1
                }
                val isCentre = delta == 0
                val color = when {
                    on -> lit
                    isCentre && cents != null && abs(cents) <= toleranceCents -> tuneColors.inTune
                    isCentre -> off.copy(alpha = 0.9f)
                    else -> off
                }
                drawRect(
                    color = color,
                    topLeft = Offset(i * (cellWidth + spacing), 0f),
                    size = Size(cellWidth, size.height),
                )
            }
        }
    }
}
