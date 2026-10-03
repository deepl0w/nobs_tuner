# Nobs Tuner — notes for agents

A chromatic and preset tuner for string instruments: Android, Kotlin, Jetpack
Compose, everything on the device. `README.md` describes what it does and how the
tuning works; this file is about working on it.

## Several agents work here at once

One **main** agent works in the original checkout at `/home/deeplow/workspace/tuner`,
owns `main`, and is the only one that may push. Every other agent works in a
worktree under `.claude/worktrees/` on its own `claude/<name>` branch.

If you are in a worktree:

- **Never push.** Not with any flag, not for any reason.
- **Sync before you start**: `.claude/scripts/fleet.sh sync` merges `main` in.
- **Commit before you go idle**: `.claude/scripts/fleet.sh save "<message>"`. A
  Stop hook will not let you finish a turn with changes uncommitted.
- Your role lives in `.claude/role`; `/role tester|architect|feature` sets it.

The main agent merges those branches with `/integrate`, runs the suite, and pushes.

`.claude/scripts/fleet.sh brief` prints where you are and where your branch
stands — the SessionStart hook runs it for you. The protocol in full, including
what each role may change, is in `.claude/skills/fleet/SKILL.md`; commands are
`/role`, `/sync`, `/wrap-up` and `/integrate`.

Two hazards worth knowing. The git **stash stack is shared** across every
worktree, so never use a bare `git stash` / `git stash pop` — use a WIP commit
instead. And `local.properties` is git-ignored, so a fresh worktree cannot run a
single Gradle task until it is back; `fleet.sh brief` and `sync` restore it.

## Building and testing

JDK 17+ and the Android SDK; everything else comes from the wrapper.

```bash
./test.sh --check                    # is this machine set up
./gradlew :app:testDebugUnitTest     # the JVM suite — run this before handing work back
./gradlew :app:lintRelease           # lint
./build.sh --run                     # build, install and launch on a device
make help                            # the same things, wrapped
```

The real-recording regression tests (`*RealRecording*`) need instrument
recordings that are too large for version control. `tools/fetch-test-audio.sh`
downloads them; without them those tests skip and the rest still runs.

The tuner cannot be verified on an emulator — host audio never reaches the guest
microphone. Verify pitch behaviour off-device against recordings instead
(`docs/adr/0008-verify-pitch-tracking-off-device.md`).

## The code

```
app/src/main/java/io/github/deeplow/nobstuner/
  audio/   AudioEngine, HighPassFilter, Fft, PitchDetector, PitchSmoother
  model/   Notes, Tuning, TuningCatalog, PitchTargeting
  data/    TunerRepository, UserSettings     (DataStore + JSON)
  ui/      Compose screens, components, theme, TunerViewModel
```

**Everything from `HighPassFilter` rightwards is plain Kotlin with no Android
imports.** That is what lets the whole audio chain run under JUnit on a laptop,
and it is the single constraint most worth protecting: if a change to the audio
path needs an Android class, it belongs in `AudioEngine` or the view model.

The pitch-detection constants are measurements, not preferences. Thresholds in
`PitchDetector.preferTrueFundamental` and `PitchSmoother` came from real
recordings; changing one means re-running the real-recording suite, and the
reasoning behind several of them is in `docs/adr/`.

## Conventions

- **Comments explain why, not what.** The existing ones give the reason a
  threshold exists or a branch is there; match that density and tone rather than
  annotating syntax.
- **Commit messages** are one imperative line saying what changed and why —
  `Judge a fading note by whether it is still falling, not by an old peak`. Read
  `git log` before writing one.
- **ADRs are append-only.** `docs/adr/` records decisions that would be expensive
  to reverse. A published record is never rewritten, only marked
  `Superseded by NNNN`, and the index in `docs/adr/README.md` is updated with it.
- Prose in docs is written out, British spelling, no telegraphic bullet lists
  where a sentence would do.

## Before publishing

`applicationId` in `app/build.gradle.kts` is still the placeholder
`io.github.deeplow.nobstuner`, and the contact address in `PRIVACY_POLICY.md` is
a placeholder too. Both must change before the first Play Store upload — the
application id is permanent once published. See `docs/PLAY_STORE.md`.
