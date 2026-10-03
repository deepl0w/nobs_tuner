# ADR 0009 — Interfaces only where a test needs a seam

- **Status:** Accepted
- **Date:** 2026-10-03

Narrows [0005](0005-datastore-and-json-not-room.md), which chose DataStore and
named `TunerRepository` as the single place persistence lives. That is still
true; this record says what shape it takes. Extends the principle in
[0008](0008-verify-pitch-tracking-off-device.md) from the audio chain to the
layer above it.

## Context

[0008](0008-verify-pitch-tracking-off-device.md) established that the pitch
chain is verified off-device, and it works: the detector and the smoother carry
dozens of tests, including real recordings. The layer that decides what the
player actually sees had none.

The reason was construction, not neglect. `TunerViewModel` built its own
`TunerRepository(application)` and `AudioEngine(application)`, so reaching any
of its behaviour meant starting an Android runtime and opening a microphone. An
emulator cannot be fed host audio — the same constraint 0008 was written about —
so the listening state machine could not be exercised at all.

That gap hid a real defect. The "detect string automatically" setting was
persisted and displayed but never read: turning it off changed nothing while the
UI promised otherwise. Nothing could have caught it, because nothing could run
the code that was supposed to honour it.

## Decision

Two interfaces, each justified by a seam a test needs:

- **`PitchSource`** — what the tuner listens to. `AudioEngine` is the
  microphone; a test supplies a scripted flow of `PitchEstimate`s.
- **`TunerRepository`** — user data. `DataStoreTunerRepository` is the real one;
  a test supplies an in-memory fake whose writes land in the flows the view
  models read, so it can assert on what an action persisted.

`AppContainer`, held by the application object, is the only place that names a
concrete implementation. View models take their dependencies as constructor
parameters.

The container is hand-written. The graph is two objects deep and does not
branch; a dependency-injection framework would add a compiler plugin and a layer
of indirection to solve a problem this size.

**Interfaces are for seams, not for symmetry.** `PitchDetector`,
`PitchSmoother`, `HighPassFilter` and `PitchTargeting` stay concrete classes —
they are already pure, already directly testable, and an interface over them
would buy nothing. A new interface should come with the test that could not be
written without it.

`DataStoreTunerRepository` additionally takes its `DataStore` as a constructor
parameter, so the mapping itself — coercion, defaults, JSON decoding, dropped
repeats — is tested against a real store on a temporary file rather than mocked
away.

### Where `PitchSource` lives

Asked by `claude/feature-multiplatform`, which is moving `audio/` and `model/`
into a shared `core` module and has a browser capture path (AudioWorklet →
`TunerPipeline`) filling the same role `AudioEngine` does.

**The streaming half belongs in `core/src/commonMain/`. The permission check
does not.**

`pitchEstimates(): Flow<PitchEstimate>` is already common in everything but
location: `PitchEstimate` and the whole chain beneath it are moving to
`commonMain`, `Flow` is multiplatform, and "collecting opens the source,
cancelling closes it" describes an `AudioRecord` loop and an `AudioWorklet`
equally well. One seam for both platforms is strictly better than two parallel
ones, and it is the same seam the tests already drive.

`hasPermission(): Boolean` is Android's shape, not a shared one. It is a
synchronous `ContextCompat` query against a permission the system has already
decided. In a browser there is no synchronous equivalent: access is prompt-driven
and the Permissions API is asynchronous, so a common `hasPermission()` would
force the web implementation to return a cached guess and call it a fact. An
interface that two platforms implement is expensive to re-cut once both have, so
the Android-shaped half should not go in.

The split costs nothing, because the contract already carries permission failure:
`pitchEstimates()` fails with a permission error and `TunerViewModel` catches it
and surfaces the notice. `hasPermission()` is only a pre-check — it avoids
opening the device and seeds the "grant access" button — and a pre-check is
exactly the kind of thing a platform is allowed to do differently.

So: move `PitchSource` as the stream alone. Permission handling stays app-side
until the view model itself is shared, which is the point at which it needs a
port of its own rather than a method on the source. There is no hurry: nothing
breaks while it sits in the app module, and the view model is not shared today.

## Consequences

The targeting rule, the listening lifecycle and the library operations are
covered: 146 unit tests where there were 64, none of them needing a device. The
auto-detect defect is fixed and pinned by a test that fails if the setting is
ignored again.

Construction moved to one readable file, so what the app is made of can be seen
without reading every view model.

`TunerViewModel` kept the listening session and gave up the rest, to
`LibraryViewModel` and `SettingsViewModel`. They share no state, only the
repository — which is what stops two screens disagreeing about which tuning is
selected, and is the same single-source-of-truth argument 0005 makes.

### What this costs

Two indirections a reader has to follow: `TunerRepository` is now an interface,
so "go to definition" lands on a signature rather than on the DataStore code.
`DataStoreTunerRepository` is where persistence actually happens, despite 0005
naming `TunerRepository`.

A fake can drift from the real implementation. `FakeTunerRepository` reimplements
the write semantics — toggling a favourite, deleting a selected tuning — so a
change to those rules has to be made twice. `TunerRepositoryTest` exercises the
real one against a real store, which is what keeps the drift visible.

## Revisit when

A third implementation of either interface appears, or the graph grows past
what one file can hold without ceremony — either is the point at which a
framework starts paying for itself.

Or when the fake and the real repository disagree in a way that a test did not
catch: that would mean the seam is in the wrong place, and the repository should
be tested only against a real store.
