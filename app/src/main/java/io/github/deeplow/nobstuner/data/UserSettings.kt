package io.github.deeplow.nobstuner.data

import io.github.deeplow.nobstuner.model.Notes

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How the deviation from the target note is drawn.
 *
 * They show the same number in four idioms, and which one reads fastest is a
 * matter of what you learned to tune on.
 */
enum class DisplayStyle(val displayName: String, val description: String) {
    NEEDLE("Needle", "A swinging pointer, like a clip-on tuner."),
    BAR("Bar", "A sliding marker on a straight scale."),
    STROBE("Strobe", "Stripes that stand still when the note is in tune."),
    DIGITAL("Digital", "The cents figure, with a row of lights."),
}

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
    val displayStyle: DisplayStyle = DisplayStyle.NEEDLE,
) {
    companion object {
        val REFERENCE_PITCH_RANGE = 415.0..466.0
        val TOLERANCE_RANGE = 1..15
    }
}
