# Architecture

How Nobs Tuner is put together. This describes the shape the code is in; the
*decisions*, with the arguments and the measurements behind them, live in
[docs/adr](adr/) and are append-only. Where a decision is recorded there, this
document links to it rather than restating the case — if the two ever disagree,
the ADR is right.

For what the app does, see the [README](../README.md).

## The shape of it

Four layers. Dependencies only ever point downwards — nothing in `model/` or
`audio/` knows that Compose or Android exist, and only `AudioEngine` and
`DataStoreTunerRepository` touch the platform at all.

```
          ┌───────────────────────────────────────────────┐
  ui/     │  NobsTunerApp ── screens ── components        │  Compose
          │        │                                      │
          │  TunerViewModel  LibraryViewModel  SettingsVM  │
          └────────┬───────────────────┬──────────────────┘
                   │                   │
          ┌────────▼────────┐ ┌────────▼──────────────────┐
  audio/  │  PitchSource    │ │  TunerRepository          │  data/
          │   └ AudioEngine │ │   └ DataStoreTunerRepo    │
          │  PitchDetector  │ └────────┬──────────────────┘
          │  PitchSmoother  │          │
          │  Fft, HighPass  │          │
          └────────┬────────┘          │
                   │                   │
          ┌────────▼───────────────────▼──────────────────┐
  model/  │  Notes   Tuning   TuningCatalog               │  pure Kotlin
          │  PitchTargeting                               │
          └───────────────────────────────────────────────┘
```

`model/` is plain Kotlin: note maths, the tuning types, the preset catalog, and
the rule that decides which note the tuner aims at. `audio/` is the signal
chain. `data/` is persistence. `ui/` is Compose plus the three view models.

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
| `model/` | directly — pure functions, no fixtures |
| `audio/` | JVM tests, plus real instrument recordings through the full chain |
| `ui/` view models | fakes for both seams, virtual clock via `runTest` |
| `ui/` screens | layout arithmetic extracted and tested (`BalancedRowsTest`) |
| device | `AudioEngineInstrumentedTest`, `AudioSourceProbeTest` |

The recording tests skip unless the fixtures are present; `./test.sh --audio`
fetches them. Why that suite exists in the form it does is
[ADR 0008](adr/0008-verify-pitch-tracking-off-device.md). Instrumented tests
cover what only a real device can answer — that the recorder opens, and which
audio source the hardware actually gives us.
They deliberately do not test pitch accuracy, because an emulator's microphone
cannot be fed a known signal; that is what the recording tests are for.

## Where things live

```
app/src/main/java/io/github/deeplow/nobstuner/
├── AppContainer.kt              composition root
├── NobsTunerApplication.kt      owns the container
├── MainActivity.kt              sets the Compose content
├── audio/                       PitchSource, AudioEngine, detector, smoother, FFT, filter
├── data/                        TunerRepository + DataStore implementation, UserSettings
├── model/                       Notes, Tuning, TuningCatalog, PitchTargeting
└── ui/
    ├── NobsTunerApp.kt          navigation and view-model wiring
    ├── *ViewModel.kt            tuner, library, settings
    ├── screens/                 tuner, library, editor, settings
    ├── components/              meters, readout, string selector, responsive helpers
    └── theme/
```

## If you are adding something

- **A new tuning preset** — add it to `TuningCatalog.presets` with an id that
  will never change.
- **A new display style** — add to the `DisplayStyle` enum, draw it in
  `MeterStyles.kt`, add its aspect ratio to `meterWidthFor` in `TunerScreen.kt`.
  The enum carries its own name and description, so Settings picks it up.
- **A new setting** — add the field to `UserSettings`, a key and its read/write
  to `DataStoreTunerRepository`, the signature to `TunerRepository`, a setter to
  `SettingsViewModel`, and a row to `SettingsScreen`. Then make sure something
  actually reads it: a setting that is persisted and displayed but never
  consulted looks exactly like a working one.
- **Anything touching what the needle points at** — it belongs in
  `PitchTargeting`, where it can be tested without a view model.
