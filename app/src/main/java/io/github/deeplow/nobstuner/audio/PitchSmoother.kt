package io.github.deeplow.nobstuner.audio

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
    /**
     * A backstop against digital silence, not a judgement about loudness.
     *
     * How loud a note arrives depends entirely on the audio source: the same
     * tone measures -26 dBFS through MIC and -59 dBFS through UNPROCESSED on
     * the same phone, because UNPROCESSED bypasses the input gain. An absolute
     * threshold tuned on one source silently deafens the app on another, so
     * this sits below anything audible and the work of telling a note from the
     * room is done by [snrMarginDb] against a learned noise floor.
     */
    private val minLevelDbfs: Double = -75.0,
    /** How far above the ambient noise a frame must sit to count as a note. */
    private val snrMarginDb: Double = 12.0,
    /** Frames of silence tolerated before the display is cleared. */
    private val releaseFrames: Int = 10,
) {
    private val history = ArrayDeque<Double>(historySize)
    private var smoothedLogHz: Double? = null
    private var silentFrames = 0
    private var lastClarity = 0.0
    private var octaveFoldFrames = 0
    private var peakLevelDbfs = -120.0

    /**
     * Levels of recent frames that held no discernible pitch. The quietest of
     * them stands in for the room.
     */
    private val levelWindow = ArrayDeque<Double>()

    fun reset() {
        history.clear()
        smoothedLogHz = null
        silentFrames = 0
        lastClarity = 0.0
        octaveFoldFrames = 0
        peakLevelDbfs = -120.0
        // The room does not change because the tuner stopped listening, so the
        // learned floor deliberately survives a reset.
    }

    fun push(estimate: PitchEstimate): TrackedPitch? {
        rememberLevel(estimate)
        val frequency = estimate.frequencyHz
        val usable = frequency != null &&
            frequency > 0.0 &&
            estimate.clarity >= minClarity &&
            estimate.levelDbfs >= minLevelDbfs &&
            estimate.levelDbfs > noiseFloor() + snrMarginDb

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
            // Do not lock on the strength of a single frame. The first frame of
            // a bow stroke or a pluck catches the attack — rosin scratch, pick
            // noise, the string slapping a fret — and the detector will happily
            // report a partial from it with full confidence. Waiting for a few
            // frames to agree costs about a tenth of a second, which nobody
            // notices, and avoids showing a note that was never played.
            if (history.size < ONSET_FRAMES) return null
            val recent = history.toList().takeLast(ONSET_FRAMES)
            val spreadCents = (recent.max() - recent.min()) * CENTS_PER_LOG_UNIT
            if (spreadCents > ONSET_AGREEMENT_CENTS) return null

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
     * Only aperiodic frames teach the floor. A note held steady — a bowed
     * string, a sustaining pickup — sits at a near-constant level for seconds,
     * and a floor that watched every frame would decide that level *was* the
     * room and stop hearing the note. Periodicity is what separates the two,
     * and unlike level it does not depend on the gain of the input.
     */
    private fun rememberLevel(estimate: PitchEstimate) {
        if (estimate.frequencyHz != null && estimate.clarity >= minClarity) return
        levelWindow.addLast(estimate.levelDbfs)
        if (levelWindow.size > LEVEL_WINDOW) levelWindow.removeFirst()
    }

    /**
     * What the room sounds like, taken as a low percentile of recent aperiodic
     * frames.
     *
     * An average over these frames sounds reasonable and is not: the tail of a
     * plucked string is still far louder than the room and turns aperiodic only
     * as it dies, so every pluck would teach the average a louder "room" than
     * the last. After a handful of plucks the floor climbs past the next note
     * and the tuner goes deaf — which is exactly what repeated plucking on a
     * guitar made it do. A minimum cannot climb that way: one quiet moment
     * anywhere in the window pins it down.
     *
     * Not the outright minimum either: one unusually quiet frame would drop the
     * floor far enough for the hiss around it to look like a note, which is how
     * a silent stretch of a recording came to read as a G#1. A quarter-way
     * percentile keeps the robustness against loud tails without being hostage
     * to a single quiet frame.
     *
     * Returns minus infinity until enough such frames have been seen, so
     * opening the app and playing straight away is not read as a loud room.
     */
    private fun noiseFloor(): Double {
        if (levelWindow.size < MIN_SAMPLES_FOR_FLOOR) return Double.NEGATIVE_INFINITY
        val sorted = levelWindow.sorted()
        return sorted[(sorted.size * FLOOR_PERCENTILE).toInt().coerceIn(0, sorted.lastIndex)]
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

        /** Frames that must agree before a new note is believed. */
        const val ONSET_FRAMES = 3

        /** How far apart those frames may be and still count as the same note. */
        const val ONSET_AGREEMENT_CENTS = 60.0

        /** Frames of level history kept for the floor: about five seconds. */
        const val LEVEL_WINDOW = 110

        /** Below this many frames the floor is not trusted yet. */
        const val MIN_SAMPLES_FOR_FLOOR = 20

        /** Where in the sorted levels the room is taken to sit. */
        const val FLOOR_PERCENTILE = 0.25
    }
}
