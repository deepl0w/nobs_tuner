package io.github.deeplow.nobstuner.tuner

import io.github.deeplow.nobstuner.audio.TrackedPitch
import io.github.deeplow.nobstuner.model.Notes
import io.github.deeplow.nobstuner.model.Tuning
import kotlin.math.abs

/** The live pitch resolved against whatever the tuner is currently aiming at. */
data class TuningReading(
    val frequencyHz: Double,
    val targetMidi: Int,
    val cents: Double,
    val clarity: Double,
    val levelDbfs: Double,
    /** Index into [Tuning.strings], or null in chromatic mode. */
    val stringIndex: Int?,
    val inTune: Boolean,
)

/**
 * Decides what a tracked pitch means: which string the player is aiming at, how
 * far off it is, and whether that counts as in tune.
 *
 * Shared rather than left in each platform's view model because "which string
 * is this?" is a judgement about the instrument, not about the UI, and two
 * implementations of it would be two tuners.
 */
object TuningResolver {

    /** Resolves against the nearest chromatic note, ignoring any tuning. */
    fun chromatic(
        pitch: TrackedPitch,
        referencePitchHz: Double,
        toleranceCents: Int,
    ): TuningReading {
        val note = Notes.nearest(pitch.frequencyHz, referencePitchHz)
        return TuningReading(
            frequencyHz = pitch.frequencyHz,
            targetMidi = note.midi,
            cents = note.cents,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            stringIndex = null,
            inTune = abs(note.cents) <= toleranceCents,
        )
    }

    /**
     * Resolves against [tuning]. Pass [manualStringIndex] to pin the tuner to
     * one string; leave it null and the string whose target is fewest cents
     * away from what is being played wins.
     */
    fun against(
        pitch: TrackedPitch,
        tuning: Tuning,
        referencePitchHz: Double,
        toleranceCents: Int,
        manualStringIndex: Int? = null,
    ): TuningReading {
        val index = manualStringIndex?.takeIf { it in tuning.strings.indices }
            ?: nearestString(pitch.frequencyHz, tuning, referencePitchHz)

        val targetMidi = tuning.strings[index]
        val cents = Notes.centsBetween(pitch.frequencyHz, targetMidi, referencePitchHz)
        return TuningReading(
            frequencyHz = pitch.frequencyHz,
            targetMidi = targetMidi,
            cents = cents,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            stringIndex = index,
            inTune = abs(cents) <= toleranceCents,
        )
    }

    /** Index of the string of [tuning] closest in cents to [frequencyHz]. */
    fun nearestString(frequencyHz: Double, tuning: Tuning, referencePitchHz: Double): Int =
        tuning.strings.indices.minByOrNull { i ->
            abs(Notes.centsBetween(frequencyHz, tuning.strings[i], referencePitchHz))
        } ?: 0
}
