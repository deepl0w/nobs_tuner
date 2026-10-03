package io.github.deeplow.nobstuner.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Microphone capture feeding the [PitchDetector].
 *
 * Collecting [pitchEstimates] opens the microphone; cancelling the collection
 * closes it. Nothing is retained between collections, so the recorder is never
 * left holding the mic while the app is backgrounded.
 */
class AudioEngine(private val context: Context) {

    companion object {
        /** 44.1 kHz is supported on every Android device; higher rates are not. */
        const val SAMPLE_RATE = Analysis.PREFERRED_SAMPLE_RATE

        /** Frame and hop come from the shared core, so every platform analyses alike. */
        const val FRAME_SIZE = Analysis.FRAME_SIZE
        const val HOP_SIZE = Analysis.HOP_SIZE

        private const val DIAG = "NobsTunerAudio"
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Cold flow of analysed frames. Throws [SecurityException] if the microphone
     * permission is missing and [IllegalStateException] if the recorder cannot
     * be opened (another app holding the mic, for example).
     */
    fun pitchEstimates(): Flow<PitchEstimate> = flow {
        if (!hasPermission()) throw SecurityException("RECORD_AUDIO permission not granted")

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer != AudioRecord.ERROR && minBuffer != AudioRecord.ERROR_BAD_VALUE) {
            "AudioRecord reported no usable buffer size for ${SAMPLE_RATE}Hz mono"
        }
        // Give the recorder room for several hops so a scheduling hiccup during
        // analysis does not drop samples on the floor.
        val bufferBytes = maxOf(minBuffer, HOP_SIZE * 2 * 4)

        val chosenSource = preferredAudioSource()
        // Worth a line in the log: which source a device hands over explains
        // most of the difference in how loud everything arrives.
        Log.i(DIAG, "audio source=${sourceName(chosenSource)} buffer=$bufferBytes")
        val recorder = createRecorder(bufferBytes, chosenSource)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord failed to initialise"
            }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord failed to start; the microphone may be in use by another app"
            }

            val detector = PitchDetector(sampleRate = SAMPLE_RATE, frameSize = FRAME_SIZE)
            val highPass = HighPassFilter(SAMPLE_RATE)
            val window = FloatArray(FRAME_SIZE)
            val hop = ShortArray(HOP_SIZE)

            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < HOP_SIZE) {
                    val read = recorder.read(hop, filled, HOP_SIZE - filled)
                    if (read <= 0) {
                        if (!currentCoroutineContext().isActive) return@flow
                        // ERROR_INVALID_OPERATION etc. — the recorder is gone.
                        error("AudioRecord read failed with code $read")
                    }
                    filled += read
                }

                // Slide the analysis window along by one hop.
                System.arraycopy(window, HOP_SIZE, window, 0, FRAME_SIZE - HOP_SIZE)
                val offset = FRAME_SIZE - HOP_SIZE
                for (i in 0 until HOP_SIZE) {
                    window[offset + i] = hop[i] / 32768f
                }
                // Filter only the samples that just arrived: the rest of the
                // window was filtered on an earlier pass, and running them
                // again would colour them twice.
                highPass.processInPlace(window, offset, HOP_SIZE)

                emit(detector.analyse(window))
            }
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            recorder.release()
        }
    }.flowOn(Dispatchers.Default)

    @SuppressLint("MissingPermission") // Checked above before the recorder is built.
    private fun createRecorder(bufferBytes: Int, source: Int): AudioRecord = AudioRecord(
        source,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferBytes,
    )

    /**
     * Picks the least-processed input available. Automatic gain control and
     * noise suppression both distort a decaying string's pitch, so UNPROCESSED
     * is strongly preferred; VOICE_RECOGNITION is the next best because most
     * devices disable AGC on it too.
     */
    private fun sourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "OTHER($source)"
    }

    private fun preferredAudioSource(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessedSupported = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.toBooleanStrictOrNull() == true
        return if (unprocessedSupported) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }
}
