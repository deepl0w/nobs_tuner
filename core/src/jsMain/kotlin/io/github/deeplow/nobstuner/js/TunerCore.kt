@file:JsExport
@file:OptIn(ExperimentalJsExport::class)

package io.github.deeplow.nobstuner.js

import io.github.deeplow.nobstuner.audio.Analysis
import io.github.deeplow.nobstuner.audio.HighPassFilter
import io.github.deeplow.nobstuner.audio.PitchDetector
import io.github.deeplow.nobstuner.audio.PitchSmoother
import io.github.deeplow.nobstuner.data.DisplayStyle
import io.github.deeplow.nobstuner.data.UserSettings
import io.github.deeplow.nobstuner.model.InstrumentFamily
import io.github.deeplow.nobstuner.model.PitchTargeting
import io.github.deeplow.nobstuner.model.Notes
import io.github.deeplow.nobstuner.model.Tuning
import io.github.deeplow.nobstuner.model.TuningCatalog

/**
 * The JavaScript face of the tuner core.
 *
 * Everything the web app knows about pitch comes through here, so the browser
 * runs the same YIN, the same octave correction and the same tuning catalog as
 * the Android app rather than a second implementation of them.
 *
 * Kotlin collections do not survive `@JsExport`, so lists cross the boundary
 * either as typed arrays or, for the catalog, as JSON parsed once at start-up.
 */

// ---- The audio pipeline --------------------------------------------------

/** One analysed frame, after smoothing. */
class HeardPitch internal constructor(
    val frequencyHz: Double,
    val clarity: Double,
    val levelDbfs: Double,
)

/**
 * Microphone samples in, a stable pitch out.
 *
 * One pipeline belongs to one continuous stream: the filter, the detector and
 * the smoother all carry state across calls. Build it with the sample rate the
 * browser actually gave you — `AudioContext.sampleRate` is 48 kHz on most
 * machines and is not negotiable — rather than assuming Android's 44.1 kHz.
 */
class TunerPipeline(sampleRate: Int) {

    val sampleRate: Int = sampleRate
    val frameSize: Int = Analysis.FRAME_SIZE
    val hopSize: Int = Analysis.HOP_SIZE

    private val detector = PitchDetector(sampleRate, Analysis.FRAME_SIZE)
    private val highPass = HighPassFilter(sampleRate)
    private val smoother = PitchSmoother()
    private val window = FloatArray(Analysis.FRAME_SIZE)

    /**
     * Slides [hop] into the analysis window and analyses it. Returns null while
     * nothing worth showing is being heard.
     *
     * [hop] must hold exactly [hopSize] samples in roughly [-1, 1].
     */
    fun push(hop: FloatArray): HeardPitch? {
        require(hop.size == hopSize) { "Expected $hopSize samples, got ${hop.size}" }

        window.copyInto(window, 0, hopSize, frameSize)
        hop.copyInto(window, frameSize - hopSize)
        // Only the samples that just arrived: the rest of the window was
        // filtered on an earlier pass and would be coloured twice.
        highPass.processInPlace(window, frameSize - hopSize, hopSize)

        val tracked = smoother.push(detector.analyse(window)) ?: return null
        return HeardPitch(tracked.frequencyHz, tracked.clarity, tracked.levelDbfs)
    }

    /** Forgets the current note. Call when the microphone stream restarts. */
    fun reset() {
        smoother.reset()
        highPass.reset()
        window.fill(0f)
    }
}

// ---- Resolving a pitch against a tuning ----------------------------------

/** What the display should show for the current moment. */
class Reading internal constructor(
    val frequencyHz: Double,
    val targetMidi: Int,
    val cents: Double,
    val clarity: Double,
    val levelDbfs: Double,
    /** Index into the strings that were passed in, or -1 in chromatic mode. */
    val stringIndex: Int,
    val inTune: Boolean,
)

/**
 * Works out which string [pitch] is aiming at and how far off it is.
 *
 * Pass an empty [strings] for chromatic mode, and -1 for [manualStringIndex] to
 * leave the choice of string to [autoDetectString].
 */
