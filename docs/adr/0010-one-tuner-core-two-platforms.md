# ADR 0010 — One tuner core, compiled for two platforms

- **Status:** Accepted
- **Date:** 2026-10-03

Extends [0008](0008-verify-pitch-tracking-off-device.md), whose "everything
downstream of the microphone is plain Kotlin" is what made this possible, and
carries the seam from [0009](0009-interfaces-for-the-seams-that-tests-need.md)
across the platform boundary. Enables [0011](0011-a-web-tuner-alongside-the-app.md).

**Narrows [0004](0004-8192-sample-frames-at-44-1-khz.md)**, whose sample rate was
chosen on the grounds that 44.1 kHz is the one rate every Android device
supports. That argument does not reach a browser, which does not offer the
choice at all — see *The rate we do not choose* below.

## Context

A browser tuner ([0011](0011-a-web-tuner-alongside-the-app.md)) needs the same
pitch detection, the same note maths and the same sixty-one tunings as the
Android app. There were two ways to get them there: reimplement the algorithm in
TypeScript, or compile the Kotlin.

Reimplementing is the cheaper build and the more expensive decade. The pitch
chain is not generic signal processing that two authors would write the same
way. Its behaviour sits in constants that were *measured* rather than derived,
and the records that explain them say so plainly:
[0002](0002-gate-notes-on-ratios-not-absolute-levels.md) tunes a noise-floor
percentile and an SNR margin against real plucks,
[0003](0003-a-fading-note-is-one-still-falling.md) turns on a decay test worth
exactly 12 dB, and `PitchDetector.preferTrueFundamental` switches octave on a
ratio of 0.5 above a floor of 0.02 because correct detections measured at or
below 0.04 and genuine octave errors at 0.09 and above.

A port would start identical and drift. Worse, it would drift *silently*: the
failure mode is not a crash but a violin reading an octave high on one platform
and not the other, which is precisely the class of defect 0008 exists because
synthesised audio would not catch.

## Decision

One implementation, in a Kotlin Multiplatform module at `core/`, compiled for
both the JVM and JavaScript.

`core/src/commonMain/` holds everything that is not a user interface and not a
device: `Fft`, `HighPassFilter`, `PitchDetector`, `PitchSmoother`, `Analysis`,
`Notes`, `Tuning`, `TuningCatalog`, `PitchTargeting`, `UserSettings` and the
`PitchSource` interface. **Package names are unchanged** — `...nobstuner.audio`,
`...nobstuner.model` — so not one import in the app module moved.

The targets are `jvm()` and `js(IR)`, not `androidTarget()`. Nothing in the
module touches Android, and Kotlin's platform-type compatibility lets an Android
app consume a `jvm` variant directly, so the module stays free of the Android
Gradle Plugin and of the version coupling that comes with it.

`AudioEngine` stays in the app module. It is the one file that imports
`android.*`, which is the boundary 0008 depends on and 0009 formalised.

**The shared tests are the enforcement.** The pitch suite moved to
`commonTest` and runs against both compilations: 93 of the same tests execute on
the JavaScript build in CI. The FFT is still checked against a naive DFT, the
detector against missing-fundamental and noisy tones, the smoother against decay
and repeated plucking — on both platforms, from one source. A divergence between
the two is not something a reviewer has to notice; it is a red build.

The recording-based suite from 0008 stays JVM-only, in `core/src/jvmTest/`,
because it decodes WAV files with `javax.sound`.

**On 0008's single-declaration rule.** That record requires the recording tests
to reference the engine's frame constants rather than copies, so the test cannot
drift from the engine. The property is preserved, but the mechanism has
**inverted**, and a reader of 0008 needs to know that before going looking.

0008 has the test referencing the engine. That is no longer possible in either
direction it describes: `core/src/jvmTest/` cannot depend on `app`, so the test
cannot see `AudioEngine` at all. Instead both now reference a third thing —
`Analysis.FRAME_SIZE` and `Analysis.HOP_SIZE` in `commonMain` are the single
declaration, and `AudioEngine.FRAME_SIZE` is defined *as* `Analysis.FRAME_SIZE`.
The guarantee is the same and slightly stronger, because the web pipeline reads
that declaration too, but **`AudioEngine` is now an alias and not the source of
truth**, and anyone following 0008's wording to it will land in the wrong file.

**On the source links in 0001, 0002, 0003 and 0008.** This move left five of
them pointing at `app/src/.../audio/`, which no longer exists. Their link
*targets* have been repointed at the files' new homes — and nothing else in those
records was touched, which the diff shows: one path string each.

That is a judgement about what "append-only" protects. The rule exists so a
record's reasoning cannot be quietly revised after the fact, and a path is not
reasoning: it is a pointer to code that was always free to move. Leaving five
404s in the log to honour a rule about arguments would make the log less useful
without making it more honest. If the fleet reads the rule more strictly than
that, reverting the commit that did it restores the broken links exactly.

`.github/workflows/android.yml` now fails the build on a broken relative link in
any Markdown file, so the next move that does this says so at the time rather
than leaving it for a reader to discover.

### Crossing into JavaScript

The `@JsExport` surface lives in `core/src/jsMain/`, not in `commonMain`. Kotlin
cannot export its collection types, and putting the facade in common would let
JavaScript's export rules reshape code that Android also has to live with. The
facade is a translation layer, and it is the only thing that knows JavaScript
exists.

The catalog crosses as JSON. It is built by hand rather than with
kotlinx.serialization because pulling that library into the browser bundle to
emit four fixed strings at start-up cost over 200 KB — most of the download, for
something a dozen lines of string building do.

## The rate we do not choose

