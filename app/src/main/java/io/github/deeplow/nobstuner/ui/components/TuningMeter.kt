package io.github.deeplow.nobstuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.deeplow.nobstuner.ui.theme.LocalTuneColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Full-scale deflection of the needle, in cents either side of centre. */
internal const val RANGE_CENTS = 50f

/** Degrees the needle travels either side of straight up at full scale. */
internal const val HALF_SWEEP_DEGREES = 90f

/**
 * Degrees from straight up at which the needle sits for a given offset.
 *
 * The dial is a half circle, so full scale ([RANGE_CENTS]) is a quarter turn
 * either way. Everything drawn on the dial has to agree with this or the needle
 * and the markings tell the user different things.
 */
internal fun needleDegreesFor(cents: Float): Float =
    (cents / RANGE_CENTS) * HALF_SWEEP_DEGREES

/** Half-width, in degrees, of the acceptance band for a tolerance in cents. */
internal fun toleranceHalfSweepDegrees(toleranceCents: Int): Float =
    needleDegreesFor(toleranceCents.toFloat())

/**
 * Half-circle needle gauge showing how far the played note is from its target.
 *
 * Pass null for [cents] when nothing is being heard; the needle parks at centre
 * and the dial greys out.
 */
@Composable
fun TuningMeter(
    cents: Double?,
    toleranceCents: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val tuneColors = LocalTuneColors.current
    val outline = MaterialTheme.colorScheme.outline
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val clamped = (cents ?: 0.0).coerceIn(-RANGE_CENTS.toDouble(), RANGE_CENTS.toDouble()).toFloat()
    val targetColor = when {
        cents == null -> tuneColors.idle
        abs(cents) <= toleranceCents -> tuneColors.inTune
        abs(cents) <= 15.0 -> tuneColors.close
        else -> tuneColors.off
    }

    // A spring rather than a tween: the needle should feel weighted, and a
    // critically-damped spring never overshoots past the in-tune band.
    val needleCents by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "needleCents",
    )
    val needleColor by animateColorAsState(targetColor, label = "needleColor")

    val description = when {
        cents == null -> "No note detected"
        abs(cents) <= toleranceCents -> "In tune"
        cents < 0 -> "${abs(cents).roundToInt()} cents flat"
        else -> "${cents.roundToInt()} cents sharp"
    }

    Canvas(
        modifier = modifier
            .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxWidth()
            .aspectRatio(1.85f)
            .semantics { contentDescription = description },
    ) {
        drawDial(
            needleCents = needleCents,
            toleranceCents = toleranceCents,
            needleColor = needleColor,
            trackColor = trackColor,
            tickColor = outline,
            inTuneColor = tuneColors.inTune,
            active = cents != null,
        )
    }
}

private fun DrawScope.drawDial(
    needleCents: Float,
    toleranceCents: Int,
    needleColor: Color,
    trackColor: Color,
    tickColor: Color,
    inTuneColor: Color,
    active: Boolean,
) {
    val strokeWidth = 14.dp.toPx()
    val padding = strokeWidth / 2f + 6.dp.toPx()
    val radius = ((size.width / 2f) - padding).coerceAtMost(size.height - padding)
    val centre = Offset(size.width / 2f, size.height - padding / 2f)

    val arcTopLeft = Offset(centre.x - radius, centre.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    // Track: a half circle opening upwards.
    drawArc(
        color = trackColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    // The acceptance band, drawn symmetrically about straight up.
    val toleranceSweep = toleranceHalfSweepDegrees(toleranceCents)
    drawArc(
        color = if (active) inTuneColor else inTuneColor.copy(alpha = 0.35f),
        startAngle = 270f - toleranceSweep,
        sweepAngle = toleranceSweep * 2f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )

    // Ticks every 10 cents, with longer marks at the extremes and centre.
    var tick = -50
    while (tick <= 50) {
        val major = tick % 25 == 0
        val angle = angleFor(tick.toFloat())
        val inner = radius - strokeWidth / 2f - (if (major) 14.dp.toPx() else 8.dp.toPx())
        val outer = radius - strokeWidth / 2f - 2.dp.toPx()
        drawLine(
            color = tickColor.copy(alpha = if (major) 0.9f else 0.45f),
            start = pointOn(centre, inner, angle),
            end = pointOn(centre, outer, angle),
            strokeWidth = if (major) 3.dp.toPx() else 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        tick += 10
    }

    // Needle.
    val needleAngle = angleFor(needleCents)
    val needleLength = radius - strokeWidth - 10.dp.toPx()
    drawLine(
        color = needleColor,
        start = pointOn(centre, 10.dp.toPx(), needleAngle + Math.PI.toFloat()),
        end = pointOn(centre, needleLength, needleAngle),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = needleColor, radius = 11.dp.toPx(), center = centre)
    drawCircle(
        color = trackColor,
        radius = 5.dp.toPx(),
        center = centre,
    )
}

/** Radians from straight up, positive clockwise (sharp to the right). */
private fun angleFor(cents: Float): Float =
    Math.toRadians(needleDegreesFor(cents).toDouble()).toFloat()

private fun pointOn(centre: Offset, distance: Float, angleFromUp: Float): Offset =
    Offset(
        x = centre.x + distance * sin(angleFromUp),
        y = centre.y - distance * cos(angleFromUp),
    )
