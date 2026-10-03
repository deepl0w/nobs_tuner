# ADR 0007 — Ship with no network permission

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

The app listens through the microphone, which is the permission users are most
wary of granting and the one Google Play asks the most pointed questions about.
A privacy policy is mandatory for it, and the Data safety form has to be
answered truthfully and is visible on the store listing.

Nothing the app does needs a network. Pitch detection is arithmetic, the catalog
is compiled in, and user data is a few kilobytes on the device.

## Decision

Declare no `INTERNET` permission, and take on no dependency that would add one —
no analytics, no crash reporting, no remote configuration, no ad SDK.

The audio contract is narrower than the permission implies and is stated in
[`PRIVACY_POLICY.md`](../../PRIVACY_POLICY.md): frames are read, measured for
pitch, and discarded. Nothing is written to storage or retained beyond the
frame. The microphone is held only while the tuner screen is resumed —
`LifecycleResumeEffect` starts and stops the capture flow.

## Consequences

The Data safety answers are "no" to collection, "no" to sharing, and "not
applicable" to transit encryption and deletion requests, with no interpretation
required.

The claim is checkable rather than asserted. `aapt2 dump permissions` on the
shipped release APK reports exactly two entries:

```
uses-permission: name='android.permission.RECORD_AUDIO'
uses-permission: name='io.github.deeplow.nobstuner.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

The second is self-defined and app-private — `androidx.core` declares it for its
own non-exported receivers. It grants nothing and is not a system permission.

Because there is no network call anywhere, a whole class of review and
compliance work does not exist: no TLS configuration, no certificate pinning
question, no third-party data processor to name.

### What this costs

No crash reporting. A crash on a user's device is invisible to us unless they
report it through Play, and the first signal of a bad release is a review.

No remote configuration. A constant tuned wrongly — and
[0002](0002-gate-notes-on-ratios-not-absolute-levels.md) has several that are
tuned rather than derived — can only be corrected by shipping a new version.

No usage data, so questions like "does anyone use the strobe display" or "which
tunings matter" can only be guessed at.

Adding any of these back is not a small change: it reverses the store listing's
central claim, and the honest version of that reversal is a new record here and
a new privacy policy, not a quiet dependency bump.

## Revisit when

- **Anyone proposes crash reporting or analytics.** That is a reversal of this
  decision, not an addition to the build file, and it changes what the listing
  says about the app.
- **A feature is proposed that genuinely needs a network** — sharing tunings by
  link, a downloadable catalog. Weigh it against the fact that "no internet
  permission" is currently a feature of the listing.
- **Before each release**, as part of the checklist in
  [`docs/PLAY_STORE.md`](../PLAY_STORE.md): re-run `aapt2 dump permissions` on
  the release APK. A transitive dependency can add a permission without anyone
  editing the manifest.
