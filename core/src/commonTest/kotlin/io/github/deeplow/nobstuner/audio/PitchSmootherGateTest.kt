package io.github.deeplow.nobstuner.audio

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The noise gate under continuous playing.
 *
 * The floor is learned from frames that hold no pitch. On a plucked instrument
 * most of those frames are not the room: they are the pick attack and the tail
 * going aperiodic as it dies, and the attack in particular is far louder than
 * the note that follows it. Learning the room from them teaches a floor that
 * climbs above the instrument, and the tuner goes deaf while the player is
 * still playing.
 */
class PitchSmootherGateTest {

    /**
     * One pluck as the detector sees it.
     *
     * The attack is a transient — pick noise, string slapping the fret — which
     * is both louder than the note and not yet periodic. Then the note proper,
     * decaying. Then the tail, still audible but no longer periodic.
     */
    private fun pluck(
        hz: Double,
        attackDbfs: Double,
        noteDbfs: Double,
        frames: Int,
        decayPerFrameDb: Double,
    ): List<PitchEstimate> = buildList {
        repeat(2) { add(PitchEstimate(null, clarity = 0.30, levelDbfs = attackDbfs)) }
        val periodic = frames - 3
        for (i in 0 until periodic) {
            add(PitchEstimate(hz, clarity = 0.93, levelDbfs = noteDbfs - i * decayPerFrameDb))
        }
        add(PitchEstimate(null, clarity = 0.50, levelDbfs = noteDbfs - periodic * decayPerFrameDb))
    }

    private fun room(frames: Int) =
        List(frames) { PitchEstimate(null, clarity = 0.05, levelDbfs = -72.0) }

    /** Fraction of pitched frames the display actually showed. */
    private fun playThrough(
        smoother: PitchSmoother,
        plucks: Int,
        hz: (Int) -> Double,
        attackDbfs: Double,
        noteDbfs: Double,
        frames: Int = 9,
        decayPerFrameDb: Double = 2.0,
    ): Pair<Int, Int> {
        var shown = 0
        var pitched = 0
        repeat(plucks) { index ->
            pluck(hz(index), attackDbfs, noteDbfs, frames, decayPerFrameDb).forEach { estimate ->
                val tracked = smoother.push(estimate)
                if (estimate.frequencyHz != null) {
                    pitched++
                    if (tracked != null) shown++
                }
            }
        }
        return shown to pitched
    }

    @Test
    fun `a string plucked over and over stays audible to the tuner`() {
        val smoother = PitchSmoother()
        room(40).forEach { smoother.push(it) }

        val (shown, pitched) = playThrough(
            smoother, plucks = 40, hz = { 146.83 },
            attackDbfs = -6.0, noteDbfs = -26.0,
        )
        assertTrue(
            shown * 4 >= pitched * 3,
            "the tuner went deaf while the string was still being plucked: " +
                "showed $shown of $pitched pitched frames",
        )
    }

    @Test
    fun `working across the strings does not silence the tuner`() {
        val smoother = PitchSmoother()
        room(40).forEach { smoother.push(it) }

        val strings = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
        val (shown, pitched) = playThrough(
            smoother, plucks = 36, hz = { strings[it % strings.size] },
            attackDbfs = -6.0, noteDbfs = -26.0,
        )
        assertTrue(
            shown * 4 >= pitched * 3,
            "the tuner stopped responding part way through the instrument: " +
                "showed $shown of $pitched pitched frames",
        )
    }

    @Test
    fun `the last plucks are heard as well as the first`() {
        // The failure the player reports is progressive: it works, then stops.
        val smoother = PitchSmoother()
        room(40).forEach { smoother.push(it) }

        val (earlyShown, earlyPitched) = playThrough(
            smoother, plucks = 5, hz = { 146.83 }, attackDbfs = -6.0, noteDbfs = -26.0,
        )
        val (lateShown, latePitched) = playThrough(
            smoother, plucks = 30, hz = { 146.83 }, attackDbfs = -6.0, noteDbfs = -26.0,
        )
        assertTrue(earlyShown > earlyPitched / 2, "nothing was heard even at the start")
        assertTrue(
            lateShown * earlyPitched * 4 >= earlyShown * latePitched * 3,
            "the tuner faded out as playing continued: heard $earlyShown/$earlyPitched at " +
                "the start but only $lateShown/$latePitched later",
        )
    }

    @Test
    fun `a loud room is still gated out`() {
        // The floor has to keep doing its job once there genuinely is one.
        val smoother = PitchSmoother()
        repeat(80) { smoother.push(PitchEstimate(null, clarity = 0.1, levelDbfs = -40.0)) }
        assertNull(
            smoother.push(PitchEstimate(98.0, clarity = 0.8, levelDbfs = -36.0)),
            "room hiss was reported as a note",
        )
    }

    @Test
    fun `digital silence is never reported as a note`() {
        val smoother = PitchSmoother()
        repeat(40) { smoother.push(PitchEstimate(null, clarity = 0.02, levelDbfs = -120.0)) }
        assertNull(smoother.push(PitchEstimate(110.0, clarity = 0.9, levelDbfs = -100.0)))
    }
}
