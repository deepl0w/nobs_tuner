# StringTune

A chromatic and preset-based tuner for string instruments, built for Android with
Kotlin and Jetpack Compose. Everything happens on the device: no network access,
no analytics, no recording.

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

## Building

Requires JDK 17+ and the Android SDK. Everything else comes from the wrapper.

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintRelease          # lint
./gradlew :app:bundleRelease        # Play Store AAB
```

Point the build at your SDK with a `local.properties` containing
`sdk.dir=/path/to/Android/Sdk`, or set `ANDROID_HOME`.

## Tests

`./gradlew :app:testDebugUnitTest` runs the JVM suite: note maths, the tuning
catalog, the FFT against a naive DFT, and the pitch detector against synthesised
tones (clean, harmonically rich, missing-fundamental, noisy, DC-offset).

There is also a regression suite that runs real instrument recordings — guitar,
cello and violin chromatic runs — through the same frame size, hop, filter,
detector and smoother the live microphone uses, and checks that the notes come
out in the right order at a consistent pitch offset. Those recordings are tens of
megabytes, so they are not in version control:

```bash
tools/fetch-test-audio.sh     # needs curl and ffmpeg
./gradlew :app:testDebugUnitTest --tests '*RealRecording*'
```

Without them those tests skip and the rest of the suite still runs.

On-device tests cover the part that only exists on a device — opening the
microphone, keeping up with the stream, and releasing it cleanly:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Publishing

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for signing, the release checklist
and the Data safety answers. **Before your first upload, change `applicationId`
in `app/build.gradle.kts`** — `io.github.deeplow.stringtune` is a placeholder and
the ID is permanent once published.

## Licence

The code in this repository is yours to licence as you wish. The test recordings
downloaded by `tools/fetch-test-audio.sh` belong to the University of Iowa
Electronic Music Studios and are not redistributed here.
