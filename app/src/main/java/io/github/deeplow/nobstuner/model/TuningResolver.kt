package io.github.deeplow.nobstuner.model

import kotlin.math.abs

/** Which note the tuner is aiming at, and how far the player is from it. */
data class TuningTarget(
    val targetMidi: Int,
    /** Signed deviation from [targetMidi], in cents. Negative means flat. */
    val cents: Double,
    /** Index into [Tuning.strings], or null when no string is being targeted. */
    val stringIndex: Int?,
    val inTune: Boolean,
)

/**
 * Decides which note a detected pitch should be measured against.
 *
 * Pure: no Android, no coroutines, no stored state. This is the rule that
 * governs what the needle points at, so it lives where it can be read and
 * tested on its own rather than inside a view model.
 */
object TuningResolver {

    /**
     * @param chromatic when true the nearest chromatic note wins and strings are ignored.
     * @param pinnedStringIndex a string the player chose by hand, or null.
     * @param autoDetectString whether to pick the nearest string when none is pinned.
     */
    fun resolve(
        frequencyHz: Double,
        tuning: Tuning,
        chromatic: Boolean,
        pinnedStringIndex: Int?,
        autoDetectString: Boolean,
        referencePitchHz: Double,
        toleranceCents: Int,
    ): TuningTarget {
        // A tuning with no strings has nothing to aim at; falling back to the
        // chromatic reading keeps the display meaningful instead of crashing.
        if (chromatic || tuning.strings.isEmpty()) {
            val note = Notes.nearest(frequencyHz, referencePitchHz)
            return TuningTarget(
                targetMidi = note.midi,
                cents = note.cents,
                stringIndex = null,
                inTune = abs(note.cents) <= toleranceCents,
            )
        }

        val index = stringIndexFor(
            frequencyHz = frequencyHz,
            tuning = tuning,
            pinnedStringIndex = pinnedStringIndex,
            autoDetectString = autoDetectString,
            referencePitchHz = referencePitchHz,
        )
        val targetMidi = tuning.strings[index]
        val cents = Notes.centsBetween(frequencyHz, targetMidi, referencePitchHz)
        return TuningTarget(
            targetMidi = targetMidi,
            cents = cents,
            stringIndex = index,
            inTune = abs(cents) <= toleranceCents,
        )
    }

    /**
     * The string a reading belongs to.
     *
     * A string the player pinned always wins. Failing that, automatic detection
     * takes whichever string is fewest cents away. With automatic detection
     * switched off there is nothing left to detect with, so the first string
     * stands in until the player taps one — the setting means "I will choose",
     * and choosing nothing still has to aim somewhere.
     */
    fun stringIndexFor(
        frequencyHz: Double,
        tuning: Tuning,
        pinnedStringIndex: Int?,
        autoDetectString: Boolean,
        referencePitchHz: Double,
    ): Int {
        pinnedStringIndex?.takeIf { it in tuning.strings.indices }?.let { return it }
        if (!autoDetectString) return 0
        return tuning.strings.indices.minByOrNull { i ->
            abs(Notes.centsBetween(frequencyHz, tuning.strings[i], referencePitchHz))
        } ?: 0
    }

    /**
     * The index the UI should show as chosen, so that switching automatic
     * detection off visibly lands on a string rather than leaving the selector
     * blank while the reading quietly targets the first one.
     */
    fun effectivePinnedIndex(
        pinnedStringIndex: Int?,
        autoDetectString: Boolean,
        chromatic: Boolean,
        tuning: Tuning,
    ): Int? = when {
        chromatic || tuning.strings.isEmpty() -> null
        pinnedStringIndex != null && pinnedStringIndex in tuning.strings.indices -> pinnedStringIndex
        autoDetectString -> null
        else -> 0
    }
}
