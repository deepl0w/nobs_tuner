package io.github.deeplow.nobstuner.model

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
 * Decides what a detected frequency is being judged against: the nearest
 * chromatic note, or one string of the selected tuning.
 *
 * Pure note math, kept out of the view model so it can be exercised without an
 * Android runtime.
 */
object PitchTargeting {

    /**
     * Index into [Tuning.strings] that [frequencyHz] should be measured against.
     *
     * A string the user pinned always wins. Otherwise [autoDetect] decides
     * whether we chase whichever string is closest or stay put on the first one
     * and let the user move by hand.
     */
    fun stringIndexFor(
        frequencyHz: Double,
        tuning: Tuning,
        a4Hz: Double = Notes.DEFAULT_A4_HZ,
        manualIndex: Int? = null,
        autoDetect: Boolean = true,
    ): Int {
        manualIndex?.takeIf { it in tuning.strings.indices }?.let { return it }
        if (!autoDetect) return 0
        return tuning.strings.indices.minByOrNull { index ->
            abs(Notes.centsBetween(frequencyHz, tuning.strings[index], a4Hz))
        } ?: 0
    }

    /** Resolves a reading in chromatic mode: nearest note of the scale. */
    fun resolveChromatic(
        frequencyHz: Double,
        clarity: Double,
        levelDbfs: Double,
        a4Hz: Double,
        toleranceCents: Int,
    ): TuningReading {
        val note = Notes.nearest(frequencyHz, a4Hz)
        return TuningReading(
            frequencyHz = frequencyHz,
            targetMidi = note.midi,
            cents = note.cents,
            clarity = clarity,
            levelDbfs = levelDbfs,
            stringIndex = null,
            inTune = abs(note.cents) <= toleranceCents,
        )
    }

    /**
     * The index the string selector should show as chosen.
     *
     * With automatic detection off and nothing pinned the reading targets the
     * first string, so the selector has to say so — otherwise switching the
     * setting off leaves the selector blank while the tuner quietly aims at a
     * string the player never picked.
     */
    fun effectivePinnedIndex(
        manualIndex: Int?,
        autoDetect: Boolean,
        chromatic: Boolean,
        tuning: Tuning,
    ): Int? = when {
        chromatic || tuning.strings.isEmpty() -> null
        manualIndex != null && manualIndex in tuning.strings.indices -> manualIndex
        autoDetect -> null
        else -> 0
    }

    /** Resolves a reading against one string of [tuning]. */
    fun resolveAgainstTuning(
        frequencyHz: Double,
        clarity: Double,
        levelDbfs: Double,
        tuning: Tuning,
        a4Hz: Double,
        toleranceCents: Int,
        manualIndex: Int?,
        autoDetect: Boolean,
    ): TuningReading {
        // A tuning with no strings has nothing to aim at; falling back to the
        // chromatic reading keeps the display meaningful instead of indexing an
        // empty list.
        if (tuning.strings.isEmpty()) {
            return resolveChromatic(frequencyHz, clarity, levelDbfs, a4Hz, toleranceCents)
        }
        val index = stringIndexFor(frequencyHz, tuning, a4Hz, manualIndex, autoDetect)
        val targetMidi = tuning.strings[index]
        val cents = Notes.centsBetween(frequencyHz, targetMidi, a4Hz)
        return TuningReading(
            frequencyHz = frequencyHz,
            targetMidi = targetMidi,
            cents = cents,
            clarity = clarity,
            levelDbfs = levelDbfs,
            stringIndex = index,
            inTune = abs(cents) <= toleranceCents,
        )
    }
}
