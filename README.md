# Nobs Tuner

A chromatic and preset-based tuner for string instruments. Everything happens on
your device: no analytics, no recording, nothing sent anywhere.

It comes in two forms that are the same tuner rather than two tuners — an
**Android app** in Kotlin and Jetpack Compose, and a **web app** you can open in
a browser and install to a home screen. The pitch detection, the note maths and
the whole tuning catalog are one body of code compiled for both, and the same
test suite runs against both compilations.

![guitar, bass, ukulele, banjo, mandolin, violin, cello and more](docs/play-assets/play-icon-512.png)

## What it does

- **60+ built-in tunings** across guitar (including 7- and 8-string), bass,
  ukulele, banjo, mandolin, orchestral strings, and oddities like Irish bouzouki,
  resonator open G, lap steel C6 and cigar-box three-strings.
- **Custom tunings** — any number of strings from 1 to 12, any note from C0 to
  C8, re-entrant orders allowed. Saved on the device and editable afterwards.
- **Favourites** — star any preset or custom tuning to pull it to the top of the
  library.
- **Chromatic mode** — tune anything to the nearest semitone, no preset needed.
- **Automatic string detection**, or tap a string to lock the tuner onto it.
- **Adjustable reference pitch** from 415 Hz to 466 Hz, for ensembles that do not
  sit at A440.
- Sharps or flats, adjustable in-tune tolerance, light/dark/system theme.

## How the tuning works

Pitch detection is the YIN algorithm (de Cheveigné & Kawahara, 2002), with the
difference function computed through an FFT so a frame costs O(N log N) rather
than O(N²). On top of the textbook algorithm:

- **An octave correction.** YIN takes the first lag that dips below its
  threshold, which on a string with a weak fundamental can be half the true
  period — the note then reads an octave high. The correction compares the
  candidate against its multiples and moves down when the longer period explains
  the waveform substantially better. The thresholds come from measurements on
  real recordings, not from guesswork; see `PitchDetector.preferTrueFundamental`.
- **A decay-aware tracker.** As a plucked or bowed note dies away its fundamental
  fades before its partials, and a naive tracker jumps an octave just as the
  player is finishing an adjustment. `PitchSmoother` folds those jumps back while
  the level is falling, and believes them when a genuinely new note arrives.
- **A 25 Hz high-pass** on the microphone feed, to keep handling noise out of the
  level gate without touching the lowest note the app supports (B0, 30.87 Hz).

Analysis runs on 8192-sample frames at 44.1 kHz with a 2048-sample hop — about 21
readings a second, with enough window to resolve a low B on a five-string bass.

## The two apps

| | Android | Web |
|---|---|---|
| Built from | `app/` — Compose | `web/` — plain HTML, CSS and ES modules |
| Shares | `core/` — pitch, note maths, catalog | the same `core/`, compiled to JavaScript |
| Microphone | `AudioRecord`, asking for an unprocessed input | `getUserMedia` into an `AudioWorklet` |
| Stores settings in | DataStore | the browser's local storage |
| Network | no permission at all | a static page; nothing reaches another origin |
| Offline | always | after the first load, via a service worker |

The web app has its own [README](web/README.md). The privacy position differs
slightly between the two, and
[PRIVACY_POLICY.md](PRIVACY_POLICY.md) answers for each separately rather than
blurring them.

## Building

Requires JDK 17+ and the Android SDK. Everything else comes from the wrapper.
Point the build at your SDK with a `local.properties` containing
`sdk.dir=/path/to/Android/Sdk`, or set `ANDROID_HOME`.

Building or testing the shared core's JavaScript side also needs **Node and
Yarn** on your `PATH`. The Android app alone does not.

Check the machine is ready, then build:

```bash
./test.sh --check     # Java, SDK, wrapper, adb, Gradle config
./build.sh            # debug APK
./build.sh --run      # build, install and launch
./build.sh --bundle   # the .aab to upload to Play
```

