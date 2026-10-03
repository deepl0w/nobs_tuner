# ADR 0001 — Detect pitch with YIN, difference function via FFT

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

A tuner has to turn a few thousand microphone samples into one fundamental
frequency, accurately enough that a five-cent error is visible, and fast enough
to do it twenty times a second on a phone.

Picking the loudest peak of an FFT spectrum is the obvious approach and the
wrong one for strings. A plucked or bowed string often puts more energy into its
second or third partial than into its fundamental, and on a decaying note the
fundamental can vanish entirely — we measured a violin G♯3 whose fundamental sat
at 0.064 of the amplitude of its second partial, and a cello C2 whose strongest
partial during decay was its octave.

YIN works in the time domain on periodicity rather than on spectral energy, so a
weak fundamental does not mislead it. Its cost is the difference function, which
is O(W·τmax) evaluated naively — for our window that is 4096 × 4096 operations
per frame, far too slow to run at the hop rate on a phone.

## Decision

Use YIN, and compute its difference function through the FFT.

Expanding the square gives `d(τ) = Σx[j]² + Σx[j+τ]² − 2·Σx[j]·x[j+τ]`, where
the first term is constant, the second is a sliding window sum updated in O(1),
and the third is a cross-correlation obtained from one complex multiply between
two FFTs. That is three transforms per frame instead of a quadratic scan. The
FFT is ours — an iterative radix-2 Cooley–Tukey in
[`Fft`](../../app/src/main/java/io/github/deeplow/nobstuner/audio/Fft.kt) — with
twiddle factors and the bit-reversal permutation precomputed, so a transform
allocates nothing.

Layer a second step on top of textbook YIN: an **octave correction** in
[`PitchDetector.preferTrueFundamental()`](../../app/src/main/java/io/github/deeplow/nobstuner/audio/PitchDetector.kt).
YIN takes the first lag whose normalised difference dips below its threshold,
which on a weak fundamental is half the true period, and the note then reads an
octave high. The correction compares the candidate against its multiples and
moves to a longer period when that period explains the waveform substantially
better.

Its two constants come from measurement, not intuition. Probing real and
synthetic signals showed correct detections sitting at d′ ≈ 0 or getting *worse*
at twice the lag (ratios of 2.1 to 3.5), while genuine octave errors sat at
d′ = 0.09 to 0.12 and collapsed to a ratio near 0.03. Hence `OCTAVE_CHECK_FLOOR`
= 0.02 — below it the lag is already a true period and every multiple looks
equally good, so there is nothing to decide — and `OCTAVE_SWITCH_RATIO` = 0.5.

## Consequences

The detector reports fundamentals that a spectral peak picker would miss. On a
synthesised tone built only from harmonics 2 through 8, with no fundamental
present at all, it still returns the fundamental.

Accuracy is better than the display resolution: against a 329.63 Hz reference
played into a phone, it read 329.64 Hz.

Everything in the chain is ours, so there is no third-party DSP licence to
carry and the whole thing runs under JUnit — see
[0008](0008-verify-pitch-tracking-off-device.md).

### What this costs

The octave correction is a heuristic tuned on three instruments. Its floor and
ratio are not derived from anything; they are a line drawn through measurements
we happened to take, and an instrument with a very different partial structure
could fall the wrong side of it. The protection is the recordings suite, not the
argument.

Skipping the correction entirely is not an option we can quietly fall back to:
without it a violin G♯3 reads as G♯4 for about two seconds.

We own an FFT. It is 86 lines and tested against a naive DFT, but it is still
code we maintain instead of a library someone else maintains.

Nothing here handles two notes at once. YIN assumes one periodic source, so
strumming a chord gives an arbitrary answer rather than a useful one.

## Revisit when

- **A user reports a wrong octave on an instrument we have no recording of.**
  Add that recording to the suite before touching `OCTAVE_SWITCH_RATIO` — the
  constant is only as good as the evidence behind it, and changing it blind
  trades one instrument's correctness for another's.
- **Anyone proposes polyphonic detection** (chord tuning, or showing all six
  strings at once). YIN cannot do it, and that is a replacement of this record
  rather than an extension of it.
