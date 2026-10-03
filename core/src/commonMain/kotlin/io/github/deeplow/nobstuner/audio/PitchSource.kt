package io.github.deeplow.nobstuner.audio

import kotlinx.coroutines.flow.Flow

/**
 * Where the tuner gets its pitch readings.
 *
 * `AudioEngine` is the Android microphone implementation and the browser has
 * its own; the interface exists so a view model can be driven by a scripted
 * flow in tests, because an emulator cannot be fed host audio and a seam here
 * is the only way to exercise the listening state machine off-device.
 *
 * Deliberately only the stream. Asking "may I open the microphone?" has no
 * answer that means the same thing on both platforms: Android can say yes or no
 * synchronously, while a browser decides at the moment of asking and reports it
 * asynchronously. A shared method would force the web implementation to return
 * a cached guess and present it as fact, so the pre-check belongs to whichever
 * platform can actually answer it — see `MicrophonePitchSource` on Android.
 */
interface PitchSource {

    /**
     * Cold flow of analysed frames. Collecting opens the source; cancelling the
     * collection closes it. Implementations throw [SecurityException] when the
     * permission is missing and [IllegalStateException] when the source cannot
     * be opened.
     */
    fun pitchEstimates(): Flow<PitchEstimate>
}
