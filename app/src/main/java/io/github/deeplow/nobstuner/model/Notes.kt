package io.github.deeplow.nobstuner.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Equal-temperament note math.
 *
 * Notes are identified by MIDI number, where 69 == A4. All conversions take the
 * reference pitch of A4 as a parameter so the user can tune to something other
 * than 440 Hz (baroque ensembles at 415 Hz, orchestras at 442/443 Hz, ...).
 */
object Notes {

    const val A4_MIDI = 69
    const val DEFAULT_A4_HZ = 440.0

    /** Lowest / highest MIDI numbers the app will ever display or let you pick. */
    const val MIN_MIDI = 12 // C0, ~16.35 Hz
    const val MAX_MIDI = 108 // C8, ~4186 Hz

    private val SHARP_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )
    private val FLAT_NAMES = arrayOf(
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
    )

    private val LN2 = ln(2.0)

    /** Frequency in Hz of a MIDI note. */
    fun frequencyOf(midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double =
        frequencyOf(midi.toDouble(), a4Hz)

    /** Frequency of a fractional MIDI position, so cents offsets can be rendered. */
    fun frequencyOf(midi: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        a4Hz * 2.0.pow((midi - A4_MIDI) / 12.0)

    /** Continuous MIDI position of a frequency. Returns a fractional value. */
    fun midiOf(frequencyHz: Double, a4Hz: Double = DEFAULT_A4_HZ): Double =
        A4_MIDI + 12.0 * (ln(frequencyHz / a4Hz) / LN2)

    /** Signed cents between [frequencyHz] and the exact pitch of [midi]. */
    fun centsBetween(frequencyHz: Double, midi: Int, a4Hz: Double = DEFAULT_A4_HZ): Double =
        1200.0 * (ln(frequencyHz / frequencyOf(midi, a4Hz)) / LN2)

    /** The chromatic note nearest to [frequencyHz], plus how far off it is. */
    fun nearest(frequencyHz: Double, a4Hz: Double = DEFAULT_A4_HZ): NoteReading {
        val continuous = midiOf(frequencyHz, a4Hz)
        val midi = continuous.roundToInt()
        return NoteReading(
            midi = midi,
            frequencyHz = frequencyHz,
            cents = (continuous - midi) * 100.0,
        )
    }

    /** Pitch-class name, e.g. "F#" or "Gb" depending on [useFlats]. */
    fun pitchClassName(midi: Int, useFlats: Boolean = false): String {
        val names = if (useFlats) FLAT_NAMES else SHARP_NAMES
        return names[Math.floorMod(midi, 12)]
    }

    /** Scientific pitch octave; MIDI 60 is C4. */
    fun octaveOf(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** Full scientific name, e.g. "A4" or "Eb3". */
    fun name(midi: Int, useFlats: Boolean = false): String =
        pitchClassName(midi, useFlats) + octaveOf(midi)

    /** Parses "A4", "F#2", "Eb3" back into a MIDI number, or null if unparseable. */
    fun parse(name: String): Int? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val letter = trimmed[0].uppercaseChar()
        val base = when (letter) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
            else -> return null
        }
        var index = 1
        var accidental = 0
        while (index < trimmed.length) {
            when (trimmed[index]) {
                '#', '♯' -> accidental++
                'b', '♭' -> accidental--
                else -> break
            }
            index++
        }
        val octave = trimmed.substring(index).toIntOrNull() ?: return null
        return (octave + 1) * 12 + base + accidental
    }
}

/** A single pitch observation resolved against the chromatic scale. */
data class NoteReading(
    val midi: Int,
    val frequencyHz: Double,
    /** Signed deviation from the exact note, in cents. Negative means flat. */
    val cents: Double,
)
