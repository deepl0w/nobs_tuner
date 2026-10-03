package io.github.deeplow.nobstuner.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The high-pass sits between the microphone and the detector, so its job is to
 * remove rumble without touching the lowest notes the app claims to support.
 * These pin the response the class documents.
 */
class HighPassFilterTest {

    private val sampleRate = 44_100

    /** Steady-state gain in dB at [frequencyHz], measured past the transient. */
    private fun gainDbAt(frequencyHz: Double, cornerHz: Double = 25.0): Double {
        val filter = HighPassFilter(sampleRate, cornerHz)
        val total = sampleRate * 4
        val samples = FloatArray(total) {
            sin(2.0 * PI * frequencyHz * it / sampleRate).toFloat()
        }
        filter.processInPlace(samples)
        var energy = 0.0
        for (i in total - sampleRate until total) energy += samples[i].toDouble() * samples[i]
        val rms = sqrt(energy / sampleRate)
        return 20.0 * log10(rms / (1.0 / sqrt(2.0)))
    }

    @Test
    fun `the corner is 3 dB down`() {
        assertEquals(-3.0, gainDbAt(25.0), 0.2)
    }

    @Test
    fun `the lowest supported note is barely touched`() {
        // B0 on a five-string bass, the lowest pitch in the catalog.
        assertEquals(-1.6, gainDbAt(30.87), 0.3)
    }

    @Test
    fun `musical frequencies pass through untouched`() {
        for (hz in listOf(82.41, 110.0, 220.0, 440.0, 1760.0)) {
            assertEquals("$hz Hz was coloured", 0.0, gainDbAt(hz), 0.5)
        }
    }

    @Test
    fun `handling rumble is heavily attenuated`() {
        // The class promises 4 Hz loses more than 30 dB while B0 loses about 2.
        assertTrue("4 Hz rumble only lost ${gainDbAt(4.0)} dB", gainDbAt(4.0) < -30.0)
        assertTrue("10 Hz rumble only lost ${gainDbAt(10.0)} dB", gainDbAt(10.0) < -15.0)
        assertTrue(
            "rumble is not attenuated far more than the lowest note",
            gainDbAt(4.0) < gainDbAt(30.87) - 25.0,
        )
    }

    @Test
    fun `the response is a second order Butterworth at the stated corner`() {
        // Independent closed form: |H|^2 = (f/fc)^4 / (1 + (f/fc)^4). Comparing
        // the measured sweep against this catches a mistyped biquad coefficient,
        // which the individual anchors above would mostly survive.
        for (hz in listOf(4.0, 10.0, 20.0, 25.0, 30.87, 40.0, 60.0, 100.0, 440.0, 2000.0)) {
            val ratio = (hz / 25.0).pow(4.0)
            val expected = 10.0 * log10(ratio / (1.0 + ratio))
            assertEquals("response is off at $hz Hz", expected, gainDbAt(hz), 0.15)
        }
    }

    @Test
    fun `a DC offset is removed`() {
        val filter = HighPassFilter(sampleRate)
        val samples = FloatArray(sampleRate) { 1.0f }
        filter.processInPlace(samples)
        assertEquals("DC survived the filter", 0.0, samples.last().toDouble(), 1e-6)
    }

    @Test
    fun `the filter is stable over a long run of noise`() {
        val filter = HighPassFilter(sampleRate)
        val random = java.util.Random(7)
        val samples = FloatArray(sampleRate * 10) { (random.nextDouble() * 2 - 1).toFloat() }
        filter.processInPlace(samples)
        assertTrue(
            "filter blew up or produced NaN",
            samples.all { it.isFinite() && abs(it) < 10f },
        )
    }

    @Test
    fun `state carries across calls so a hop boundary is not a discontinuity`() {
        val whole = FloatArray(4096) { sin(2.0 * PI * 440.0 * it / sampleRate).toFloat() }
        val piecewise = whole.copyOf()

        HighPassFilter(sampleRate).processInPlace(whole)

        val filter = HighPassFilter(sampleRate)
        var offset = 0
        while (offset < piecewise.size) {
            filter.processInPlace(piecewise, offset, 512)
            offset += 512
        }

        for (i in whole.indices) {
            assertEquals("sample $i diverged between whole and hopped runs", whole[i], piecewise[i], 1e-6f)
        }
    }

    @Test
    fun `reset clears the state`() {
        val filter = HighPassFilter(sampleRate)
        val loud = FloatArray(1024) { 1.0f }
        filter.processInPlace(loud)

        filter.reset()
        val fresh = FloatArray(1024) { sin(2.0 * PI * 440.0 * it / sampleRate).toFloat() }
        val expected = fresh.copyOf()
        filter.processInPlace(fresh)
        HighPassFilter(sampleRate).processInPlace(expected)

        for (i in fresh.indices) {
            assertEquals("reset left state behind at sample $i", expected[i], fresh[i], 1e-7f)
        }
    }
}
