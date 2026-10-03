package io.github.deeplow.nobstuner.model

import kotlinx.serialization.Serializable

/**
 * Instrument families, used to group presets and to pick sensible defaults when
 * the user starts a new custom tuning.
 */
@Serializable
enum class InstrumentFamily(
    val displayName: String,
    val defaultStringCount: Int,
    /** A reasonable starting point when creating a custom tuning for this family. */
    val seedStrings: List<Int>,
) {
    GUITAR("Guitar", 6, listOf(40, 45, 50, 55, 59, 64)),
    BASS("Bass", 4, listOf(28, 33, 38, 43)),
    UKULELE("Ukulele", 4, listOf(67, 60, 64, 69)),
    BANJO("Banjo", 5, listOf(67, 50, 55, 59, 62)),
    MANDOLIN("Mandolin", 4, listOf(55, 62, 69, 76)),
    ORCHESTRAL("Orchestral strings", 4, listOf(55, 62, 69, 76)),
    OTHER("Other", 6, listOf(40, 45, 50, 55, 59, 64)),
}

/**
 * A tuning: an ordered list of target pitches, lowest-numbered string first as
 * the player counts them.
 *
 * [strings] holds MIDI note numbers rather than frequencies so that changing the
 * reference pitch (A4) re-targets every tuning for free.
 */
@Serializable
data class Tuning(
    val id: String,
    val name: String,
    val family: InstrumentFamily,
    val strings: List<Int>,
    /** True for user-created tunings, false for the built-in catalog. */
    val isCustom: Boolean = false,
) {
    val stringCount: Int get() = strings.size

    /** Human-readable spelling of the strings, e.g. "E A D G B E". */
    fun summary(useFlats: Boolean = false): String =
        strings.joinToString(" ") { Notes.pitchClassName(it, useFlats) }

    /** Spelling including octaves, e.g. "E2 A2 D3 G3 B3 E4". */
    fun detailedSummary(useFlats: Boolean = false): String =
        strings.joinToString(" ") { Notes.name(it, useFlats) }
}
