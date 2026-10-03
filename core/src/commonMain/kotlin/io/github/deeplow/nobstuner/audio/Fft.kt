package io.github.deeplow.nobstuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley–Tukey FFT.
 *
 * Twiddle factors and the bit-reversal permutation are precomputed once per
 * instance, so running a transform allocates nothing. Instances are not
 * thread-safe only insofar as the caller's arrays are; the instance itself holds
 * no mutable transform state.
 */
class Fft(val size: Int) {

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two, got $size" }
    }

    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)
    private val bitReverse = IntArray(size)

    init {
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            cosTable[i] = cos(angle)
            sinTable[i] = sin(angle)
        }
        val bits = size.countTrailingZeroBits()
        for (i in 0 until size) {
            var remaining = i
            var reversed = 0
            repeat(bits) {
                reversed = (reversed shl 1) or (remaining and 1)
                remaining = remaining ushr 1
            }
            bitReverse[i] = reversed
        }
    }

    /** Forward transform of [re]/[im], both of length [size], in place. */
    fun forward(re: DoubleArray, im: DoubleArray) {
        require(re.size == size && im.size == size) { "Arrays must be of length $size" }

        for (i in 0 until size) {
            val j = bitReverse[i]
            if (j > i) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        var blockSize = 2
        while (blockSize <= size) {
            val half = blockSize / 2
            val tableStep = size / blockSize
            var blockStart = 0
            while (blockStart < size) {
                var i = blockStart
                var tableIndex = 0
                while (i < blockStart + half) {
                    val pair = i + half
                    val c = cosTable[tableIndex]
                    val s = sinTable[tableIndex]
                    val tre = re[pair] * c - im[pair] * s
                    val tim = re[pair] * s + im[pair] * c
                    re[pair] = re[i] - tre
                    im[pair] = im[i] - tim
                    re[i] += tre
                    im[i] += tim
                    i++
                    tableIndex += tableStep
                }
                blockStart += blockSize
            }
            blockSize = blockSize shl 1
        }
    }

    /** Inverse transform, normalised by 1/[size], in place. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until size) im[i] = -im[i]
        forward(re, im)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= -scale
        }
    }
}