For the web app:

```bash
./gradlew :core:syncWebCore               # compile the core into web/vendor/
python3 -m http.server 8000 --directory web
```

Then open `http://localhost:8000`. It has to be served over HTTP rather than
opened as a file — the microphone needs a secure origin, and localhost counts.

`make` wraps the same things — `make help` lists every target:

| Command | Does |
|---|---|
| `make build` / `make run` | Debug APK; or build, install and launch |
| `make test` | JVM unit tests, shared core and app |
| `make test-web` | The same pitch suite against the JavaScript build |
| `make web` | Compile the core and serve the web app on :8000 |
| `make test-audio` | Fetch the instrument recordings, then run the unit tests |
| `make device-test` | Instrumented tests on a connected device |
| `make lint` / `make verify` | Lint; or unit tests + lint + instrumented |
| `make deploy` / `make logs` | Install and follow logs; or just follow logs |
| `make release` / `make bundle` | Release APK; or the Play Store bundle |
| `make clean` / `make info` | Clean; or print toolchain, packages and paths |

The scripts read `applicationId` and `namespace` out of `app/build.gradle.kts`
rather than hardcoding them, so renaming the application id before publishing
does not quietly break them. With more than one device attached they stop and
ask which, instead of picking for you — pass `--device <serial>` (`--serial` for
`test.sh`). `deploy.sh` installs over the top so saved tunings survive; use
`--fresh` to wipe.

Under the hood these are ordinary Gradle tasks, if you would rather call them
directly:

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :core:jvmTest             # shared pitch and catalog tests
./gradlew :core:jsNodeTest          # the same, against the JavaScript build
./gradlew :app:testDebugUnitTest    # Android-side unit tests
./gradlew :core:syncWebCore         # compile the core into web/vendor/
./gradlew :app:lintRelease          # lint
./gradlew :app:bundleRelease        # Play Store AAB
```

## Tests

`./gradlew :core:jvmTest :app:testDebugUnitTest` runs the JVM suite: note maths,
the tuning catalog, the FFT against a naive DFT, the pitch detector against
synthesised tones (clean, harmonically rich, missing-fundamental, noisy,
DC-offset), and the view models against fakes.

Most of that lives in the shared core, so it also runs against the JavaScript
the browser loads:

```bash
./gradlew :core:jsNodeTest    # the same pitch suite, on Node
```

That is not belt and braces. It is the thing that stops the web tuner and the
Android tuner quietly disagreeing about what note is being played — a
disagreement that would otherwise surface on someone's instrument rather than in
CI.

There is also a regression suite that runs real instrument recordings — guitar,
cello and violin chromatic runs — through the same frame size, hop, filter,
detector and smoother the live microphone uses, and checks that the notes come
out in the right order at a consistent pitch offset. Those recordings are tens of
megabytes, so they are not in version control:

```bash
tools/fetch-test-audio.sh     # needs curl and ffmpeg
./gradlew :core:jvmTest --tests '*RealRecording*'
```

Without them those tests skip and the rest of the suite still runs.

On-device tests cover the part that only exists on a device — opening the
microphone, keeping up with the stream, and releasing it cleanly:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## How it is put together

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the layers, the path a note
takes from the microphone to the needle, and the decisions behind both.

## Publishing

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for signing, the release checklist
and the Data safety answers. **Before your first upload, change `applicationId`
in `app/build.gradle.kts`** — `io.github.deeplow.nobstuner` is a placeholder and
the ID is permanent once published.

## Licence

[MIT](LICENSE) — © 2026 Dennis Plosceanu.

The instrument recordings fetched by `tools/fetch-test-audio.sh` are **not**
covered by it. They belong to the University of Iowa Electronic Music Studios,
are downloaded on demand, and are not redistributed in this repository.

## Architecture decisions

The decisions behind the pitch detection, the gating, the persistence and the
privacy posture are recorded in [docs/adr/](docs/adr/).
