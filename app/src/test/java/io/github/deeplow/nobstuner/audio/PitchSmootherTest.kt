package io.github.deeplow.nobstuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchSmootherTest {

    private fun good(hz: Double) = PitchEstimate(hz, clarity = 0.95, levelDbfs = -20.0)

    /** Pushes enough agreeing frames to get past the onset check. */
    private fun PitchSmoother.lockOn(hz: Double): TrackedPitch? {
        var last: TrackedPitch? = null
        repeat(3) { last = push(good(hz)) }
        return last
    }

    @Test
    fun `locks on once a few frames agree`() {
        val smoother = PitchSmoother()
        // One frame is not enough: the first frame of a note is the attack.
        assertNull(smoother.push(good(440.0)))
        assertNull(smoother.push(good(440.0)))
        val tracked = smoother.push(good(440.0))
        assertNotNull(tracked)
        assertEquals(440.0, tracked!!.frequencyHz, 1e-9)
    }

    @Test
    fun `a lone outlier at an onset never becomes the reading`() {
        // Exactly the bow-scratch case: one confident frame on a high partial,
        // then the real note arrives.
        val smoother = PitchSmoother()
        assertNull(smoother.push(good(705.6)))
        assertNull(smoother.push(good(233.1)))
        assertNull(smoother.push(good(233.1)))
        val tracked = smoother.push(good(233.1))
        assertNotNull(tracked)
        assertEquals(233.1, tracked!!.frequencyHz, 2.0)
    }

    @Test
    fun `drops frames that are far too quiet or too noisy`() {
        val smoother = PitchSmoother()
        // Below the digital-silence backstop.
        assertNull(smoother.push(PitchEstimate(440.0, clarity = 0.95, levelDbfs = -90.0)))
        // Not periodic enough to be a note.
        assertNull(smoother.push(PitchEstimate(440.0, clarity = 0.2, levelDbfs = -20.0)))
        // Nothing detected at all.
        assertNull(smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -70.0)))
    }

    @Test
    fun `a quiet note is still heard once the room is known to be quieter`() {
        // The level that matters is the one relative to the room, not an
        // absolute figure: UNPROCESSED input runs around 33 dB below MIC.
        val smoother = PitchSmoother()
        repeat(20) { smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -85.0)) }

        var tracked: TrackedPitch? = null
        repeat(4) {
            tracked = smoother.push(PitchEstimate(329.63, clarity = 1.0, levelDbfs = -59.0))
        }
        assertNotNull("a -59 dBFS note over a -85 dBFS room should register", tracked)
        assertEquals(329.63, tracked!!.frequencyHz, 0.5)
    }

    @Test
    fun `a note barely above the room is ignored`() {
        val smoother = PitchSmoother()
        // The floor is an exponential average, so give it long enough to settle
        // on the room — a couple of seconds of hearing nothing, which is what
        // happens anyway between picking the app up and playing a note.
        repeat(60) { smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -50.0)) }
        repeat(4) {
            assertNull(smoother.push(PitchEstimate(329.63, clarity = 1.0, levelDbfs = -45.0)))
        }
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

    @Test
    fun `every pluck registers, not just the first`() {
        // Reported from a real guitar: the first pluck reads, then nothing.
        // A pluck is loud at the attack and fades; the frames at the end of the
        // fade are rejected for low clarity while still being far louder than
        // the room, so a noise floor learned from rejected frames climbs after
        // every pluck until the next one can no longer clear it.
        val smoother = PitchSmoother()
        val detected = mutableListOf<Int>()

        repeat(6) { pluck ->
            // Attack and sustain: loud and clearly periodic.
            repeat(12) { frame ->
                val level = -20.0 - frame * 0.8
                val tracked = smoother.push(
                    PitchEstimate(110.0, clarity = 0.97, levelDbfs = level),
                )
                if (tracked != null) detected += pluck
            }
            // Fade: still audible, but no longer periodic enough to read.
            repeat(14) { frame ->
                smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -32.0 - frame * 1.5))
            }
            // A moment of room tone before the next pluck.
            repeat(10) { smoother.push(PitchEstimate(null, clarity = 0.0, levelDbfs = -78.0)) }
        }

        val plucksHeard = detected.distinct()
        assertEquals(
            "only plucks $plucksHeard registered out of six",
            listOf(0, 1, 2, 3, 4, 5),
            plucksHeard,
        )
    }
}
