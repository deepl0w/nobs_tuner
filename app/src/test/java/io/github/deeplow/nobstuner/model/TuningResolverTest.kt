package io.github.deeplow.nobstuner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningResolverTest {

    private val standard = TuningCatalog.findById("guitar_standard")!! // E2 A2 D3 G3 B3 E4
    private val a440 = Notes.DEFAULT_A4_HZ

    private fun resolve(
        hz: Double,
        tuning: Tuning = standard,
        chromatic: Boolean = false,
        pinned: Int? = null,
        autoDetect: Boolean = true,
        tolerance: Int = 5,
    ) = TuningResolver.resolve(
        frequencyHz = hz,
        tuning = tuning,
        chromatic = chromatic,
        pinnedStringIndex = pinned,
        autoDetectString = autoDetect,
        referencePitchHz = a440,
        toleranceCents = tolerance,
    )

    // ---- Automatic detection ---------------------------------------------

    @Test
    fun `auto detection picks the nearest string`() {
        // A2 is string index 1 in standard tuning.
        val target = resolve(Notes.frequencyOf(45))
        assertEquals(1, target.stringIndex)
        assertEquals(45, target.targetMidi)
    }

    @Test
    fun `auto detection picks the nearest string even when badly out of tune`() {
        // 30 cents flat of D3 still belongs to the D string, not the A below it.
        val flatD = Notes.frequencyOf(50 - 0.30, a440)
        val target = resolve(flatD)
        assertEquals(2, target.stringIndex)
        assertEquals(-30.0, target.cents, 0.5)
        assertFalse(target.inTune)
    }

    // ---- The autoDetectString setting ------------------------------------

    @Test
    fun `with auto detection off and nothing pinned the first string is targeted`() {
        // Playing A2, but the player said they would choose the string.
        val target = resolve(Notes.frequencyOf(45), autoDetect = false)
        assertEquals(0, target.stringIndex)
        assertEquals(40, target.targetMidi) // low E, not the A being played
    }

    @Test
    fun `with auto detection off a pinned string is still honoured`() {
        val target = resolve(Notes.frequencyOf(45), pinned = 3, autoDetect = false)
        assertEquals(3, target.stringIndex)
        assertEquals(55, target.targetMidi)
    }

    @Test
    fun `auto detection off changes the target for the same pitch`() {
        val hz = Notes.frequencyOf(45)
        assertEquals(1, resolve(hz, autoDetect = true).stringIndex)
        assertEquals(0, resolve(hz, autoDetect = false).stringIndex)
    }

    // ---- Pinning ---------------------------------------------------------

    @Test
    fun `a pinned string wins over the nearer one`() {
        // Playing A2 but pinned to the low E string: measure against E.
        val target = resolve(Notes.frequencyOf(45), pinned = 0)
        assertEquals(0, target.stringIndex)
        assertEquals(40, target.targetMidi)
        assertEquals(500.0, target.cents, 1.0) // a fourth above E2
    }

    @Test
    fun `an out of range pin falls back to detection`() {
        val target = resolve(Notes.frequencyOf(45), pinned = 99)
        assertEquals(1, target.stringIndex)
    }

    @Test
    fun `a negative pin falls back to detection`() {
        val target = resolve(Notes.frequencyOf(45), pinned = -1)
        assertEquals(1, target.stringIndex)
    }

    // ---- Chromatic mode --------------------------------------------------

    @Test
    fun `chromatic mode ignores the tuning entirely`() {
        // C#5 is in no standard guitar string.
        val target = resolve(Notes.frequencyOf(73), chromatic = true)
        assertEquals(73, target.targetMidi)
        assertNull(target.stringIndex)
    }

    @Test
    fun `chromatic mode ignores a pinned string`() {
        val target = resolve(Notes.frequencyOf(73), chromatic = true, pinned = 0)
        assertNull(target.stringIndex)
        assertEquals(73, target.targetMidi)
    }

    // ---- Tolerance -------------------------------------------------------

    @Test
    fun `in tune when inside the tolerance band`() {
        val slightlySharp = Notes.frequencyOf(40 + 0.04, a440) // +4 cents
        assertTrue(resolve(slightlySharp, tolerance = 5).inTune)
    }

    @Test
    fun `out of tune when outside the tolerance band`() {
        val sharp = Notes.frequencyOf(40 + 0.06, a440) // +6 cents
        assertFalse(resolve(sharp, tolerance = 5).inTune)
    }

    @Test
    fun `tolerance is applied to the chromatic reading too`() {
        val sharp = Notes.frequencyOf(73 + 0.08, a440)
        assertFalse(resolve(sharp, chromatic = true, tolerance = 5).inTune)
        assertTrue(resolve(sharp, chromatic = true, tolerance = 10).inTune)
    }

    // ---- Degenerate input ------------------------------------------------

    @Test
    fun `a tuning with no strings falls back to the chromatic reading`() {
        val empty = Tuning("empty", "Empty", InstrumentFamily.OTHER, emptyList())
        val target = resolve(Notes.frequencyOf(69), tuning = empty)
        assertEquals(69, target.targetMidi)
        assertNull(target.stringIndex)
    }

    // ---- The index the selector highlights -------------------------------

    @Test
    fun `selector shows nothing pinned while detection is on`() {
        assertNull(
            TuningResolver.effectivePinnedIndex(
                pinnedStringIndex = null,
                autoDetectString = true,
                chromatic = false,
                tuning = standard,
            ),
        )
    }

    @Test
    fun `selector falls back to the first string when detection is off`() {
        assertEquals(
            0,
            TuningResolver.effectivePinnedIndex(
                pinnedStringIndex = null,
                autoDetectString = false,
                chromatic = false,
                tuning = standard,
            ),
        )
    }

    @Test
    fun `selector shows the pin when there is one`() {
        assertEquals(
            4,
            TuningResolver.effectivePinnedIndex(
                pinnedStringIndex = 4,
                autoDetectString = true,
                chromatic = false,
                tuning = standard,
            ),
        )
    }

    @Test
    fun `selector shows nothing in chromatic mode`() {
        assertNull(
            TuningResolver.effectivePinnedIndex(
                pinnedStringIndex = 2,
                autoDetectString = false,
                chromatic = true,
                tuning = standard,
            ),
        )
    }

    // ---- Reference pitch -------------------------------------------------

    @Test
    fun `reference pitch moves the target with the tuning`() {
        // At A=415 the low E string sits lower, so 440-based E2 reads sharp.
        val e2At440 = Notes.frequencyOf(40, 440.0)
        val target = TuningResolver.resolve(
            frequencyHz = e2At440,
            tuning = standard,
            chromatic = false,
            pinnedStringIndex = 0,
            autoDetectString = true,
            referencePitchHz = 415.0,
            toleranceCents = 5,
        )
        assertTrue("expected sharp against a 415 Hz reference", target.cents > 90.0)
    }
}
