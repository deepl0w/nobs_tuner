package io.github.deeplow.stringtune.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchSmootherTest {

    private fun good(hz: Double) = PitchEstimate(hz, clarity = 0.95, levelDbfs = -20.0)

    @Test
    fun `passes a confident reading straight through`() {
        val tracked = PitchSmoother().push(good(440.0))
        assertNotNull(tracked)
        assertEquals(440.0, tracked!!.frequencyHz, 1e-9)
    }

    @Test
    fun `drops frames that are too quiet or too noisy`() {
        val smoother = PitchSmoother()
        assertNull(smoother.push(PitchEstimate(440.0, clarity = 0.95, levelDbfs = -70.0)))
        assertNull(smoother.push(PitchEstimate(440.0, clarity = 0.2, levelDbfs = -20.0)))
        assertNull(smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -70.0)))
    }

    @Test
    fun `settles on a steady pitch`() {
        val smoother = PitchSmoother()
        repeat(30) { smoother.push(good(440.0)) }
        val tracked = smoother.push(good(440.0))
        assertEquals(440.0, tracked!!.frequencyHz, 0.01)
    }

    @Test
    fun `a single rogue frame does not move the reading far`() {
        val smoother = PitchSmoother()
        repeat(10) { smoother.push(good(220.0)) }
        // One frame flips to the octave, as YIN occasionally does.
        val tracked = smoother.push(good(440.0))
        assertNotNull(tracked)
        assertTrue(
            "outlier pulled the reading to ${tracked!!.frequencyHz}",
            abs(tracked.frequencyHz - 220.0) < 5.0,
        )
    }

    @Test
    fun `follows a deliberate retune`() {
        val smoother = PitchSmoother()
        repeat(10) { smoother.push(good(220.0)) }
        repeat(10) { smoother.push(good(246.94)) }
        val tracked = smoother.push(good(246.94))
        assertEquals(246.94, tracked!!.frequencyHz, 1.0)
    }

    @Test
    fun `holds briefly through a decaying note then releases`() {
        val smoother = PitchSmoother()
        repeat(10) { smoother.push(good(330.0)) }

        val silent = PitchEstimate(null, clarity = 0.0, levelDbfs = -80.0)
        // A couple of dead frames should not blank the display mid-adjustment.
        assertNotNull(smoother.push(silent))
        assertNotNull(smoother.push(silent))

        repeat(20) { smoother.push(silent) }
        assertNull(smoother.push(silent))
    }

    @Test
    fun `reset clears the held reading`() {
        val smoother = PitchSmoother()
        repeat(5) { smoother.push(good(330.0)) }
        smoother.reset()
        assertNull(smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -80.0)))
    }
}
