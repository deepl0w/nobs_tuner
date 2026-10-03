package io.github.deeplow.nobstuner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.deeplow.nobstuner.model.Notes
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.ui.theme.LocalTuneColors
import io.github.deeplow.nobstuner.ui.theme.MonoNumeric
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The big readout: target note, how many cents off, and the two frequencies.
 * Everything stays laid out when nothing is heard so the screen does not jump
 * between silence and sound.
 */
@Composable
fun NoteReadout(
    targetMidi: Int?,
    cents: Double?,
    detectedHz: Double?,
    targetHz: Double?,
    toleranceCents: Int,
    useFlats: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val tuneColors = LocalTuneColors.current
    val statusColor by animateColorAsState(
        targetValue = when {
            cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
            abs(cents) <= toleranceCents -> tuneColors.inTune
            abs(cents) <= 15.0 -> tuneColors.close
            else -> tuneColors.off
        },
        label = "statusColor",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionArrow(
                visible = cents != null && cents < -toleranceCents,
                text = "♭",
                color = statusColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = targetMidi?.let { Notes.pitchClassName(it, useFlats) } ?: "\u266A",
                style = MaterialTheme.typography.displayLarge,
                fontSize = when {
                    targetMidi == null -> if (compact) 42.sp else 56.sp
                    compact -> 64.sp
                    else -> 86.sp
                },
                fontWeight = FontWeight.Bold,
                color = if (targetMidi == null) statusColor.copy(alpha = 0.45f) else statusColor,
            )
            Text(
                text = targetMidi?.let { Notes.octaveOf(it).toString() } ?: "",
                style = MaterialTheme.typography.headlineMedium,
                color = statusColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = if (compact) 20.dp else 28.dp),
            )
            Spacer(Modifier.width(12.dp))
            DirectionArrow(
                visible = cents != null && cents > toleranceCents,
                text = "♯",
                color = statusColor,
            )
        }

        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))

        Text(
            text = when {
                cents == null -> "Play a note"
                abs(cents) <= toleranceCents -> "In tune"
                else -> "%+d cents".format(cents.roundToInt())
            },
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
        )

        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrequencyChip(label = "Heard", hz = detectedHz)
            FrequencyChip(label = "Target", hz = targetHz)
        }
    }
}

@Composable
private fun DirectionArrow(visible: Boolean, text: String, color: Color) {
    // Reserve the space whether or not the symbol is showing, so the note name
    // stays optically centred as the reading moves between flat and sharp.
    Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = color,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun FrequencyChip(label: String, hz: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hz?.let { "%.1f Hz".format(it) } ?: "— Hz",
            style = MaterialTheme.typography.titleMedium.merge(MonoNumeric),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One chip per string. Tapping pins the tuner to that string; tapping the
 * pinned one releases it back to automatic detection.
 */
/**
 * Splits [count] chips into rows of as equal a length as possible, given that
 * at most [maxPerRow] fit side by side.
 *
 * Filling each row to the brim leaves an orphan — six strings in rows of five
 * reads as "5 and 1" rather than as one instrument. Balancing gives 3 + 3, or
 * 2 + 2 + 2 when the window is narrower, which keeps the strings legible as a
 * set. Longer rows come first, so seven strings go 4 + 3.
 */
internal fun balancedRows(count: Int, maxPerRow: Int): List<Int> {
    if (count <= 0) return emptyList()
    val perRow = maxPerRow.coerceAtLeast(1)
    val rows = (count + perRow - 1) / perRow
    val base = count / rows
    val remainder = count % rows
    return List(rows) { index -> base + if (index < remainder) 1 else 0 }
}

@Composable
fun StringSelector(
    tuning: Tuning,
    activeIndex: Int?,
    pinnedIndex: Int?,
    tunedIndices: Set<Int>,
    useFlats: Boolean,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = 8.dp
        val minChip = 48.dp   // a comfortable touch target
        val maxChip = 76.dp
        val count = tuning.stringCount

        // How many fit at the smallest size we are willing to draw.
        val maxPerRow = ((maxWidth + spacing) / (minChip + spacing))
            .toInt()
            .coerceAtLeast(1)
        val rows = balancedRows(count, maxPerRow)
        val widest = rows.maxOrNull() ?: count

        // One size for every chip, taken from the longest row, so the rows line
        // up as a grid rather than drifting in size.
        val chipSize = ((maxWidth - spacing * (widest - 1)) / widest)
            .coerceIn(minChip, maxChip)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var index = 0
            rows.forEach { rowLength ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(rowLength) {
                        val stringIndex = index++
                        val midi = tuning.strings[stringIndex]
                        StringChip(
                            label = Notes.pitchClassName(midi, useFlats),
                            octave = Notes.octaveOf(midi),
                            stringNumber = count - stringIndex,
                            size = chipSize,
                            isActive = stringIndex == activeIndex,
                            isPinned = stringIndex == pinnedIndex,
                            isTuned = stringIndex in tunedIndices,
                            onClick = {
                                onSelect(if (stringIndex == pinnedIndex) null else stringIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StringChip(
    label: String,
    octave: Int,
    stringNumber: Int,
    size: Dp,
    isActive: Boolean,
    isPinned: Boolean,
    isTuned: Boolean,
    onClick: () -> Unit,
) {
    val tuneColors = LocalTuneColors.current
    val container by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            isTuned -> tuneColors.inTune.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "chipContainer",
    )
    val borderAlpha by animateFloatAsState(if (isPinned) 1f else 0f, label = "chipBorder")

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier
            .size(size)
            .border(
                width = 2.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                shape = CircleShape,
            )
            .semantics {
                contentDescription = buildString {
                    append("String $stringNumber, $label$octave")
                    if (isTuned) append(", tuned")
                    if (isPinned) append(", selected")
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                )
                Text(
                    text = octave.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isTuned) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = tuneColors.inTune,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

/** Centred helper text used for empty and permission states. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}
