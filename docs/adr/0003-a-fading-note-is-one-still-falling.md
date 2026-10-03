# ADR 0003 — A fading note is one still falling, not one below a peak

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

[`PitchSmoother`](../../core/src/commonMain/kotlin/io/github/deeplow/nobstuner/audio/PitchSmoother.kt)
folds octave jumps back to where a note was, because as a plucked or bowed note
dies its fundamental fades before its partials and the detector starts hearing
the octave above. Without the fold, the display jumps an octave exactly as the
player is finishing an adjustment.

A fold that never gives up would be wrong too — a player really does move from
one string to another an octave away — so the fold is capped at
`OCTAVE_FOLD_LIMIT` = 12 frames, *unless* the note is decaying, in which case it
continues. Something therefore has to decide what "decaying" means.

The first answer was: the level sits more than 12 dB below the loudest level
seen since the last silent release. It is wrong, and the way it is wrong is
instructive, because it reads as a restatement of "this note is fading" and is
not one.

Pluck a low string hard, then a higher one gently. The quiet note is more than
12 dB below the loud one's peak, so it registers as *that* note still decaying,
for as long as it is held. The escape is gated on not-decaying, so it never
fires, and the display sits an octave — or two — under the string actually being
played until a full silent release. On a guitar: tune the low E hard, pick the
high E softly, and read E2.

Giving the peak a release constant fixes that case and regresses another. The
cello C2 in the recordings suite has a genuine harmonic artifact that is folded
*only* because the stale peak keeps `decaying` true; with a release, the display
flips to C3 partway through the decay. Three tests documented the deadlock.

## Decision

Define `decaying` as **the level is still falling**, not as the level being far
below some earlier maximum.

`PitchSmoother` keeps the last `TREND_FRAMES` = 9 frame levels and treats the
note as fading when the newest is at least `FALL_MARGIN_DB` = 2 dB below the
oldest. `peakLevelDbfs` is deleted.

The two cases are not separable by level at all — they are separable by
direction. A note that is genuinely fading keeps getting quieter. A new, quieter
note settles at its own level and stays there.

## Consequences

Both behaviours hold at once, which neither of the level-based rules managed.
The cello artifact stays folded for as long as it really is dying, and a new
string is followed once it steadies. The three tests that had documented the
deadlock as a known live bug now pass, and the recordings suite is unchanged.

Removing `peakLevelDbfs` also removes a piece of state whose lifetime was tied
to the release window, which is one fewer thing that can be stale.

### What this costs

A sustained source with a genuinely constant level — a bowed note held very
evenly, or a sustaining pickup — never looks like it is falling, so an octave
artifact on one of those is folded for only 12 frames before being accepted.
We have no recording of that case; the detector-level correction from
[0001](0001-yin-with-an-fft-difference-function.md) is what covers it, and if it
does not, this record is where the gap is.

Two more constants, and 9 frames is about 0.4 s — a deliberately short view. A
note that fades in steps rather than smoothly could read as settled between the
steps.

## Revisit when

- **An octave artifact is reported on a sustained, level source** — bowed,
  e-bowed, or a sustain pedal. That is the hole this rule leaves open, and the
  fix belongs in the detector, not here.
- **`OCTAVE_FOLD_LIMIT` is tuned for any reason.** It only has meaning when
  `decaying` is false, so the two constants have to be reasoned about together.
