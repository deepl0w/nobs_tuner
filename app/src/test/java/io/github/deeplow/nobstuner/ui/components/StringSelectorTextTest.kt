package io.github.deeplow.nobstuner.ui.components

import io.github.deeplow.nobstuner.model.Notes
import io.github.deeplow.nobstuner.model.PitchTargeting
import io.github.deeplow.nobstuner.model.TuningCatalog
import io.github.deeplow.nobstuner.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the string selector puts on screen and into a screen reader.
 *
 * Wrong copy here is a silent defect: it renders perfectly and tells the player
 * something untrue about what the tuner is doing. Offering to "tap again for
 * automatic detection" when automatic detection is switched off is the case
 * that prompted these.
 */
class StringSelectorTextTest {

    private val standard = TuningCatalog.findById("guitar_standard")!! // E2 A2 D3 G3 B3 E4

    // ---- The hint under the selector -------------------------------------

    @Test
    fun `with detection off the hint says to tap the string being tuned`() {
        assertEquals(
            "Tap the string you are tuning.",
            stringSelectorHint(autoDetect = false, pinnedIndex = 0),
        )
    }

    @Test
    fun `with detection off the hint never offers to hand back to detection`() {
        // There is nothing to hand back to, so the offer would be a lie. This
        // holds whatever the selector happens to be showing as chosen.
        for (pinned in listOf(null, 0, 3, 5)) {
            val hint = stringSelectorHint(autoDetect = false, pinnedIndex = pinned)
            assertFalse(
                "hint offered automatic detection while it was off (pinned=$pinned): $hint",
                hint.contains("automatic detection"),
            )
        }
    }

    @Test
    fun `with detection on and a string pinned the hint offers the way back`() {
        val hint = stringSelectorHint(autoDetect = true, pinnedIndex = 2)
        assertEquals("Listening for one string. Tap it again for automatic detection.", hint)
    }

    @Test
    fun `with detection on and nothing pinned the hint invites a tap`() {
        assertEquals(
            "Tap a string to lock onto it.",
            stringSelectorHint(autoDetect = true, pinnedIndex = null),
        )
    }

    /**
     * The hint and the chosen chip are driven by the same two facts, so they
     * have to be read from the same place. This is the state the tuner is
     * actually in when the setting is off, taken from the view model's own rule.
     */
    @Test
    fun `the hint agrees with what the selector marks as chosen`() {
        val pinned = PitchTargeting.effectivePinnedIndex(
            manualIndex = null,
            autoDetect = false,
            chromatic = false,
            tuning = standard,
        )
        assertEquals("the selector should mark the first string", 0, pinned)
        assertEquals(
            "Tap the string you are tuning.",
            stringSelectorHint(autoDetect = false, pinnedIndex = pinned),
        )
    }

    @Test
    fun `the default settings produce the invitation to tap`() {
        val defaults = UserSettings()
        val pinned = PitchTargeting.effectivePinnedIndex(
            manualIndex = null,
            autoDetect = defaults.autoDetectString,
            chromatic = false,
            tuning = standard,
        )
        assertEquals(
            "Tap a string to lock onto it.",
            stringSelectorHint(autoDetect = defaults.autoDetectString, pinnedIndex = pinned),
        )
    }

    // ---- What a screen reader announces ----------------------------------

    @Test
    fun `a chip announces its string number and note`() {
        assertEquals(
            "String 6, E2",
            stringChipDescription(
                stringNumber = 6, noteName = "E", octave = 2,
                isTuned = false, isPinned = false,
            ),
        )
    }

    @Test
    fun `a chosen chip says so`() {
        assertEquals(
            "String 6, E2, selected",
            stringChipDescription(
                stringNumber = 6, noteName = "E", octave = 2,
                isTuned = false, isPinned = true,
            ),
        )
    }

    @Test
    fun `a tuned chip says so, and says it before the selection`() {
        assertEquals(
            "String 3, G3, tuned, selected",
            stringChipDescription(
                stringNumber = 3, noteName = "G", octave = 3,
                isTuned = true, isPinned = true,
            ),
        )
        assertEquals(
            "String 3, G3, tuned",
            stringChipDescription(
                stringNumber = 3, noteName = "G", octave = 3,
                isTuned = true, isPinned = false,
            ),
        )
    }

    @Test
    fun `the announcement matches the chip the selector actually draws`() {
        // The selector counts strings the way a player does: index 0 is the
        // highest-numbered string. Announcing the index instead would send a
        // TalkBack user to the wrong string.
        standard.strings.forEachIndexed { index, midi ->
            val description = stringChipDescription(
                stringNumber = standard.stringCount - index,
                noteName = Notes.pitchClassName(midi, useFlats = false),
                octave = Notes.octaveOf(midi),
                isTuned = false,
                isPinned = false,
            )
            assertEquals("String ${6 - index}, ${Notes.name(midi)}", description)
        }
        // Concretely: the low E is string 6, not string 1.
        assertTrue(
            stringChipDescription(
                stringNumber = standard.stringCount, noteName = "E", octave = 2,
                isTuned = false, isPinned = false,
            ).startsWith("String 6, E2"),
        )
    }

    @Test
    fun `flats are announced as flats when that is the preference`() {
        assertEquals(
            "String 1, Eb4",
            stringChipDescription(
                stringNumber = 1,
                noteName = Notes.pitchClassName(63, useFlats = true),
                octave = Notes.octaveOf(63),
                isTuned = false,
                isPinned = false,
            ),
        )
    }
}
