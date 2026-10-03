package io.github.deeplow.nobstuner.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Diagnostic: how loud is the same sound through each audio source on this
 * device? Not an assertion — it prints a table so the default in [AudioEngine]
 * can be chosen from measurements instead of assumptions.
 */
@RunWith(AndroidJUnit4::class)
class AudioSourceProbeTest {

    private val sources = listOf(
        "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
        "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "MIC" to MediaRecorder.AudioSource.MIC,
        "CAMCORDER" to MediaRecorder.AudioSource.CAMCORDER,
        "DEFAULT" to MediaRecorder.AudioSource.DEFAULT,
    )

    @Test
    fun compareSourceLevels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "RECORD_AUDIO not granted; grant it and re-run to probe the sources",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        val report = StringBuilder()
        fun line(text: String) {
            Log.i(TAG, text)
            report.appendLine(text)
        }

        val rate = 44_100
        val minBuffer = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        line("minBufferSize=$minBuffer")

        for ((name, source) in sources) {
            val result = runCatching { measure(source, rate, maxOf(minBuffer, 8192)) }
            val text = result.getOrNull()?.let { (peak, rms, frames) ->
                "peak=%.1f dBFS  rms=%.1f dBFS  frames=%d"
                    .format(java.util.Locale.ROOT, peak, rms, frames)
            } ?: "FAILED: ${result.exceptionOrNull()?.message}"
            line("%-18s %s".format(java.util.Locale.ROOT, name, text))
        }

        val out = java.io.File(context.getExternalFilesDir(null), "audio-source-probe.txt")
        out.writeText(report.toString())
        Log.i(TAG, "written to ${'$'}{out.absolutePath}")
    }

    private fun measure(source: Int, rate: Int, bufferBytes: Int): Triple<Double, Double, Int> {
        val recorder = AudioRecord(
            source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "not initialised" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "not recording" }

            val buffer = ShortArray(4096)
            var sumSquares = 0.0
            var count = 0L
            var peak = 0.0
            var frames = 0
            // Discard the first reads: the first buffers after startRecording
            // are often zeros while the input warms up.
            repeat(6) { recorder.read(buffer, 0, buffer.size) }
            val deadline = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < deadline) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) break
                frames++
                for (i in 0 until read) {
                    val v = buffer[i] / 32768.0
                    sumSquares += v * v
                    if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
                }
                count += read
            }
            val rms = if (count > 0) sqrt(sumSquares / count) else 0.0
            return Triple(db(peak), db(rms), frames)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun db(v: Double) = if (v <= 1e-9) -120.0 else 20.0 * log10(v)

    private companion object {
        const val TAG = "NobsTunerProbe"
    }
}
