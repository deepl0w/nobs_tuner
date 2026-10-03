package io.github.deeplow.nobstuner.audio

import io.github.deeplow.nobstuner.model.Notes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 44_100
private const val FRAME = 8192

class PitchDetectorTest {

    private fun detector() = PitchDetector(SAMPLE_RATE, FRAME)

    /** A sine at [frequency], optionally with a few harmonics and a phase offset. */
    private fun tone(
        frequency: Double,
        harmonics: Int = 1,
        amplitude: Double = 0.4,
        phase: Double = 0.0,
        noise: Double = 0.0,
        seed: Int = 1,
    ): FloatArray {
        val random = Random(seed)
        return FloatArray(FRAME) { i ->
            var value = 0.0
            for (h in 1..harmonics) {
                value += (1.0 / h) * sin(2.0 * PI * frequency * h * i / SAMPLE_RATE + phase)
            }
            value *= amplitude
            if (noise > 0.0) value += random.nextDouble(-noise, noise)
            value.toFloat()
        }
    }

    private fun assertDetects(expectedHz: Double, samples: FloatArray, toleranceCents: Double) {
        val estimate = detector().analyse(samples)
        val detected = estimate.frequencyHz
        assertNotNull("no pitch detected for $expectedHz Hz", detected)
        val cents = 1200.0 * (Math.log(detected!! / expectedHz) / Math.log(2.0))
        assertTrue(
            "expected $expectedHz Hz, got $detected Hz (${"%.2f".format(cents)} cents off)",
            abs(cents) <= toleranceCents,
        )
    }

    @Test
    fun `detects open guitar strings`() {
        // E2 A2 D3 G3 B3 E4
        intArrayOf(40, 45, 50, 55, 59, 64).forEach { midi ->
            assertDetects(Notes.frequencyOf(midi), tone(Notes.frequencyOf(midi)), 1.0)
        }
    }

    @Test
    fun `detects the low end of a five string bass`() {
        // B0 is the lowest note the app claims to handle, at ~30.87 Hz.
        val b0 = Notes.frequencyOf(23)
        assertDetects(b0, tone(b0), 2.0)
        assertDetects(Notes.frequencyOf(28), tone(Notes.frequencyOf(28)), 1.0)
    }

    @Test
    fun `detects the high end of a violin E string`() {
        val e5 = Notes.frequencyOf(76)
        assertDetects(e5, tone(e5), 1.0)
        // And an octave above that, which chromatic mode should still catch.
        assertDetects(Notes.frequencyOf(88), tone(Notes.frequencyOf(88)), 2.0)
    }

    @Test
    fun `reports the fundamental, not a harmonic, for rich tones`() {
        // A plucked string is nothing like a sine; this is the case that
        // separates YIN from a plain spectral peak picker.
        intArrayOf(40, 45, 50, 55).forEach { midi ->
            val f0 = Notes.frequencyOf(midi)
            assertDetects(f0, tone(f0, harmonics = 12), 2.0)
        }
    }

    @Test
    fun `survives a missing fundamental`() {
        // Small speakers and some pickups roll off the fundamental entirely.
        val f0 = Notes.frequencyOf(40)
        val samples = FloatArray(FRAME) { i ->
            var value = 0.0
            for (h in 2..8) value += (1.0 / h) * sin(2.0 * PI * f0 * h * i / SAMPLE_RATE)
            (value * 0.4).toFloat()
        }
        assertDetects(f0, samples, 3.0)
    }

    @Test
    fun `tolerates moderate noise`() {
        val f0 = Notes.frequencyOf(45)
        assertDetects(f0, tone(f0, harmonics = 6, noise = 0.03), 3.0)
    }

    @Test
    fun `is insensitive to phase and amplitude`() {
        val f0 = Notes.frequencyOf(50)
        assertDetects(f0, tone(f0, harmonics = 5, phase = 1.3), 2.0)
        assertDetects(f0, tone(f0, harmonics = 5, amplitude = 0.02), 2.0)
        assertDetects(f0, tone(f0, harmonics = 5, amplitude = 0.95), 2.0)
    }

