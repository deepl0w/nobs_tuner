package io.github.deeplow.stringtune.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** One analysed audio frame. */
data class PitchEstimate(
    /** Fundamental frequency in Hz, or null when no stable pitch was found. */
    val frequencyHz: Double?,
    /** 0..1 — how periodic the frame looked. Below ~0.8 the reading is shaky. */
    val clarity: Double,
    /** Frame level in dBFS, used to tell "silence" from "a quiet note". */
    val levelDbfs: Double,
)

/**
 * YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002) with the
 * difference function computed through the FFT.
 *
 * The naive difference function is O(W·τmax); evaluated at every hop that is far
 * too slow for a phone. Expanding the square gives
 *
 *     d(τ) = Σⱼ x[j]² + Σⱼ x[j+τ]² − 2·Σⱼ x[j]·x[j+τ]
 *
 * where the first term is constant, the second is a sliding window sum updated
 * in O(1), and the third is a cross-correlation obtained from one complex
 * multiply between two FFTs. That brings a frame down to O(N log N).
 *
 * Not thread-safe: the scratch buffers are reused between calls, so a detector
 * belongs to exactly one analysis loop.
 */
class PitchDetector(
    private val sampleRate: Int,
    private val frameSize: Int,
    /** YIN's absolute threshold. Lower = stricter about periodicity. */
    private val threshold: Double = 0.12,
    minFrequencyHz: Double = 27.0,
    maxFrequencyHz: Double = 4200.0,
) {
    /** Integration window: the number of terms summed in the difference function. */
    private val windowSize = frameSize / 2

    private val fft = Fft(frameSize)

    // Scratch, allocated once. `a` is the first half of the frame, `b` the whole
    // frame; their cross-correlation is what the difference function needs.
    private val aRe = DoubleArray(frameSize)
    private val aIm = DoubleArray(frameSize)
    private val bRe = DoubleArray(frameSize)
    private val bIm = DoubleArray(frameSize)
    private val difference = DoubleArray(windowSize)
    private val normalised = DoubleArray(windowSize)
    private val centred = DoubleArray(frameSize)

    private val minTau = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
    private val maxTau = (sampleRate / minFrequencyHz).toInt().coerceAtMost(windowSize - 1)

    init {
        require(frameSize > 1 && frameSize and (frameSize - 1) == 0) {
            "frameSize must be a power of two, got $frameSize"
        }
        require(maxTau > minTau) { "Frequency range does not fit in a frame of $frameSize samples" }
    }

    /**
     * Analyses one frame. [samples] must hold at least [frameSize] values in
     * roughly [-1, 1]; anything beyond [frameSize] is ignored.
     */
    fun analyse(samples: FloatArray): PitchEstimate {
        require(samples.size >= frameSize) { "Need $frameSize samples, got ${samples.size}" }

        // Remove DC offset; a biased frame inflates d(τ) uniformly and drags the
        // normalised curve towards 1, which costs real detections on quiet notes.
        var sum = 0.0
        for (i in 0 until frameSize) sum += samples[i]
        val mean = sum / frameSize

        var energy = 0.0
        for (i in 0 until frameSize) {
            val value = samples[i] - mean
            centred[i] = value
            energy += value * value
        }

        val rms = sqrt(energy / frameSize)
        val levelDbfs = if (rms <= 1e-9) -120.0 else 20.0 * log10(rms)
        if (rms <= 1e-6) {
            return PitchEstimate(frequencyHz = null, clarity = 0.0, levelDbfs = levelDbfs)
        }

        computeDifference()
        cumulativeMeanNormalise()

        val tau = preferTrueFundamental(absoluteThreshold())
        if (tau < 0) {
            return PitchEstimate(frequencyHz = null, clarity = 0.0, levelDbfs = levelDbfs)
        }

        val refinedTau = parabolicInterpolate(tau)
        if (refinedTau <= 0.0) {
            return PitchEstimate(frequencyHz = null, clarity = 0.0, levelDbfs = levelDbfs)
        }

        val clarity = (1.0 - normalised[tau]).coerceIn(0.0, 1.0)
        return PitchEstimate(
            frequencyHz = sampleRate / refinedTau,
            clarity = clarity,
            levelDbfs = levelDbfs,
        )
    }

    /** Fills [difference] with d(τ) for τ in 0 until [windowSize]. */
    private fun computeDifference() {
        java.util.Arrays.fill(aIm, 0.0)
        java.util.Arrays.fill(bIm, 0.0)
        // a = first half of the frame, zero-padded; b = the whole frame.
        System.arraycopy(centred, 0, bRe, 0, frameSize)
        java.util.Arrays.fill(aRe, 0.0)
        System.arraycopy(centred, 0, aRe, 0, windowSize)

        fft.forward(aRe, aIm)
        fft.forward(bRe, bIm)

        // conj(A) * B, then inverse — element m is Σⱼ a[j]·b[j+m]. Because
        // a is zero past windowSize and j+m never reaches frameSize, the circular
        // correlation agrees with the linear one over the τ range we read.
        for (i in 0 until frameSize) {
            val re = aRe[i] * bRe[i] + aIm[i] * bIm[i]
            val im = aRe[i] * bIm[i] - aIm[i] * bRe[i]
            aRe[i] = re
            aIm[i] = im
        }
        fft.inverse(aRe, aIm)

        // Sliding sum of squares over [τ, τ+windowSize).
        var power = 0.0
        for (j in 0 until windowSize) power += centred[j] * centred[j]
        val power0 = power

        difference[0] = 0.0
        for (tau in 1 until windowSize) {
            power += centred[tau + windowSize - 1] * centred[tau + windowSize - 1] -
                centred[tau - 1] * centred[tau - 1]
            difference[tau] = (power0 + power - 2.0 * aRe[tau]).coerceAtLeast(0.0)
        }
    }

    /** Converts d(τ) into YIN's cumulative mean normalised difference d'(τ). */
    private fun cumulativeMeanNormalise() {
        normalised[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until windowSize) {
            runningSum += difference[tau]
            normalised[tau] = if (runningSum <= 0.0) {
                1.0
            } else {
                difference[tau] * tau / runningSum
            }
        }
    }

    /**
     * First τ whose d'(τ) dips below [threshold], walked down to the bottom of
     * that dip. Returns -1 when nothing in range is periodic enough.
     *
     * Taking the *first* qualifying dip rather than the global minimum is what
     * keeps YIN from reporting the octave above on harmonically rich strings.
     */
    private fun absoluteThreshold(): Int {
        var tau = minTau
        while (tau <= maxTau) {
            if (normalised[tau] < threshold) {
                while (tau + 1 <= maxTau && normalised[tau + 1] < normalised[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }
        return -1
    }

    /**
     * Pulls a candidate back down to the real fundamental.
     *
     * YIN takes the first lag that dips below the threshold, which on a string
     * whose fundamental is weak can be half the true period — the app then shows
     * the note an octave high. The giveaway is that such a lag leaves the
     * fundamental unexplained, so d' there stays well clear of zero while d' at
     * twice the lag collapses.
     *
     * When the candidate really is a period, d' is already ~0 and so is d' at
     * every multiple of it; there is nothing to choose between them, which is
     * why the check is skipped below [OCTAVE_CHECK_FLOOR]. Without that guard a
     * perfectly periodic tone would be dragged an octave down.
     */
    private fun preferTrueFundamental(tau: Int): Int {
        if (tau < 0 || normalised[tau] < OCTAVE_CHECK_FLOOR) return tau

        // Stop at the first multiple that qualifies. Once a lag is a true
        // period every multiple of it looks just as good, so continuing would
        // keep stepping down octaves for no reason.
        for (multiple in 2..4) {
            val centre = tau * multiple
            if (centre > maxTau) break
            // The true period is not necessarily an exact multiple of a lag that
            // was itself rounded, so look around the expected position.
            val radius = (centre / 20).coerceAtLeast(1)
            val from = (centre - radius).coerceAtLeast(minTau)
            val to = (centre + radius).coerceAtMost(maxTau)
            var candidate = from
            for (t in from..to) if (normalised[t] < normalised[candidate]) candidate = t

            if (normalised[candidate] < normalised[tau] * OCTAVE_SWITCH_RATIO) {
                return candidate
            }
        }
        return tau
    }

    /** Sub-sample refinement of the dip, fitting a parabola to its neighbours. */
    private fun parabolicInterpolate(tau: Int): Double {
        val left = (tau - 1).coerceAtLeast(0)
        val right = (tau + 1).coerceAtMost(windowSize - 1)
        if (left == tau || right == tau) return tau.toDouble()

        val s0 = difference[left]
        val s1 = difference[tau]
        val s2 = difference[right]
        val denominator = 2.0 * (2.0 * s1 - s2 - s0)
        if (denominator == 0.0) return tau.toDouble()
        val shift = (s2 - s0) / denominator
        // A well-formed dip shifts by well under half a sample; anything larger
        // means the parabola fit is bogus, so keep the integer lag.
        return if (shift > -1.0 && shift < 1.0) tau + shift else tau.toDouble()
    }

    private companion object {
        /**
         * Minimum d' at the candidate lag before an octave correction is even
         * considered. Measured against real recordings: correct detections sit
         * at or below ~0.04, genuine octave-up errors at 0.09 and above.
         */
        const val OCTAVE_CHECK_FLOOR = 0.02

        /** How much better the longer period must look before we move to it. */
        const val OCTAVE_SWITCH_RATIO = 0.5
    }
}
