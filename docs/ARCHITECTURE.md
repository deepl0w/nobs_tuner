# Architecture

How Nobs Tuner is put together. This describes the shape the code is in; the
*decisions*, with the arguments and the measurements behind them, live in
[docs/adr](adr/) and are append-only. Where a decision is recorded there, this
document links to it rather than restating the case — if the two ever disagree,
the ADR is right.

For what the app does, see the [README](../README.md).

## Two artefacts, one tuner

There is an Android app and a web app, and they are the *same tuner*: the pitch
detection, the note maths and the sixty-one presets are one body of Kotlin,
compiled twice ([0010](adr/0010-one-tuner-core-two-platforms.md)). Only the
microphone and the user interface are written per platform.

```
      :core (Kotlin Multiplatform)              →  jvm   →  :app  (Android)
      audio/ model/ data/UserSettings           →  js    →  web/  (PWA)
```

Everything shared is in `core/src/commonMain/`, under the package names it has
always had, so `import io.github.deeplow.nobstuner.audio.PitchDetector` means
the same thing in both. An Android import cannot compile there, which is what
now enforces the boundary the records rely on.

## The shape of it

Four layers. Dependencies only ever point downwards — nothing in `model/` or
`audio/` knows that Compose or Android exist, and only `AudioEngine` and
`DataStoreTunerRepository` touch the platform at all.

```
          ┌───────────────────────────────────────────────┐
  ui/     │  NobsTunerApp ── screens ── components        │  Compose
          │        │                                      │  :app
          │  TunerViewModel  LibraryViewModel  SettingsVM  │
          └────────┬───────────────────┬──────────────────┘
                   │                   │
          ┌────────▼────────┐ ┌────────▼──────────────────┐
  audio/  │  PitchSource    │ │  TunerRepository          │  data/
          │   └ AudioEngine │ │   └ DataStoreTunerRepo    │  :app
          ╞═════════════════╪═╪═══════════════════════════╡
          │  PitchDetector  │ │  UserSettings             │  :core
          │  PitchSmoother  │ └────────┬──────────────────┘
          │  Fft, HighPass  │          │
          └────────┬────────┘          │
                   │                   │
          ┌────────▼───────────────────▼──────────────────┐
  model/  │  Notes   Tuning   TuningCatalog               │  pure Kotlin
          │  PitchTargeting                               │  :core
          └───────────────────────────────────────────────┘
```

The double line is the module boundary. Above it is Android; below it is shared
with the browser, where `main.js` and `meters.js` sit where the view models and
Compose do, and an `AudioWorklet` sits where `AudioEngine` does.

`model/` is plain Kotlin: note maths, the tuning types, the preset catalog, and
the rule that decides which note the tuner aims at. `audio/` is the signal
chain. `data/` is persistence — the interface and its DataStore implementation
are Android, while `UserSettings` itself is shared. `ui/` is Compose plus the
three view models.

## The path a note takes

```
microphone
   │  8192-sample window, 2048-sample hop (~21 readings/second)
   ▼
HighPassFilter      25 Hz corner — handling noise and room rumble
   ▼
PitchDetector       YIN via FFT        → PitchEstimate(hz?, clarity, level)
   ▼
PitchSmoother       gate, median, ease → TrackedPitch(hz, clarity, level)
   ▼
PitchTargeting      which string? how far off?  → TuningTarget
   ▼
TunerUiState        what the screen draws
```

The first three stages are pure functions of their input plus their own stream
state, and none of them import anything from Android. That is the whole reason
the suite can check the hard part on the JVM — 146 unit tests, no device — and
it is a decision in its own right:
[ADR 0008](adr/0008-verify-pitch-tracking-off-device.md). The frame size and hop
are [ADR 0004](adr/0004-8192-sample-frames-at-44-1-khz.md); the detector itself
is [ADR 0001](adr/0001-yin-with-an-fft-difference-function.md) and the gating
[0002](adr/0002-gate-notes-on-ratios-not-absolute-levels.md) and
[0003](adr/0003-a-fading-note-is-one-still-falling.md).

## Decisions worth knowing