    @Test
    fun `resolves pitches between the semitones`() {
        // A string 30 cents flat of A2 must read as 30 cents flat, not snap to A2.
        val target = Notes.frequencyOf(45) * Math.pow(2.0, -30.0 / 1200.0)
        assertDetects(target, tone(target, harmonics = 6), 2.0)
    }

    @Test
    fun `ignores a DC offset`() {
        val f0 = Notes.frequencyOf(55)
        val samples = tone(f0, harmonics = 4)
        val biased = FloatArray(FRAME) { samples[it] + 0.5f }
        assertDetects(f0, biased, 2.0)
    }

    @Test
    fun `silence yields no pitch`() {
        val estimate = detector().analyse(FloatArray(FRAME))
        assertNull(estimate.frequencyHz)
        assertEquals(0.0, estimate.clarity, 1e-9)
        assertTrue(estimate.levelDbfs <= -100.0)
    }

    @Test
    fun `white noise is not reported as a pitch`() {
        val random = Random(3)
        val samples = FloatArray(FRAME) { random.nextDouble(-0.3, 0.3).toFloat() }
        val estimate = detector().analyse(samples)
        // Either nothing is found, or whatever is found is flagged as unreliable.
        assertTrue(
            "noise reported as a confident pitch: $estimate",
            estimate.frequencyHz == null || estimate.clarity < 0.76,
        )
    }

    @Test
    fun `level tracks amplitude`() {
        val loud = detector().analyse(tone(220.0, amplitude = 0.5))
        val quiet = detector().analyse(tone(220.0, amplitude = 0.05))
        assertTrue(loud.levelDbfs > quiet.levelDbfs + 15.0)
        assertTrue(loud.levelDbfs < 0.0)
    }

    @Test
    fun `clarity is high for a clean tone`() {
        val estimate = detector().analyse(tone(196.0, harmonics = 5))
        assertTrue("clarity was ${estimate.clarity}", estimate.clarity > 0.9)
    }

    @Test
    fun `a detector can be reused`() {
        val detector = detector()
        intArrayOf(40, 64, 45, 55).forEach { midi ->
            val f0 = Notes.frequencyOf(midi)
            val detected = detector.analyse(tone(f0, harmonics = 6)).frequencyHz
            assertNotNull(detected)
            assertEquals(f0, detected!!, f0 * 0.002)
        }
    }

    @Test
    fun `rejects frames that are too short`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            detector().analyse(FloatArray(FRAME - 1))
        }
    }

    @Test
    fun `rejects a frame size that cannot hold the frequency range`() {
        // The integration window is half the frame, so a 16-sample frame leaves
        // room for lags up to 7 — below the shortest lag the range implies.
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PitchDetector(SAMPLE_RATE, frameSize = 16)
        }
        // A frame only has to span the range it is asked for, not the default one.
        PitchDetector(SAMPLE_RATE, frameSize = 64, minFrequencyHz = 2000.0, maxFrequencyHz = 4200.0)
    }

    @Test
    fun `rejects a non power of two frame size`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PitchDetector(SAMPLE_RATE, frameSize = 3000)
        }
    }

    @Test
    fun `honours a narrowed frequency range`() {
        // Restricting the range is how a caller trades low-note reach for speed;
        // a note below the floor must not be reported as something else.
        val detector = PitchDetector(
            SAMPLE_RATE,
            frameSize = FRAME,
            minFrequencyHz = 200.0,
            maxFrequencyHz = 1000.0,
        )
        val inRange = detector.analyse(tone(440.0, harmonics = 4))
        assertEquals(440.0, inRange.frequencyHz!!, 1.0)

        val tooLow = detector.analyse(tone(60.0, harmonics = 4)).frequencyHz
        assertTrue(
            "60 Hz should not be reported by a 200-1000 Hz detector, got $tooLow",
            tooLow == null || tooLow >= 190.0,
        )
    }
}
