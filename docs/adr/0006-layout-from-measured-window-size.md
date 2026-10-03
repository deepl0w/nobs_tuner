# ADR 0006 — Lay out from measured window size, not device class

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

The tuner screen has to hold a mode switch, a display, a note readout and a row
of string buttons, on everything from a 360 × 640 dp phone to a 1280 × 800 dp
tablet, in both orientations.

"Phone or tablet" does not describe the problem. A phone in landscape is short
and wide, which is the same layout problem a small tablet in landscape has and a
completely different one from the same phone held upright. Multi-window and
freeform split the difference again, and a resource qualifier cannot see any of
it.

## Decision

Derive the layout from the measured size of the window, in
[`WindowShape`](../../app/src/main/java/io/github/deeplow/nobstuner/ui/components/Responsive.kt),
built from `BoxWithConstraints` inside the scaffold. It exposes what the screens
actually branch on — `isCompactWidth`, `isShort`, `isTightHeight`,
`prefersSideBySide`, `readingMaxWidth` — rather than a device category.

Two arrangements follow from it: a single centred column, and dial-beside-
controls when the window is short and wide or genuinely large.

Two further rules fall out of the same measurement rather than from constants:

- **The dial is sized by the height available**, not only the width. It is 1.85
  times as wide as it is tall, so on a landscape phone the limit comes from the
  height, and `meterWidthFor()` computes it from the shape.
- **String buttons are sized to fit, and wrapped in balanced rows.** Six strings
  across five slots reads as "five and one" rather than as one instrument, so
  `balancedRows()` splits 6 into 3 + 3, or 2 + 2 + 2 when narrower, longest row
  first.

## Consequences

Verified by screenshot at 360 × 640, 411 × 914, 428 × 932, 800 × 1280,
914 × 411 and 1280 × 800 dp. No resource qualifiers and no duplicated layouts;
one composable tree reads the shape.

`balancedRows()` is pure arithmetic and is tested exhaustively for every
combination of 1–12 strings against 1–12 slots.

Content is width-capped — a dial does not get more readable by being 800 dp
wide, it just gets bigger.

### What this costs

`BoxWithConstraints` subcomposes, so this is measurably more expensive than a
plain `Box`. At one per screen it does not matter, and lint will complain if the
scope is ever added without being read.

The thresholds — 600 dp, 840 dp, 520 dp, 700 dp — are Material's buckets and our
own judgement mixed together, and nothing enforces that a screen branches on a
sensible one. A screen is free to ask `shape.height < 500.dp` directly and
nobody would notice.

Screenshots are the only check on any of this. There is no instrumented layout
test, so a regression at an untested size would ship.

## Revisit when

- **The app is asked to run in a resizable window that changes while open** —
  foldables mid-fold, desktop windowing. The shape is read at composition, which
  is correct, but nothing has been tested through a live resize.
- **A third arrangement is proposed.** Two is manageable as an `if`; three wants
  the decision named rather than spelled out at each call site.