**Tunings are MIDI numbers, not frequencies.** `Tuning.strings` holds integers
where 69 is A4. Changing the reference pitch retargets all 60-odd presets and
every custom tuning for free, and there are no stored frequencies to migrate
when someone tunes to A=415. Everything that converts takes `a4Hz` as a
parameter; nothing caches a frequency.

**The microphone is a cold `Flow`.** Collecting `PitchSource.pitchEstimates()`
opens the recorder, cancelling the collection closes it, and nothing is retained
in between. `TunerScreen` ties that to `LifecycleResumeEffect`, so the mic is
held exactly while the tuner is in front of the user. There is no `stop()` to
forget to call.

**One repository, several view models.** `TunerRepository` is the single source
of truth for settings, custom tunings, favourites, the selected tuning and
chromatic mode. The three view models read and write only through it, which is
why the library screen and the tuner screen can never disagree about which
tuning is selected — there is only one copy of that fact.

**Custom tunings are one JSON blob, not a database**, in a single DataStore
preference key — [ADR 0005](adr/0005-datastore-and-json-not-room.md).

**Preset ids are permanent.** `TuningCatalog` ids end up persisted in the
favourites set and as the selected tuning, so renaming one orphans a user's
data. Change `Tuning.name` instead; the id is not shown anywhere.

**Aiming is a pure function.** `PitchTargeting` decides which note a reading is
measured against — the pinned string, else the nearest string, else, when
automatic detection is switched off, the first one. It takes its inputs as
arguments and returns a value, so the rule that governs what the needle points
at can be read in one place and tested directly, rather than being reachable
only by running a view model.

## View models

Three, split by what they are *for* rather than by which screen shows them:

| | owns | used by |
|---|---|---|
| `TunerViewModel` | the listening session, the live reading, tuned-string ticks | tuner screen |
| `LibraryViewModel` | browsing presets, favourites, custom-tuning CRUD | library, editor |
| `SettingsViewModel` | everything in Settings, plus theme and keep-awake | whole app, settings |

They share nothing but the repository. `SettingsViewModel` is held at the top of
the UI because the theme and the keep-awake flag apply app-wide and must outlive
a visit to the settings screen.

### Screens take values, not view models

`NobsTunerApp` obtains all three view models and passes plain state and
callbacks down. No screen references a view model, so each one reads as a
function of its arguments and can be previewed or driven from a test without a
container behind it.

### Flow lifetime means what it says

Every derived flow is shared with `WhileSubscribed(5_000)`: it survives a
rotation and then stops. For that to be true, **nothing inside a view model may
collect its own output** — one permanent internal subscriber would hold the
whole graph open for the view model's lifetime and silently defeat the setting
on every flow upstream of it.

So state that accumulates is folded inside the graph instead of by a collector.
`tunedStrings` restarts a `scan` whenever the target changes, which both
accumulates the ticks and clears them, with no `init` block watching for the
change. `TunerViewModelTest` pins this down: it asserts that with no screen
observing, `startListening()` opens nothing.

The same idea removes the other piece of cross-cutting bookkeeping. A string the
player pins is stored *with the target it was chosen for*, so it stops applying
by itself when the tuning changes rather than needing something to notice and
clear it.

## Dependencies and seams

`AppContainer` is the composition root — the one place that decides which
concrete implementations exist
([ADR 0009](adr/0009-interfaces-for-the-seams-that-tests-need.md)). It is
hand-written: the graph is two objects deep and does not branch, so a DI
framework would cost more to read than it saves. What matters is not the
mechanism but that construction happens there and not inside the view models.

Two interfaces exist purely as seams:

- **`PitchSource`** — `AudioEngine` is the microphone; `FakePitchSource` is a
  flow the test drives. An emulator cannot be fed host audio, so without this
  seam the listening state machine could not be exercised off-device at all.
- **`TunerRepository`** — `DataStoreTunerRepository` is the real one;
  `FakeTunerRepository` is in-memory, and because its writes land in the flows
  the view models read, a test can assert on what a user action persisted.

## Testing