[0004](0004-8192-sample-frames-at-44-1-khz.md) fixes `FRAME_SIZE` at 8192,
`HOP_SIZE` at 2048 and the sample rate at 44 100, the last because it is the one
rate every Android device supports. A browser does not take requests: it hands
over whatever `AudioContext.sampleRate` says its hardware runs at — 48 kHz on
most machines — and asking for anything else makes it resample, which is a worse
starting point than analysing what the hardware actually produced.

Frame and hop stay fixed in **samples**, so at 48 kHz every window counted in
frames is 8.13% shorter in **time**:

| | 44.1 kHz | 48 kHz |
|---|---|---|
| Hop | 46.44 ms | 42.67 ms |
| Integration window | 92.88 ms | 85.33 ms |
| …as periods of B0 (30.87 Hz) | 2.87 | 2.63 |
| Onset agreement (3 frames) | 139 ms | 128 ms |
| Level trend (9 frames) | 418 ms | 384 ms |
| Release (10 frames) | 464 ms | 427 ms |
| Octave-fold limit (12 frames) | 557 ms | 512 ms |
| Noise-floor window (110 frames) | 5.11 s | 4.69 s |

**The frequency-domain requirement still holds, and is now checked.** 0004 asks
for an integration window comfortably longer than one period of the lowest note;
2.63 periods of B0 clears that. `PitchDetectorTest.detects the same notes at the
rate a browser runs at` runs the detector at 48 kHz across the range from B0 to
E5, and B0 — the worst case — comes back 0.0007 cents off. That test runs on
both compilations, so the claim is not platform hearsay.

**The time-domain constants are a different matter, and this is the honest
part.** The thresholds in [0002](0002-gate-notes-on-ratios-not-absolute-levels.md)
and [0003](0003-a-fading-note-is-one-still-falling.md) are counted in frames but
*justified* in units of time — how long to wait before believing a new note, how
long a level has to keep falling before a note counts as dying. They were
measured at 44.1 kHz. Nobody has measured them at 48. The direction of the shift
is the reassuring one — every window gets shorter, so the web tuner is quicker to
believe a new note and quicker to let go of a dead one, rather than slower — but
"the direction looks safe" is an argument, not a measurement.

It is compounded by where the measurements come from.
`Analysis.PREFERRED_SAMPLE_RATE` is also the rate `tools/fetch-test-audio.sh`
resamples the fixtures to, so [0008](0008-verify-pitch-tracking-off-device.md)'s
recording suite — the one that found four of the five defects that record lists —
verifies the chain at precisely the rate the web path never runs at. Synthesised
tones at 48 kHz are covered; real instruments at 48 kHz are not.

This is recorded as a known gap rather than fixed, because closing it means
either resampling the corpus twice, which doubles a multi-gigabyte download, or
accepting that a resampled 48 kHz fixture is not the same evidence as a 48 kHz
recording. Neither is obviously right, and neither should be decided in passing.

## Consequences

The algorithm cannot diverge, because there is only one of it. A constant tuned
on a recording is tuned for both platforms at once, and the ADRs that explain
those constants stay true of the web app without being restated.

The tuning catalog has one definition. Adding a preset is one edit, and both
artefacts get it with no chance of a transposition slipping into one of them.

`PitchTargeting` demonstrated the point before this record was written. The
web app briefly had a second resolver, written in parallel, which did not honour
`autoDetectString`; it was deleted in favour of the shared one.

### What this costs

**A JavaScript toolchain in an Android build.** `:core:jsNodeTest` needs Node
and Yarn. They are taken from the `PATH` rather than downloaded, because
`settings.gradle.kts` refuses project-declared repositories and the Kotlin plugin
adds its own to fetch them. That is a dependency the Android build never had, and
someone with neither installed gets a failure in a module they were not editing.
`./test.sh --check` reports both.

**The browser pays for the Kotlin standard library.** About 184 KB of it,
against the few kilobytes a hand-written TypeScript core would have cost. It
compresses to a fraction of that and the service worker caches it after the first
load, but it is real, and it is the price of the guarantee above.

**`web/vendor/` is build output.** It is git-ignored and produced by
`./gradlew :core:syncWebCore`. Editing the web app without running that leaves
you testing against a stale core, and nothing says so.

**Two modules share package names.** `io.github.deeplow.nobstuner.audio` exists
in both `core` and `app`. That is what spared every import, and it means "where
does this file live" now has two answers.

**The export boundary is weakly typed and the compiler will not help.** A
top-level `val` arrives in JavaScript as `{ get(): number }` rather than as a
number, so `midi > core.maxMidi` compared a number with an object and was
quietly always false. It was found by opening the page, not by any test or type
check. Constants now travel inside the JSON payload for that reason, and
anything new crossing the boundary deserves the same suspicion.

## Revisit when

- **The web app needs behaviour the Android app must not have**, or vice versa.
  A handful of `expect`/`actual` declarations is fine; a core full of platform
  branches means the two products have genuinely separated and should say so.
- **Bundle size becomes the binding constraint.** If the Kotlin runtime is what
  stands between the web tuner and being usable on a slow connection, a
  hand-written core is back on the table — but only with a shared corpus of
  golden vectors to pin it to this one.
- **A third platform appears.** iOS would be a `native` target and mostly free;
  that is the case where this decision pays for itself twice.
- **A pitch defect is reported from a browser and not from the Android app**, or
  the reverse. The first thing to suspect is the sample rate: that is the one
  axis on which the two genuinely differ, and the gap named above — real
  recordings verified only at 44.1 kHz — is where the evidence runs out.
- **Any of 0002's or 0003's frame counts are retuned.** They are measured in
  frames and argued in milliseconds, and there are now two rates at which a
  frame means something different. Retuning one without saying which rate it was
  measured at would leave the other platform carrying a number nobody checked.
