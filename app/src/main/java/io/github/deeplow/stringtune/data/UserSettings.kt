package io.github.deeplow.stringtune.data

import io.github.deeplow.stringtune.model.Notes

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Everything the user can change in Settings. */
data class UserSettings(
    val referencePitchHz: Double = Notes.DEFAULT_A4_HZ,
    /** Spell accidentals as flats (Eb) rather than sharps (D#). */
    val useFlats: Boolean = false,
    /** Let the app guess which string is being played instead of tuning one at a time. */
    val autoDetectString: Boolean = true,
    val keepScreenOn: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Half-width of the "in tune" band, in cents. */
    val toleranceCents: Int = 5,
) {
    companion object {
        val REFERENCE_PITCH_RANGE = 415.0..466.0
        val TOLERANCE_RANGE = 1..15
    }
}
