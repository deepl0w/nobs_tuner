# ADR 0008 — Verify pitch tracking off-device, against real recordings

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

Every interesting defect this app has had was in pitch tracking, and none of
them were visible to a synthesised sine wave. The list is specific:

- a violin G♯3 read as G♯4, because its fundamental was weak;
- a cello C2 flicked to C3 as it decayed;
- a bow-stroke onset locked the display onto a partial that was never played;
- repeated plucking on a guitar worked once and then went deaf;
- a quiet string an octave above a loud one read an octave low.

Synthetic tones find none of these. They have no attack transient, no decay, no
room, and a fundamental that never weakens.

The obvious place to test the real thing is on a device, and that route is
closed: **the Android emulator does not pass host audio to the guest
microphone.** Verified on emulator 36.4.10 with the android-35 google_apis
image — a PipeWire null sink and a FIFO-backed source were both routed in with
`-allow-host-audio`, `pactl` confirmed qemu was reading from the virtual source,
and `tinycap` inside the guest returned the same fixed noise floor
(−77.1 dBFS, peak 344.5 Hz) whatever was played, including a full-scale 440 Hz
tone.

## Decision

Verify the pitch chain **off-device**, by pushing real instrument recordings
through the identical code path the microphone uses, and verify only the
recorder itself on-device.

[`RealRecordingPitchTest`](../../app/src/test/java/io/github/deeplow/nobstuner/audio/RealRecordingPitchTest.kt)
streams whole recordings through `HighPassFilter`, `PitchDetector` and
`PitchSmoother` at `AudioEngine.FRAME_SIZE` and `AudioEngine.HOP_SIZE` —
referencing those constants, not copies of them, so the test cannot drift from
the engine. This is possible because `AudioEngine` is the only file under
`audio/` or `model/` that imports `android.*`; everything downstream is plain
Kotlin.

The recordings are chromatic runs on one string, from the University of Iowa
Electronic Music Studios: guitar E2–B2, cello C2–B2, violin G3–B3. They are tens
of megabytes, so they are **not in version control** —
[`tools/fetch-test-audio.sh`](../../tools/fetch-test-audio.sh) downloads and
converts them, and the tests skip without them.

What the test asserts matters as much as that it runs. A recorded instrument is
not at A440 — the Iowa guitar is **consistently about 35 cents flat across every
note**, measured at F♯2 −37.9, G2 −40.8, A2 −35.8, B2 −30.3 cents. So the
assertion is not per-note accuracy against concert pitch; it is that every note
is off by the *same* amount. The statistic is the median absolute deviation from
the run's own median offset, which a detector that drifted with pitch, or that
snapped to the nearest semitone, would fail.

On-device,
[`AudioEngineInstrumentedTest`](../../app/src/androidTest/java/io/github/deeplow/nobstuner/audio/AudioEngineInstrumentedTest.kt)
covers what only exists on a device: that the recorder opens with the preferred
source, keeps up with the stream, and releases cleanly so it can be reopened.

## Consequences

Four of the five defects above were found by this suite rather than by a user,
and each is now pinned by a test that fails if it returns.

The suite is fast and runs in CI like any unit test — no device, no emulator, no
audio routing.

Because the test shares the engine's constants, changing `FRAME_SIZE` or
`HOP_SIZE` re-runs the whole corpus at the new setting automatically.

### What this costs

**The tuner's own microphone path is not covered end to end by any automated
test.** The instrumented test proves frames arrive; nothing automated proves the
right note appears on screen. That gap is closed only by picking up a phone, and
it is where the repeated-plucking defect lived for an entire session.

The corpus is three instruments and one string each. Nothing covers a ukulele, a
banjo's re-entrant fifth, a twelve-string's pairs, or any electric instrument.
A constant tuned to pass these three can still be wrong for those.

Fixtures are not in version control, so a clean checkout has weaker coverage
than it appears to: the tests skip rather than fail, and a CI run with no
network silently proves less. The run reports skips, which is the only signal.

The Iowa recordings are studio captures. Their noise floor is nothing like a
phone in a room, which is exactly why the −48 dBFS gate from
[0002](0002-gate-notes-on-ratios-not-absolute-levels.md) passed every test and
failed every real device.

## Revisit when

- **A defect is reported that the suite does not reproduce.** Add a recording of
  it before fixing, and prefer a real one — the reason this record exists is
  that synthesised audio kept agreeing with the code.
- **The emulator gains working microphone injection**, or testing moves to a
  device farm. End-to-end coverage of the microphone path becomes possible and
  the largest gap above closes.
- **An instrument family is added to the catalog** that nothing in the corpus
  resembles — the first electric or re-entrant instrument is worth a recording.
