package io.github.deeplow.nobstuner.audio

import io.github.deeplow.nobstuner.model.Notes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * End-to-end check against real instrument recordings rather than synthesised
 * tones: the whole file is pushed through [PitchDetector] and [PitchSmoother]
 * at the same frame and hop size the live microphone uses, and the notes that
 * come out are compared with the chromatic run the recording is known to hold.
 *
 * Fixtures are not in version control because they are tens of megabytes. Run
 * `tools/fetch-test-audio.sh` to download them; without them these tests skip.
 *
 * Source: University of Iowa Electronic Music Studios, Musical Instrument
 * Samples (theremin.music.uiowa.edu/MIS.html).
 */
class RealRecordingPitchTest {

    private fun fixture(name: String): File? {
        val url = javaClass.classLoader?.getResource("realaudio/$name") ?: return null
        return File(url.toURI()).takeIf { it.isFile }
    }

    /** Reads a mono 16-bit PCM WAV into normalised floats. */
    private fun readWav(file: File): Pair<FloatArray, Int> {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val format = stream.format
            require(format.channels == 1) { "expected mono, got ${format.channels} channels" }
            require(format.sampleSizeInBits == 16) { "expected 16-bit samples" }
            val bytes = stream.readAllBytes()
            val samples = FloatArray(bytes.size / 2)
            for (i in samples.indices) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt()
                samples[i] = ((hi shl 8) or lo).toShort() / 32768f
            }
            return samples to format.sampleRate.toInt()
        }
    }

    private data class HeldNote(val midi: Int, val centsSpread: List<Double>)

    /**
     * Streams [samples] exactly as the live microphone feed does and returns the notes that
     * stayed put long enough to be worth showing a user, in the order heard.
     */
    private fun notesHeard(samples: FloatArray, sampleRate: Int): List<HeldNote> {
        val detector = PitchDetector(sampleRate, Analysis.FRAME_SIZE)
        val smoother = PitchSmoother()
        val highPass = HighPassFilter(sampleRate)
        val window = FloatArray(Analysis.FRAME_SIZE)

        val held = mutableListOf<HeldNote>()
        var currentMidi: Int? = null
        var currentCents = mutableListOf<Double>()
        var framesOnNote = 0

        fun flush() {
            val midi = currentMidi
            // Four hops is ~190 ms: long enough to exclude pick attack and the
            // scrape of a bow change, short enough to catch every real note.
            if (midi != null && framesOnNote >= 4) held += HeldNote(midi, currentCents.toList())
            currentMidi = null
            currentCents = mutableListOf()
            framesOnNote = 0
        }

        var offset = 0
        while (offset + Analysis.HOP_SIZE <= samples.size) {
            System.arraycopy(window, Analysis.HOP_SIZE, window, 0, Analysis.FRAME_SIZE - Analysis.HOP_SIZE)
            System.arraycopy(
                samples, offset, window,
                Analysis.FRAME_SIZE - Analysis.HOP_SIZE, Analysis.HOP_SIZE,
            )
            highPass.processInPlace(
                window,
                Analysis.FRAME_SIZE - Analysis.HOP_SIZE,
                Analysis.HOP_SIZE,
            )
            offset += Analysis.HOP_SIZE

            val tracked = smoother.push(detector.analyse(window))
            if (tracked == null) {
                flush()
                continue
            }
            val reading = Notes.nearest(tracked.frequencyHz)
            if (reading.midi == currentMidi) {
                framesOnNote++
                currentCents += reading.cents
            } else {
                flush()
                currentMidi = reading.midi
                currentCents = mutableListOf(reading.cents)
                framesOnNote = 1
            }
        }
        flush()
        return held
    }

    private fun check(
        fixtureName: String,
        firstMidi: Int,
        lastMidi: Int,
        maxScatterCents: Double,
    ) {
        val file = fixture(fixtureName)
        assumeTrue("missing fixture $fixtureName — run tools/fetch-test-audio.sh", file != null)

        val (samples, rate) = readWav(file!!)
        val heard = notesHeard(samples, rate)
        val expected = (firstMidi..lastMidi).toList()

        assertTrue("$fixtureName: nothing detected at all", heard.isNotEmpty())

        // Every note the tuner settled on must be one that is actually played.
        val stray = heard.filter { it.midi !in expected }
        assertTrue(
            "$fixtureName: detected notes outside the recorded range " +
                stray.joinToString { "${Notes.name(it.midi)} (${it.centsSpread.size} frames)" },
            stray.isEmpty(),
        )

        // And every note in the run has to be found — no silently skipped strings.
        val missing = expected - heard.map { it.midi }.toSet()
        assertTrue(
            "$fixtureName: never detected ${missing.joinToString { Notes.name(it) }}",
            missing.isEmpty(),
        )

        // The run ascends, so the notes must come out in non-decreasing order.
        val order = heard.map { it.midi }
        assertEquals("$fixtureName: notes were not heard in ascending order: $order", order.sorted(), order)

        // A recorded instrument is not necessarily at A440 — the guitar in this
        // set sits about 35 cents flat throughout — so what is checked is that
        // every note is off by the *same* amount rather than by none at all.
        //
        // The statistic is the median absolute deviation from the run's own
        // median offset. A plain min-to-max spread would really be measuring the
        // player's intonation: a cellist leaning one note sharp is not a tracking
        // error, whereas a detector that drifted with pitch or snapped to the
        // nearest semitone would move every note and blow up the MAD.
        val offsets = heard.map { note -> note.centsSpread.sorted()[note.centsSpread.size / 2] }
        val median = offsets.sorted()[offsets.size / 2]
        val deviations = offsets.map { abs(it - median) }.sorted()
        val mad = deviations[deviations.size / 2]
        assertTrue(
            "$fixtureName: offsets scatter by $mad cents around $median " +
                offsets.joinToString(prefix = "[", postfix = "]") { "${it.roundToInt()}" },
            mad <= maxScatterCents,
        )

        // Each note still has to be nearer its own semitone than its neighbours'.
        offsets.forEachIndexed { index, cents ->
            assertTrue(
                "$fixtureName: ${Notes.name(heard[index].midi)} sat ${cents.roundToInt()} cents off",
                abs(cents) < 50.0,
            )
        }
    }

    @Test
    fun `acoustic guitar low E string, E2 through B2`() {
        // A plucked low E is the hardest common case: strong harmonics and a
        // fundamental near the bottom of the detector's range. This guitar is
        // tuned roughly 35 cents flat, which the spread check tolerates.
        check("guitar_E2-B2.wav", firstMidi = 40, lastMidi = 47, maxScatterCents = 12.0)
    }

    @Test
    fun `cello C string, C2 through B2`() {
        // Bowed and lower still — C2 is 65 Hz, B2 the top of the first position.
        check("cello_C2-B2.wav", firstMidi = 36, lastMidi = 47, maxScatterCents = 15.0)
    }

    @Test
    fun `violin G string, G3 through B3`() {
        // Bowed with vibrato, so the spread is wider by nature.
        check("violin_G3-B3.wav", firstMidi = 55, lastMidi = 59, maxScatterCents = 25.0)
    }
}
