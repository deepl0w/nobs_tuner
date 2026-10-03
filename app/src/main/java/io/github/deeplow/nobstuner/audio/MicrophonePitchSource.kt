package io.github.deeplow.nobstuner.audio

/**
 * A [PitchSource] that can say, before it is collected, whether it is allowed
 * to open the microphone.
 *
 * Android grants `RECORD_AUDIO` ahead of time and will answer synchronously, so
 * the tuner can show "Grant access" rather than opening the device and handling
 * the failure. Nothing is lost if the answer turns out to be stale — the
 * contract already carries permission failure through
 * [PitchSource.pitchEstimates] and the view model catches it — which is why
 * this is an Android convenience rather than part of the shared contract.
 */
interface MicrophonePitchSource : PitchSource {

    /** Whether the microphone may be opened right now. */
    fun hasPermission(): Boolean
}
