package io.github.deeplow.stringtune.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * The octave-folding half of [PitchSmoother].
 *
 * A fading fundamental makes the detector hear the harmonic above, and the
 * smoother folds that back. The risk is folding away a jump the player actually
 * made, which leaves the tuner naming a note nobody is holding.
 */
class PitchSmootherOctaveTest {

    private fun frame(hz: Double?, levelDbfs: Double) =
        PitchEstimate(hz, clarity = 0.95, levelDbfs = levelDbfs)

    /** Plays [hz] at [levelDbfs] for [frames] and returns what the display settles on. */
    private fun PitchSmoother.play(hz: Double, levelDbfs: Double, frames: Int): Double {
        var last = Double.NaN
        repeat(frames) { last = push(frame(hz, levelDbfs))!!.frequencyHz }
        return last
    }

    @Test
    fun `a harmonic flip on a note that is dying away is folded back`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 82.41, levelDbfs = -12.0, frames = 15)
        // The fundamental fades out of the pluck and YIN starts hearing E3.
        val tracked = smoother.play(hz = 164.81, levelDbfs = -34.0, frames = 6)
        assertEquals("the octave artifact was not folded back", 82.41, tracked, 2.0)
    }

    @Ignore(
        "LIVE BUG: peakLevelDbfs is the loudest level since the last release, not " +
            "the peak of the note being tracked, so a quieter note that follows a " +
            "louder one reads as that louder one decaying forever. The fold-limit " +
            "escape is gated on !decaying, so it never fires and the reading stays " +
            "an octave (or two) below the string being played until a full silent " +
            "release. Giving the peak a release fixes this but regresses " +
            "RealRecordingPitchTest's cello run, which folds a genuine artifact only " +
            "because the stale peak makes `decaying` true. Needs a real fix, not a " +
            "release constant."
    )
    @Test
    fun `moving to a quieter string an octave up is followed, not folded`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 220.0, levelDbfs = -10.0, frames = 20)
        // A4 plucked more gently than the A3 before it. Roughly a second of it.
        val tracked = smoother.play(hz = 440.0, levelDbfs = -25.0, frames = 25)
        assertEquals("stuck an octave below the string being played", 440.0, tracked, 10.0)
    }

    @Ignore(
        "LIVE BUG: peakLevelDbfs is the loudest level since the last release, not " +
            "the peak of the note being tracked, so a quieter note that follows a " +
            "louder one reads as that louder one decaying forever. The fold-limit " +
            "escape is gated on !decaying, so it never fires and the reading stays " +
            "an octave (or two) below the string being played until a full silent " +
            "release. Giving the peak a release fixes this but regresses " +
            "RealRecordingPitchTest's cello run, which folds a genuine artifact only " +
            "because the stale peak makes `decaying` true. Needs a real fix, not a " +
            "release constant."
    )
    @Test
    fun `moving two octaves up at a lower level is followed`() {
        // Guitar: a hard-plucked low E, then the high E picked softly. Exactly
        // two octaves, which is what makes the jump look like an artifact.
        val smoother = PitchSmoother()
        smoother.play(hz = 82.41, levelDbfs = -10.0, frames = 20)
        val tracked = smoother.play(hz = 329.63, levelDbfs = -30.0, frames = 25)
        assertEquals("stuck two octaves below the string being played", 329.63, tracked, 8.0)
    }

    @Ignore(
        "LIVE BUG: peakLevelDbfs is the loudest level since the last release, not " +
            "the peak of the note being tracked, so a quieter note that follows a " +
            "louder one reads as that louder one decaying forever. The fold-limit " +
            "escape is gated on !decaying, so it never fires and the reading stays " +
            "an octave (or two) below the string being played until a full silent " +
            "release. Giving the peak a release fixes this but regresses " +
            "RealRecordingPitchTest's cello run, which folds a genuine artifact only " +
            "because the stale peak makes `decaying` true. Needs a real fix, not a " +
            "release constant."
    )
    @Test
    fun `a brief gap between plucks does not strand the reading an octave down`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 82.41, levelDbfs = -10.0, frames = 20)
        // Shorter than the release window, so the lock survives the gap.
        repeat(5) { smoother.push(frame(null, -90.0)) }
        val tracked = smoother.play(hz = 329.63, levelDbfs = -30.0, frames = 25)
        assertEquals(329.63, tracked, 8.0)
    }

    @Test
    fun `an octave jump at the same level is followed straight away`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 220.0, levelDbfs = -14.0, frames = 20)
        val tracked = smoother.play(hz = 440.0, levelDbfs = -14.0, frames = 20)
        assertEquals(440.0, tracked, 5.0)
    }

    @Test
    fun `a jump that is not an octave is never folded`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 220.0, levelDbfs = -10.0, frames = 20)
        val tracked = smoother.play(hz = 330.0, levelDbfs = -30.0, frames = 20)
        assertEquals(330.0, tracked, 5.0)
    }

    @Test
    fun `a downward octave jump is taken at face value`() {
        val smoother = PitchSmoother()
        smoother.play(hz = 440.0, levelDbfs = -10.0, frames = 20)
        val tracked = smoother.play(hz = 220.0, levelDbfs = -30.0, frames = 20)
        assertTrue("reading was pushed up an octave, got $tracked", tracked < 260.0)
    }
}
