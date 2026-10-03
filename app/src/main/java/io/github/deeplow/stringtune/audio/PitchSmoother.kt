package io.github.deeplow.stringtune.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** What the UI should show for the current moment. */
data class TrackedPitch(
    val frequencyHz: Double,
    val clarity: Double,
    val levelDbfs: Double,
)

/**
 * Turns the detector's per-frame estimates into something a needle can follow.
 *
 * Three things happen here:
 *  - frames that are too quiet or too noisy are dropped outright;
 *  - a short median over recent frames removes the occasional octave flip that
 *    survives YIN's threshold step;
 *  - the displayed value eases towards the median, quickly when it is far away
 *    and slowly when it is close, so the reading settles instead of shivering.
 *
 * Smoothing is done in log-frequency, where a fixed step is a fixed number of
 * cents regardless of octave.
 */
class PitchSmoother(
    private val historySize: Int = 5,
    private val minClarity: Double = 0.76,
    private val minLevelDbfs: Double = -48.0,
    /** Frames of silence tolerated before the display is cleared. */
    private val releaseFrames: Int = 10,
) {
    private val history = ArrayDeque<Double>(historySize)
    private var smoothedLogHz: Double? = null
    private var silentFrames = 0
    private var lastClarity = 0.0
    private var octaveFoldFrames = 0
    private var peakLevelDbfs = -120.0

    fun reset() {
        history.clear()
        smoothedLogHz = null
        silentFrames = 0
        lastClarity = 0.0
        octaveFoldFrames = 0
        peakLevelDbfs = -120.0
    }

    fun push(estimate: PitchEstimate): TrackedPitch? {
        val frequency = estimate.frequencyHz
        val usable = frequency != null &&
            frequency > 0.0 &&
            estimate.clarity >= minClarity &&
            estimate.levelDbfs >= minLevelDbfs

        if (!usable) {
            silentFrames++
            if (silentFrames >= releaseFrames) {
                reset()
                return null
            }
            // Within the release window keep showing the last good reading, so a
            // plucked string that is decaying does not blink out mid-adjustment.
            val held = smoothedLogHz ?: return null
            return TrackedPitch(exp(held), lastClarity, estimate.levelDbfs)
        }

        silentFrames = 0
        lastClarity = estimate.clarity

        peakLevelDbfs = maxOf(peakLevelDbfs, estimate.levelDbfs)

        // Resolve the octave before touching the history, so the check for a
        // settled lock sees the full window rather than one short of it.
        val adjusted = stabiliseOctave(ln(frequency), estimate.levelDbfs)
        if (history.size == historySize) history.removeFirst()
        history.addLast(adjusted)

        val median = history.sorted()[history.size / 2]
        val previous = smoothedLogHz
        if (previous == null) {
            smoothedLogHz = median
            return TrackedPitch(exp(median), estimate.clarity, estimate.levelDbfs)
        }

        // Distance in cents drives how hard we chase: a deliberate retune should
        // land immediately, a wobbling sustain should not.
        val cents = abs(median - previous) * CENTS_PER_LOG_UNIT
        val alpha = when {
            cents > 120.0 -> 0.85
            cents > 35.0 -> 0.45
            else -> 0.18
        }
        val next = previous + (median - previous) * alpha
        smoothedLogHz = next
        return TrackedPitch(exp(next), estimate.clarity, estimate.levelDbfs)
    }

    /**
     * Folds a reading that has jumped by almost exactly an octave back to where
     * the note was.
     *
     * As a plucked or bowed note dies away its fundamental fades before the
     * partials above it, and the detector starts hearing the second harmonic as
     * the note — the display jumps an octave just as the player is finishing an
     * adjustment. A real octave change does not disappear after a moment, so a
     * run of folds longer than [OCTAVE_FOLD_LIMIT] is taken at face value and
     * the new octave is accepted.
     */
    private fun stabiliseOctave(logHz: Double, levelDbfs: Double): Double {
        val previous = smoothedLogHz ?: return logHz
        // Only fold once the lock has settled; during a note's attack the
        // history is still filling and there is nothing trustworthy to fold to.
        if (history.size < historySize) return logHz

        val cents = (logHz - previous) * CENTS_PER_LOG_UNIT
        val octaves = (cents / 1200.0).roundToInt()

        // Only upward jumps are folded. A fading fundamental makes the detector
        // hear the octave above, never the octave below, and folding downward
        // jumps too would trap the reading up there if a noisy attack happened
        // to lock on the harmonic first.
        if (octaves <= 0 || abs(cents - octaves * 1200.0) > OCTAVE_SNAP_CENTS) {
            octaveFoldFrames = 0
            return logHz
        }
        // A note that is dying away cannot be the player moving up an octave:
        // a real new note arrives with an attack, not 12 dB below the one before
        // it. While the level is this far down, the jump is always an artifact.
        val decaying = levelDbfs < peakLevelDbfs - DECAY_MARGIN_DB
        if (!decaying && octaveFoldFrames >= OCTAVE_FOLD_LIMIT) {
            // Persistent: the player really has moved up an octave. Re-lock
            // outright, because a history full of folded values would otherwise
            // keep dragging the median back down.
            history.clear()
            smoothedLogHz = logHz
            octaveFoldFrames = 0
            return logHz
        }
        octaveFoldFrames++
        return logHz - octaves * 1200.0 / CENTS_PER_LOG_UNIT
    }

    private companion object {
        /** 1200 cents per octave, and an octave is ln(2) in log-frequency. */
        val CENTS_PER_LOG_UNIT = 1200.0 / ln(2.0)

        /** How near an exact octave a jump must be to be treated as an artifact. */
        const val OCTAVE_SNAP_CENTS = 40.0

        /** Consecutive folds before a persistent octave change is believed. */
        const val OCTAVE_FOLD_LIMIT = 12

        /** How far below the note's own peak counts as "decaying", in dB. */
        const val DECAY_MARGIN_DB = 12.0
    }
}
