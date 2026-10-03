package io.github.deeplow.stringtune.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second-order Butterworth high-pass, used to strip handling noise and room
 * rumble from the microphone before pitch analysis.
 *
 * Phone microphones pick up a lot of energy below 20 Hz — a hand shifting on
 * the case, a table knock, air moving. None of it is musical, but it inflates
 * the frame level enough to drag quiet passages past the silence gate.
 *
 * The corner sits at 25 Hz, below the lowest note the app supports (B0 at
 * 30.87 Hz on a five-string bass), so that note loses about 2 dB while 4 Hz
 * rumble loses more than 30.
 *
 * State carries across calls, so one filter belongs to one continuous stream.
 */
class HighPassFilter(sampleRate: Int, cornerHz: Double = 25.0) {

    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val w0 = 2.0 * PI * cornerHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * (1.0 / sqrt(2.0))) // Butterworth: Q = 1/sqrt(2)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0) / a0
        b1 = (-(1.0 + cosW0)) / a0
        b2 = ((1.0 + cosW0) / 2.0) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    /** Filters [count] samples of [samples] starting at [offset], in place. */
    fun processInPlace(samples: FloatArray, offset: Int = 0, count: Int = samples.size - offset) {
        for (i in offset until offset + count) {
            val x0 = samples[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            samples[i] = y0.toFloat()
        }
    }
}
