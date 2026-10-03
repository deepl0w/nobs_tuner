package io.github.deeplow.nobstuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the tuner aims at, which is the decision the whole screen hangs off:
 * the note name, the needle and the "in tune" tick all come from it.
 */
class PitchTargetingTest {

    private val standard = TuningCatalog.findById("guitar_standard")!!
    private fun hz(midi: Int) = Notes.frequencyOf(midi)

    // ---- Automatic string detection --------------------------------------

    @Test
    fun `each open string of a standard guitar picks its own index`() {
        standard.strings.forEachIndexed { index, midi ->
            assertEquals(
                "playing ${Notes.name(midi)} aimed at the wrong string",
                index,
                PitchTargeting.stringIndexFor(hz(midi), standard),
            )
        }
    }

    @Test
    fun `a string that is badly out still picks its own target`() {
        // A low E a third of a semitone flat is still nearest to string 6.
        val flatLowE = Notes.frequencyOf(40 - 0.33)
        assertEquals(0, PitchTargeting.stringIndexFor(flatLowE, standard))
    }

    @Test
    fun `a pitch between two strings picks the nearer one`() {
        // Halfway between A2 (45) and D3 (50) in cents, nudged towards D.
        val betweenTowardsD = Notes.frequencyOf(47.8)
        assertEquals(2, PitchTargeting.stringIndexFor(betweenTowardsD, standard))
    }

    @Test
    fun `the reference pitch moves the targets together`() {
        // At A4 = 415 the open A string is 427.5 cents... in absolute Hz terms,
        // what used to be A2 now reads closest to a different target unless the
        // reference is honoured.
        val a2At415 = Notes.frequencyOf(45, a4Hz = 415.0)
        assertEquals(1, PitchTargeting.stringIndexFor(a2At415, standard, a4Hz = 415.0))
    }

    // ---- Pinning ---------------------------------------------------------

    @Test
    fun `a pinned string wins over whatever is being played`() {
        // Playing the high E but pinned to the low E string.
        assertEquals(0, PitchTargeting.stringIndexFor(hz(64), standard, manualIndex = 0))
    }

    @Test
    fun `a pin outside the tuning is ignored`() {
        // A six-string pin left over from a seven-string tuning must not crash
        // or index past the end.
        assertEquals(5, PitchTargeting.stringIndexFor(hz(64), standard, manualIndex = 9))
        assertEquals(5, PitchTargeting.stringIndexFor(hz(64), standard, manualIndex = -1))
    }

    // ---- The "detect string automatically" setting ------------------------

    @Test
    fun `turning automatic detection off stops the target chasing the note`() {
        // The setting promises "select one by hand". With it off and nothing
        // pinned, playing the high E must not silently retarget string 1.
        assertEquals(
            0,
            PitchTargeting.stringIndexFor(hz(64), standard, autoDetect = false),
        )
    }

    @Test
    fun `with automatic detection off the pinned string is still honoured`() {
        assertEquals(
            3,
            PitchTargeting.stringIndexFor(hz(64), standard, manualIndex = 3, autoDetect = false),
        )
    }

    @Test
    fun `with automatic detection on the target follows the note`() {
        assertEquals(
            5,
            PitchTargeting.stringIndexFor(hz(64), standard, autoDetect = true),
        )
    }

    // ---- Full readings ---------------------------------------------------

    @Test
    fun `an exactly tuned string reads zero cents and in tune`() {
        val reading = PitchTargeting.resolveAgainstTuning(
            frequencyHz = hz(45), clarity = 0.9, levelDbfs = -20.0,
            tuning = standard, a4Hz = 440.0, toleranceCents = 5,
            manualIndex = null, autoDetect = true,
        )
        assertEquals(45, reading.targetMidi)
        assertEquals(1, reading.stringIndex)
        assertEquals(0.0, reading.cents, 1e-6)
        assertTrue(reading.inTune)
    }

    @Test
    fun `the tolerance band is inclusive at its edge and excludes beyond it`() {
        fun centsOff(cents: Double, tolerance: Int) = PitchTargeting.resolveAgainstTuning(
            frequencyHz = Notes.frequencyOf(45 + cents / 100.0), clarity = 0.9, levelDbfs = -20.0,
            tuning = standard, a4Hz = 440.0, toleranceCents = tolerance,
            manualIndex = 1, autoDetect = true,
        ).inTune

        assertTrue("4 cents off should be in tune at a tolerance of 5", centsOff(4.0, 5))
        assertTrue("a flat note is judged the same as a sharp one", centsOff(-4.0, 5))
        assertFalse("6 cents off is not in tune at a tolerance of 5", centsOff(6.0, 5))
        assertTrue("6 cents off is in tune at a tolerance of 10", centsOff(6.0, 10))
    }

    @Test
    fun `a sharp string reads positive cents and a flat one negative`() {
        fun cents(delta: Double) = PitchTargeting.resolveAgainstTuning(
            frequencyHz = Notes.frequencyOf(45 + delta / 100.0), clarity = 0.9, levelDbfs = -20.0,
            tuning = standard, a4Hz = 440.0, toleranceCents = 5,
            manualIndex = 1, autoDetect = true,
        ).cents

        assertEquals(12.0, cents(12.0), 1e-6)
        assertEquals(-12.0, cents(-12.0), 1e-6)
    }

    // ---- Chromatic mode --------------------------------------------------

    @Test
    fun `chromatic mode names the nearest note and reports no string`() {
        val reading = PitchTargeting.resolveChromatic(
            frequencyHz = 440.0, clarity = 0.9, levelDbfs = -20.0,
            a4Hz = 440.0, toleranceCents = 5,
        )
        assertEquals(69, reading.targetMidi)
        assertEquals(0.0, reading.cents, 1e-9)
        assertNull("chromatic mode has no string to highlight", reading.stringIndex)
        assertTrue(reading.inTune)
    }

    @Test
    fun `chromatic mode follows the reference pitch`() {
        val reading = PitchTargeting.resolveChromatic(
            frequencyHz = 415.0, clarity = 0.9, levelDbfs = -20.0,
            a4Hz = 415.0, toleranceCents = 5,
        )
        assertEquals("A4 at a 415 Hz reference should read as A4", 69, reading.targetMidi)
        assertEquals(0.0, reading.cents, 1e-9)
    }

    // ---- Every shipped tuning --------------------------------------------

    @Test
    fun `every preset resolves each of its strings to a valid index`() {
        for (tuning in TuningCatalog.presets) {
            tuning.strings.forEachIndexed { index, midi ->
                val resolved = PitchTargeting.stringIndexFor(hz(midi), tuning)
                assertTrue(
                    "${tuning.id} string $index resolved out of range",
                    resolved in tuning.strings.indices,
                )
                assertEquals(
                    "${tuning.id} aimed at the wrong pitch for string $index",
                    midi,
                    tuning.strings[resolved],
                )
            }
        }
    }
}
