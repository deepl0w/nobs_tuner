package io.github.deeplow.nobstuner.audio

import kotlinx.coroutines.flow.Flow

/**
 * Where the tuner gets its pitch readings.
 *
 * [AudioEngine] is the microphone implementation. The interface exists so the
 * view model can be driven by a scripted flow in tests: an emulator cannot be
 * fed host audio, so a seam here is the only way to exercise the listening
 * state machine off-device.
 */
interface PitchSource {

    /** Whether the microphone may be opened right now. */
    fun hasPermission(): Boolean

    /**
     * Cold flow of analysed frames. Collecting opens the source; cancelling the
     * collection closes it. Implementations throw [SecurityException] when the
     * permission is missing and [IllegalStateException] when the source cannot
     * be opened.
     */
    fun pitchEstimates(): Flow<PitchEstimate>
}