fun resolve(
    pitch: HeardPitch,
    strings: IntArray,
    referencePitchHz: Double,
    toleranceCents: Int,
    manualStringIndex: Int,
    autoDetectString: Boolean,
): Reading {
    val resolved = if (strings.isEmpty()) {
        PitchTargeting.resolveChromatic(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            a4Hz = referencePitchHz,
            toleranceCents = toleranceCents,
        )
    } else {
        PitchTargeting.resolveAgainstTuning(
            frequencyHz = pitch.frequencyHz,
            clarity = pitch.clarity,
            levelDbfs = pitch.levelDbfs,
            // Only the notes matter here; the name and family belong to the
            // JavaScript side, which holds the catalog the user picked from.
            tuning = Tuning("", "", InstrumentFamily.OTHER, strings.toList()),
            a4Hz = referencePitchHz,
            toleranceCents = toleranceCents,
            manualIndex = manualStringIndex.takeIf { it >= 0 },
            autoDetect = autoDetectString,
        )
    }
    return Reading(
        frequencyHz = resolved.frequencyHz,
        targetMidi = resolved.targetMidi,
        cents = resolved.cents,
        clarity = resolved.clarity,
        levelDbfs = resolved.levelDbfs,
        stringIndex = resolved.stringIndex ?: -1,
        inTune = resolved.inTune,
    )
}

// Equal-temperament note maths, with A4 wherever the user has put it.
//
// Flat top-level functions rather than an object, because Kotlin exports an
// object as a `getInstance()` factory and the extra hop reads badly from
// JavaScript. Constants are not exported at all: a top-level `val` arrives in
// JavaScript as `{ get(): number }` rather than a number, so `x === core.limit`
// silently compares a number with an object and is quietly always false. The
// limits go through `defaultsJson()` instead, where they are plain numbers.

/** Full scientific name, e.g. "A4" or "Eb3". */
fun noteName(midi: Int, useFlats: Boolean): String = Notes.name(midi, useFlats)

/** Pitch-class name alone, e.g. "F#" or "Gb". */
fun notePitchClass(midi: Int, useFlats: Boolean): String = Notes.pitchClassName(midi, useFlats)

/** Scientific pitch octave; MIDI 60 is C4. */
fun noteOctave(midi: Int): Int = Notes.octaveOf(midi)

fun noteFrequency(midi: Int, a4Hz: Double): Double = Notes.frequencyOf(midi, a4Hz)

/** Signed cents between [frequencyHz] and the exact pitch of [midi]. */
fun noteCentsFrom(frequencyHz: Double, midi: Int, a4Hz: Double): Double =
    Notes.centsBetween(frequencyHz, midi, a4Hz)

/** Parses "A4", "F#2", "Eb3"; returns -1 when the name makes no sense. */
fun parseNote(name: String): Int = Notes.parse(name) ?: -1

// ---- The catalog and the settings schema ---------------------------------

/**
 * The built-in tunings, as JSON: an array of
 * `{ id, name, family, strings, isCustom }`.
 */
fun presetsJson(): String = TuningCatalog.presets.jsonArray { tuning ->
    field("id", tuning.id).append(',')
    field("name", tuning.name).append(',')
    field("family", tuning.family.name).append(',')
    field("strings", tuning.strings).append(',')
    field("isCustom", false)
}

/** The instrument families, in the order the library shows them. */
fun familiesJson(): String = InstrumentFamily.entries.jsonArray { family ->
    field("name", family.name).append(',')
    field("displayName", family.displayName).append(',')
    field("defaultStringCount", family.defaultStringCount).append(',')
    field("seedStrings", family.seedStrings)
}

/** The display styles, with the copy the settings screen shows for each. */
fun displayStylesJson(): String = DisplayStyle.entries.jsonArray { style ->
    field("name", style.name).append(',')
    field("displayName", style.displayName).append(',')
    field("description", style.description)
}

/** Default settings and the ranges they are allowed to move in. */
fun defaultsJson(): String {
    val defaults = UserSettings()
    return buildString {
        append('{')
        field("referencePitchHz", defaults.referencePitchHz).append(',')
        field("useFlats", defaults.useFlats).append(',')
        field("autoDetectString", defaults.autoDetectString).append(',')
        field("keepScreenOn", defaults.keepScreenOn).append(',')
        field("themeMode", defaults.themeMode.name).append(',')
        field("toleranceCents", defaults.toleranceCents).append(',')
        field("displayStyle", defaults.displayStyle.name).append(',')
        field("minReferencePitchHz", UserSettings.REFERENCE_PITCH_RANGE.start).append(',')
        field("maxReferencePitchHz", UserSettings.REFERENCE_PITCH_RANGE.endInclusive).append(',')
        field("minToleranceCents", UserSettings.TOLERANCE_RANGE.first).append(',')
        field("maxToleranceCents", UserSettings.TOLERANCE_RANGE.last).append(',')
        field("minMidi", Notes.MIN_MIDI).append(',')
        field("maxMidi", Notes.MAX_MIDI).append(',')
        field("defaultTuningId", TuningCatalog.default.id)
        append('}')
    }
}