| layer | how it is tested |
|---|---|
| `model/` | directly — pure functions, no fixtures. **JVM and JavaScript** |
| `audio/` | synthesised tones, plus real recordings through the full chain. **JVM and JavaScript**, except the recordings |
| `ui/` view models | fakes for both seams, virtual clock via `runTest` |
| `ui/` screens | layout arithmetic extracted and tested (`BalancedRowsTest`) |
| device | `AudioEngineInstrumentedTest`, `AudioSourceProbeTest` |

Everything in `core/src/commonTest/` runs twice — once against the JVM
compilation the Android app uses and once, on Node, against the JavaScript the
browser loads. That is what stops the two platforms drifting apart
([0010](adr/0010-one-tuner-core-two-platforms.md)): a detector that behaved
differently in a browser would turn CI red rather than turn up on someone's
violin. `./gradlew :core:jvmTest` and `:core:jsNodeTest` run the two halves;
`./test.sh` runs the JVM one alongside the app's.

The recording tests are JVM-only, because they decode WAV files with
`javax.sound`, and they skip unless the fixtures are present; `./test.sh --audio`
fetches them. Why that suite exists in the form it does is
[ADR 0008](adr/0008-verify-pitch-tracking-off-device.md). Instrumented tests
cover what only a real device can answer — that the recorder opens, and which
audio source the hardware actually gives us.
They deliberately do not test pitch accuracy, because an emulator's microphone
cannot be fed a known signal; that is what the recording tests are for.

## Where things live

```
core/src/
├── commonMain/kotlin/io/github/deeplow/nobstuner/
│   ├── audio/                   PitchSource, detector, smoother, FFT, filter, Analysis
│   ├── model/                   Notes, Tuning, TuningCatalog, PitchTargeting
│   └── data/UserSettings.kt     the settings type, without the storage
├── commonTest/                  the pitch suite — runs on the JVM *and* on Node
├── jvmTest/                     RealRecordingPitchTest, which decodes WAV files
└── jsMain/kotlin/.../js/        the @JsExport facade the web app imports

app/src/main/java/io/github/deeplow/nobstuner/
├── AppContainer.kt              composition root
├── NobsTunerApplication.kt      owns the container
├── MainActivity.kt              sets the Compose content
├── audio/                       AudioEngine and MicrophonePitchSource — the Android half
├── data/                        TunerRepository + its DataStore implementation
└── ui/
    ├── NobsTunerApp.kt          navigation and view-model wiring
    ├── *ViewModel.kt            tuner, library, settings
    ├── screens/                 tuner, library, editor, settings
    ├── components/              meters, readout, string selector, responsive helpers
    └── theme/

web/                             the PWA; see web/README.md
├── index.html styles.css sw.js manifest.webmanifest
├── src/                         main.js, audio.js, meters.js, store.js, views/
└── vendor/                      the compiled core — generated, git-ignored
```

## If you are adding something

Ask first whether it belongs to one platform or to both. Anything about *pitch*
— what a note is, which string is meant, how a reading settles — belongs in
`:core`, where it is written once and tested on both compilations. Anything
about how it looks or where it is stored belongs to a platform.

- **A new tuning preset** — add it to `TuningCatalog.presets` with an id that
  will never change. Both apps pick it up; the web app reads the same list
  through `presetsJson()`.
- **A new display style** — add to the `DisplayStyle` enum in `:core`, which
  carries its own name and description so both settings screens list it. Then
  draw it twice: `MeterStyles.kt` for Compose and `web/src/meters.js` for
  canvas, adding its aspect ratio to `meterWidthFor` in both. This is the one
  place a deliberate duplicate lives, because a dial is a drawing and there is
  no shared canvas to draw it on.
- **A new setting** — add the field to `UserSettings` in `:core`, then a key and
  its read/write in `DataStoreTunerRepository`, the signature on
  `TunerRepository`, a setter on `SettingsViewModel` and a row in
  `SettingsScreen`; on the web, a default in `defaultsJson()`, validation in
  `store.js` and a row in `views/settings.js`. Then make sure something actually
  reads it: a setting that is persisted and displayed but never consulted looks
  exactly like a working one.
- **Anything touching what the needle points at** — it belongs in
  `PitchTargeting`, where it can be tested without a view model.
