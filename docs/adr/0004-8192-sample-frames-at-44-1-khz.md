# ADR 0004 — 8192-sample frames, 2048 hop, 44.1 kHz

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

YIN finds a period by comparing a frame against itself at a lag. The integration
window therefore has to be long enough to contain the period being looked for,
and comfortably longer than one of them if the answer is to be stable.

The lowest note the app ships a tuning for is **B0 at 30.87 Hz** — the low
string of a five-string bass, and of a five-string double bass. At 44.1 kHz its
period is **1429 samples**. The highest is E5 at 659.26 Hz.

`PitchDetector` uses an integration window of half the frame, so the frame has
to be at least four times the lowest period for two periods to fit inside the
window.

44.1 kHz is not really a choice: it is the one sample rate every Android device
supports for capture.

## Decision

`FRAME_SIZE` = 8192, `HOP_SIZE` = 2048, `SAMPLE_RATE` = 44 100, in
[`AudioEngine`](../../app/src/main/java/io/github/deeplow/nobstuner/audio/AudioEngine.kt).

That gives a 4096-sample integration window, which holds 2.9 periods of B0, and
a new reading every 46 ms — about 21 a second.

4096 was tried and rejected: its 2048-sample window holds 1.4 periods of B0,
which is below the point where YIN is dependable down there.

## Consequences

The bottom of the range works. The detector resolves B0 to within 2 cents on a
synthesised tone, and the recordings suite exercises a cello C2 at 65 Hz through
the full chain.

The cost per frame is three 8192-point transforms — roughly 2 Mflops, about
43 Mflops/s at the hop rate, which is nothing on any phone that runs Android 7.

### What this costs

Each frame spans 186 ms of audio. A reading is therefore a statement about the
last fifth of a second, not about this instant, and a fast vibrato is averaged
rather than tracked. For a tuner that is the right trade; for anything wanting
expression it would not be.

The window is sized for the lowest note the catalog contains, and every
instrument pays for it — a ukulele tuner would be happy with a quarter of the
latency.

Memory is several 8192-element double arrays per detector, around 65 KB each.
Irrelevant here, but it is why one detector is built per capture session rather
than per frame.

## Revisit when

- **A tuning below B0 is added** — a contrabass, an octave bass, a drop-tuned
  seven-string below B0. `TuningCatalog` is the place that changes, but this is
  the number that has to change with it: check
  `period = 44100 / f` and keep `FRAME_SIZE ≥ 4 × period`.
- **Anyone asks for a faster needle.** The hop is already a quarter of the
  frame; the only real lever is the frame, and shortening it costs the bottom
  of the range.
