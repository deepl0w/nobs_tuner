package io.github.deeplow.nobstuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FftTest {

    @Test
    fun `rejects non power of two sizes`() {
        assertFailsWith<IllegalArgumentException> { Fft(100) }
        assertFailsWith<IllegalArgumentException> { Fft(1) }
    }

    @Test
    fun `matches a naive DFT`() {
        val n = 64
        val random = Random(7)
        val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val im = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }

        val (expectedRe, expectedIm) = naiveDft(re, im)

        val actualRe = re.copyOf()
        val actualIm = im.copyOf()
        Fft(n).forward(actualRe, actualIm)

        for (k in 0 until n) {
            assertEquals(expectedRe[k], actualRe[k], 1e-9, "re[$k]")
            assertEquals(expectedIm[k], actualIm[k], 1e-9, "im[$k]")
        }
    }

    @Test
    fun `a pure tone lands in a single bin`() {
        val n = 256
        val bin = 9
        val re = DoubleArray(n) { cos(2.0 * PI * bin * it / n) }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)

        for (k in 0 until n) {
            val magnitude = kotlin.math.hypot(re[k], im[k])
            val expected = if (k == bin || k == n - bin) n / 2.0 else 0.0
            assertEquals(expected, magnitude, 1e-8, "bin $k")
        }
    }

    @Test
    fun `inverse undoes forward`() {
        val n = 512
        val random = Random(11)
        val originalRe = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val originalIm = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }

        val re = originalRe.copyOf()
        val im = originalIm.copyOf()
        val fft = Fft(n)
        fft.forward(re, im)
        fft.inverse(re, im)

        for (i in 0 until n) {
            assertEquals(originalRe[i], re[i], 1e-10)
            assertEquals(originalIm[i], im[i], 1e-10)
        }
    }

    @Test
    fun `instances are reusable across transforms`() {
        val fft = Fft(128)
        repeat(3) {
            val re = DoubleArray(128) { i -> sin(2.0 * PI * 5 * i / 128) }
            val im = DoubleArray(128)
            fft.forward(re, im)
            assertEquals(128 / 2.0, kotlin.math.hypot(re[5], im[5]), 1e-9)
        }
    }

    private fun naiveDft(re: DoubleArray, im: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = re.size
        val outRe = DoubleArray(n)
        val outIm = DoubleArray(n)
        for (k in 0 until n) {
            var sumRe = 0.0
            var sumIm = 0.0
            for (j in 0 until n) {
                val angle = -2.0 * PI * k * j / n
                sumRe += re[j] * cos(angle) - im[j] * sin(angle)
                sumIm += re[j] * sin(angle) + im[j] * cos(angle)
            }
            outRe[k] = sumRe
            outIm[k] = sumIm
        }
        return outRe to outIm
    }
}
