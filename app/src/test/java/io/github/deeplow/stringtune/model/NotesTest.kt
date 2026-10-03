package io.github.deeplow.stringtune.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NotesTest {

    @Test
    fun `A4 is the reference pitch`() {
        assertEquals(440.0, Notes.frequencyOf(69), 1e-9)
        assertEquals(432.0, Notes.frequencyOf(69, a4Hz = 432.0), 1e-9)
    }

    @Test
    fun `well known string frequencies`() {
        // Guitar low E, A, and high E; cello C.
        assertEquals(82.41, Notes.frequencyOf(40), 0.01)
        assertEquals(110.0, Notes.frequencyOf(45), 0.01)
        assertEquals(329.63, Notes.frequencyOf(64), 0.01)
        assertEquals(65.41, Notes.frequencyOf(36), 0.01)
    }

    @Test
    fun `an octave is a doubling`() {
        for (midi in Notes.MIN_MIDI..(Notes.MAX_MIDI - 12)) {
            assertEquals(
                Notes.frequencyOf(midi) * 2.0,
                Notes.frequencyOf(midi + 12),
                1e-9 * Notes.frequencyOf(midi + 12),
            )
        }
    }

    @Test
    fun `midi and frequency round trip`() {
        for (midi in Notes.MIN_MIDI..Notes.MAX_MIDI) {
            val back = Notes.midiOf(Notes.frequencyOf(midi))
            assertEquals(midi.toDouble(), back, 1e-9)
        }
    }

    @Test
    fun `cents measure distance from a target`() {
        val a4 = Notes.frequencyOf(69)
        assertEquals(0.0, Notes.centsBetween(a4, 69), 1e-9)
        // A semitone up from A4 is exactly 100 cents sharp of A4.
        assertEquals(100.0, Notes.centsBetween(Notes.frequencyOf(70), 69), 1e-9)
        assertEquals(-100.0, Notes.centsBetween(Notes.frequencyOf(68), 69), 1e-9)
    }

    @Test
    fun `nearest snaps to the closest chromatic note`() {
        val slightlySharp = Notes.frequencyOf(64) * 1.01
        val reading = Notes.nearest(slightlySharp)
        assertEquals(64, reading.midi)
        assertTrue("expected a sharp reading, got ${reading.cents}", reading.cents > 0)

        // Exactly between two notes the reading must still land on one of them.
        val between = Notes.frequencyOf(64.5)
        assertTrue(Notes.nearest(between).midi in 64..65)
        assertTrue(abs(Notes.nearest(between).cents) <= 50.0 + 1e-9)
    }

    @Test
    fun `names use scientific pitch notation`() {
        assertEquals("A4", Notes.name(69))
        assertEquals("C4", Notes.name(60))
        assertEquals("E2", Notes.name(40))
        assertEquals("B0", Notes.name(23))
        assertEquals("C#3", Notes.name(49))
        assertEquals("Db3", Notes.name(49, useFlats = true))
    }

    @Test
    fun `parsing accepts what naming produces`() {
        for (midi in Notes.MIN_MIDI..Notes.MAX_MIDI) {
            assertEquals(midi, Notes.parse(Notes.name(midi)))
            assertEquals(midi, Notes.parse(Notes.name(midi, useFlats = true)))
        }
    }

    @Test
    fun `parsing handles unicode accidentals and rejects junk`() {
        assertEquals(49, Notes.parse("C♯3"))
        assertEquals(49, Notes.parse("D♭3"))
        assertEquals(69, Notes.parse(" a4 "))
        assertNull(Notes.parse(""))
        assertNull(Notes.parse("H3"))
        assertNull(Notes.parse("C"))
        assertNull(Notes.parse("Cx3"))
    }

    @Test
    fun `reference pitch shifts every note together`() {
        val a442 = 442.0
        // Raising the reference by ~7.85 cents raises every note by the same amount.
        val expectedCents = 1200.0 * (Math.log(a442 / 440.0) / Math.log(2.0))
        for (midi in intArrayOf(28, 40, 60, 76)) {
            val shifted = Notes.frequencyOf(midi, a442)
            assertEquals(expectedCents, Notes.centsBetween(shifted, midi), 1e-9)
        }
    }
}
