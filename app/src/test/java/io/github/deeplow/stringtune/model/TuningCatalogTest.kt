package io.github.deeplow.stringtune.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningCatalogTest {

    @Test
    fun `ids are unique`() {
        val ids = TuningCatalog.presets.map { it.id }
        assertEquals(
            "duplicate preset ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}",
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `every preset is playable`() {
        TuningCatalog.presets.forEach { tuning ->
            assertTrue("${tuning.id} has no strings", tuning.strings.isNotEmpty())
            assertTrue("${tuning.id} has too many strings", tuning.strings.size <= 12)
            assertTrue("${tuning.id} has a blank name", tuning.name.isNotBlank())
            tuning.strings.forEach { midi ->
                assertTrue(
                    "${tuning.id} has out-of-range note $midi",
                    midi in Notes.MIN_MIDI..Notes.MAX_MIDI,
                )
            }
        }
    }

    @Test
    fun `presets are not marked custom`() {
        assertTrue(TuningCatalog.presets.none { it.isCustom })
    }

    @Test
    fun `lookup finds presets and the default is a preset`() {
        assertNotNull(TuningCatalog.findById("guitar_standard"))
        assertEquals(null, TuningCatalog.findById("nope"))
        assertTrue(TuningCatalog.default in TuningCatalog.presets)
    }

    @Test
    fun `spot check the tunings musicians will notice`() {
        fun notes(id: String) = TuningCatalog.findById(id)!!.detailedSummary()

        assertEquals("E2 A2 D3 G3 B3 E4", notes("guitar_standard"))
        assertEquals("D2 A2 D3 G3 B3 E4", notes("guitar_drop_d"))
        assertEquals("D2 A2 D3 G3 A3 D4", notes("guitar_dadgad"))
        assertEquals("D2 G2 D3 G3 B3 D4", notes("guitar_open_g"))
        assertEquals("E1 A1 D2 G2", notes("bass_standard"))
        assertEquals("B0 E1 A1 D2 G2", notes("bass_5_standard"))
        // Standard ukulele is re-entrant: the 4th string sits above the 3rd.
        assertEquals("G4 C4 E4 A4", notes("uke_standard"))
        assertEquals("G3 C4 E4 A4", notes("uke_low_g"))
        assertEquals("G3 D4 A4 E5", notes("violin_standard"))
        assertEquals("C3 G3 D4 A4", notes("viola_standard"))
        assertEquals("C2 G2 D3 A3", notes("cello_standard"))
        assertEquals("G4 D3 G3 B3 D4", notes("banjo_open_g"))
        assertEquals("B1 E2 A2 D3 G3 B3 E4", notes("guitar_7_standard"))
    }

    @Test
    fun `summary drops octaves and follows the accidental preference`() {
        val dropC = TuningCatalog.findById("guitar_drop_c")!!
        assertEquals("C G C F A D", dropC.summary())

        val halfStep = TuningCatalog.findById("guitar_half_step_down")!!
        assertEquals("D# G# C# F# A# D#", halfStep.summary())
        assertEquals("Eb Ab Db Gb Bb Eb", halfStep.summary(useFlats = true))
    }

    @Test
    fun `every family has at least one preset`() {
        InstrumentFamily.entries.forEach { family ->
            assertTrue("$family has no presets", TuningCatalog.byFamily(family).isNotEmpty())
        }
    }

    @Test
    fun `family seed strings are valid`() {
        InstrumentFamily.entries.forEach { family ->
            assertEquals(family.defaultStringCount, family.seedStrings.size)
            family.seedStrings.forEach {
                assertTrue(it in Notes.MIN_MIDI..Notes.MAX_MIDI)
            }
        }
    }
}
