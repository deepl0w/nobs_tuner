# ADR 0010 — One tuner core, compiled for two platforms

- **Status:** Accepted
- **Date:** 2026-10-03

Extends [0008](0008-verify-pitch-tracking-off-device.md), whose "everything
downstream of the microphone is plain Kotlin" is what made this possible, and
carries the seam from [0009](0009-interfaces-for-the-seams-that-tests-need.md)
across the platform boundary. Enables [0011](0011-a-web-tuner-alongside-the-app.md).

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
drift from the engine. That property is preserved but has moved: the one
declaration is now `Analysis.FRAME_SIZE` / `Analysis.HOP_SIZE` in
`commonMain`, and `AudioEngine.FRAME_SIZE` is defined *as* `Analysis.FRAME_SIZE`.
Do not read `AudioEngine` as the source of truth — it is an alias, and the web
pipeline reads the same declaration, so 0004's frame geometry is now shared by
every platform rather than merely copied correctly.

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
