# ADR 0002 — Gate notes on ratios, with one absolute backstop

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

The detector returns something for every frame, including frames that hold only
room tone. Something has to decide which of those readings is a note worth
showing, and the obvious lever is loudness.

Loudness is the wrong lever, and the measurement that settles it is this: the
same tone, the same phone, the same room, measured **−26 dBFS through
`MediaRecorder.AudioSource.MIC` and −59 dBFS through `UNPROCESSED`**. The app
prefers `UNPROCESSED` because automatic gain control distorts a decaying
string's pitch, so the whole signal arrives about 33 dB quieter than a threshold
chosen on MIC-level audio would expect.

That is not theoretical. A gate of −48 dBFS, tuned against studio recordings,
made the app completely deaf on a real phone: the detector was reading 329.64 Hz
with a clarity of 1.00 while the smoother discarded every single frame.

A floor relative to the room is the obvious repair, but the first attempt at one
— an average over frames where no pitch was found — fails differently and worse.
A plucked string's tail is far louder than the room and only stops looking
periodic as it dies, so **every pluck teaches the average a louder "room" than
the last**. After a handful of plucks the floor has climbed above the next note
and the tuner goes deaf mid-session. That is precisely what a guitar did to it.

## Decision

Decide with ratios, which do not depend on input gain:

- **Clarity** (`minClarity` = 0.76) — YIN's own periodicity measure, a ratio by
  construction. This does the main work of telling a note from a room.
- **A margin above a learned noise floor** (`snrMarginDb` = 12 dB).

The floor is estimated in
[`PitchSmoother.noiseFloor()`](../../core/src/commonMain/kotlin/io/github/deeplow/nobstuner/audio/PitchSmoother.kt)
with three properties, each of which exists because its absence broke something:

1. **Only aperiodic frames contribute.** A bowed note held at a steady level
   would otherwise be learned as the room, and the tuner would stop hearing a
   note it was already tracking.
2. **Only frames with no note in flight contribute.** Aperiodic is not the same
   as silent: a pluck's attack is pick noise, louder than the note it
   introduces, and its tail turns aperiodic while still clearly audible. Both
   arrive mid-note.
3. **A low percentile, not a mean and not a minimum.** A mean ratchets upward as
   above. The outright minimum is hostage to a single quiet frame — one dropped
   the floor far enough that the hiss around it registered as a G♯1 at
   −74.6 dBFS. `FLOOR_PERCENTILE` = 0.25 over a window of 110 frames.

One absolute threshold survives, and the brief for this record was wrong to
imply otherwise: `minLevelDbfs` = −75 dBFS. It is not a judgement about
loudness, it is a backstop against digital silence, and it sits far below
anything audible on any input — the quietest real signal we have measured is
−59 dBFS.

A new note is additionally not believed until `ONSET_FRAMES` = 3 frames agree
within `ONSET_AGREEMENT_CENTS` = 60. The first frame of a pluck or bow stroke is
the attack, and the detector will confidently report a partial from it; one such
frame was enough to display a note nobody played.

## Consequences

The app works across input sources that differ by 30 dB without retuning
anything, which is the property that absolute thresholds cannot offer.

Each gate is independently justified and separately tested —
`PitchSmootherGateTest` and `PitchSmootherTest` carry 16 tests between them,
including one that plucks six times in a row and asserts all six register.

### What this costs

This is four interacting mechanisms where a single number would be easier to
reason about. Someone changing one of them will not obviously see what the other
three were protecting, which is why each is commented with the failure it
prevents and why that comment is worth keeping.

The floor needs `MIN_SAMPLES_FOR_FLOOR` = 20 aperiodic frames before it means
anything. Until then the margin is not applied at all, so for roughly the first
second in a loud room the app is more credulous than it later becomes.

Clarity at 0.76 is itself a tuned constant, and a very noisy stage could put a
real note under it. We have no recording of that case.

The onset requirement costs about 140 ms before the first reading of a note
appears. That is deliberate and nobody notices it, but it is latency we chose.

## Revisit when

- **Anyone changes the audio source away from `UNPROCESSED`**, or adds a
  fallback that changes it at runtime. The gain shifts by tens of dB and the
  only thing that keeps working is the part that is a ratio — check
  `minLevelDbfs` still sits below the quietest real signal on the new source.
- **A report arrives of the tuner going deaf partway through a session.** That
  is the signature of the floor climbing. Log `noiseFloor()` against the frame
  level before adjusting any constant.
