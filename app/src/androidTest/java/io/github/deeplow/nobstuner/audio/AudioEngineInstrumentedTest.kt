package io.github.deeplow.nobstuner.audio

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Exercises the microphone plumbing on a real device: that [AudioEngine] can
 * open a recorder with the audio source it prefers, keep up with the stream, and
 * shut down cleanly when collection stops.
 *
 * What the audio *contains* is not checked here — that is covered off-device by
 * the unit tests, which push known recordings through the same detector. This
 * test is about the half of the path that only exists on a device.
 */
@RunWith(AndroidJUnit4::class)
class AudioEngineInstrumentedTest {

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private lateinit var engine: AudioEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = AudioEngine(context)
        // The test runner grants manifest permissions, so this should hold; if it
        // does not, every assertion below would fail for a misleading reason.
        assertTrue("RECORD_AUDIO was not granted to the test run", engine.hasPermission())
    }

    @Test
    fun producesFramesFromTheMicrophone() = runBlocking {
        val frames = withTimeout(15_000) {
            engine.pitchEstimates().take(8).toList()
        }
        assertEquals(8, frames.size)
        frames.forEach { estimate ->
            // Silence is fine; a level outside this range means the samples were
            // never written, or were written with the wrong scaling.
            assertTrue(
                "implausible frame level ${estimate.levelDbfs} dBFS",
                estimate.levelDbfs in -130.0..6.0,
            )
            assertTrue("clarity out of range", estimate.clarity in 0.0..1.0)
            estimate.frequencyHz?.let {
                assertTrue("frequency out of the detector's range: $it", it in 20.0..5000.0)
            }
        }
    }

    @Test
    fun keepsUpWithRealTime() = runBlocking {
        // Each frame advances by one hop, so N frames must not take much longer
        // than N hops of wall clock or the analysis is falling behind the mic.
        val frameCount = 12
        val hopMillis = AudioEngine.HOP_SIZE * 1000L / AudioEngine.SAMPLE_RATE
        val elapsed = measureTimeMillis {
            withTimeout(20_000) { engine.pitchEstimates().take(frameCount).toList() }
        }
        // Allow generous slack for the first buffer and for emulator jitter.
        val budget = hopMillis * frameCount * 3 + 3_000
        assertTrue(
            "took ${elapsed}ms for $frameCount frames, budget ${budget}ms",
            elapsed < budget,
        )
    }

    @Test
    fun releasesTheMicrophoneSoItCanBeReopened() = runBlocking {
        repeat(3) { attempt ->
            val frames = withTimeout(15_000) { engine.pitchEstimates().take(2).toList() }
            assertEquals("attempt $attempt produced the wrong number of frames", 2, frames.size)
        }
    }
}
