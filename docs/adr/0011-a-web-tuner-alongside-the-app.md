# ADR 0011 — A web tuner alongside the app, and what it does to the privacy claim

- **Status:** Accepted
- **Date:** 2026-10-03

Does not supersede [0007](0007-no-network-permission.md), whose decision about
the Android artefact is unchanged, but it is the record to read next to it:
0007's claim was written when there was one artefact, and there are now two.
Built on [0010](0010-one-tuner-core-two-platforms.md).

## Context

The Android app requires an install, a Play account and a supported device.
A tuner is a thing people want for ninety seconds, often on a borrowed phone or
a laptop in a rehearsal room. A web version reaches all of that with a link, and
every browser worth the name now has `getUserMedia` and `AudioWorklet`, which is
all the tuner needs.

The obstacle is not technical. [0007](0007-no-network-permission.md) rests the
Play listing on a claim — no `INTERNET` permission, nothing leaves the device —
and the Data safety answers are "no" to collection and "no" to sharing with no
interpretation required. A web page is, unavoidably, fetched over a network.
Shipping one without saying so would quietly turn a precise claim into a vague
one.

## Decision

Ship a Progressive Web App from `web/`, hosted on GitHub Pages, built on the
shared core from [0010](0010-one-tuner-core-two-platforms.md). State the privacy
position **per artefact**, because the two are not the same and pretending
otherwise is what would make the claim worthless.

### The Android artefact's claim is untouched

Nothing in `web/` enters the APK or the AAB. `:app` gains no dependency that
could introduce a permission, and the release still declares `RECORD_AUDIO` and
nothing else. 0007's pre-release check — `aapt2 dump permissions` on the release
APK — is unchanged and remains the thing that proves it.

### The web artefact makes a different claim, and a checkable one

It cannot say "no network": fetching the page is a network request, and the host
sees that request. What it can say is that nothing *about you* travels over one,
and that this is enforced rather than promised. The page carries a
Content-Security-Policy restricted to its own origin:

```
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self';
font-src 'self'; media-src 'self'; connect-src 'self'; worker-src 'self';
manifest-src 'self'; base-uri 'none'; form-action 'none'; object-src 'none'
```

`connect-src 'self'` is the load-bearing line. No `fetch`, `XMLHttpRequest`,
WebSocket or `sendBeacon` can reach any other origin — not an analytics
endpoint, not a font CDN, not an error reporter. Audio captured by
`getUserMedia` is analysed in the page and discarded frame by frame, and even if
a future edit tried to send it somewhere, the browser would refuse. No inline
script or style is permitted either, so there is nowhere for an injected one to
run.

This is the part that matters: a reader does not have to believe us. The policy
is in the page source, the browser enforces it, and the DevTools network tab
shows every request that was actually made. A promise in a privacy policy is
checkable only by its author; a CSP is checkable by anyone.

`frame-ancestors` is deliberately absent — browsers ignore it in a `<meta>`
element and a static host cannot send real headers, so including it would be
decoration.

The service worker is the strongest practical demonstration. After the first
load the app is fully usable with the network switched off, which is both the
feature a rehearsal room wants and a thing anyone can verify in ten seconds with
airplane mode.

`PRIVACY_POLICY.md` says which artefact a reader is holding before it says
anything else, and answers for each separately.

## Consequences

The tuner reaches anyone with a link, including iOS users the Play listing never
could, and installs to a home screen without a store.

The two artefacts cannot disagree about *tuning*, because
[0010](0010-one-tuner-core-two-platforms.md) gives them one implementation and
one test suite. They can only disagree about presentation and platform
behaviour, which is a much smaller surface to keep honest.

Hosting is static files on GitHub Pages. There is no server to run, no database,
no account system and no logs beyond whatever the host keeps about HTTP requests
— which is a thing to disclose, not a thing to control.

### What this costs

**Two privacy stories to keep straight.** Anyone editing `PRIVACY_POLICY.md`
now has to be clear which artefact a sentence is about, and a claim that is true
of one may be false of the other. That is a permanent editorial burden and the
main reason this record exists.

**The host sees requests.** GitHub Pages logs IP addresses for page loads like
any web server. That is outside our control and has to be said rather than
engineered away. The Android app has no equivalent exposure, so this is a real
difference in favour of the app, not a wash.

**The browser will not give us the microphone we want.**
`AudioEngine` asks Android for `UNPROCESSED` and falls back to
`VOICE_RECOGNITION`, both chosen to defeat automatic gain control and noise
suppression that distort a decaying string. The web can only *request*
`autoGainControl: false` and friends; a browser may ignore all three. The web
tuner is therefore working with a signal the Android one would reject, and
[0002](0002-gate-notes-on-ratios-not-absolute-levels.md)'s ratio-based gating is
what makes that survivable — it was already designed not to trust absolute
levels.

**No end-to-end coverage of the browser's audio path**, for the same reason
0008 gives for Android: automation can prove frames arrive, not that the right
note appears. The gap is the same shape and is closed the same way, by picking
up a device.

**The service worker serves a cached shell.** The cache name carries the
deploy's version so each release starts clean, but during development an edit
can be invisible until a hard reload. That cost real time before it was
understood.

**iOS is a second-class host and not ours to fix.** Safari grants a PWA less
storage, evicts it more readily, and has historically had `getUserMedia` bugs in
standalone mode. When it breaks there, we wait.

## Revisit when

- **Anyone proposes a hosted feature** — sharing a tuning by link, a
  server-side catalog, sign-in. That is a reversal of the position above, not an
  addition to it, and it needs its own record and a rewritten privacy policy.
- **Anyone proposes analytics on the web app** because "the web is different".
  It is not different enough: 0007's reasoning about what analytics costs applies
  unchanged, and adding them would mean deleting the CSP line this record rests
  on.
- **The Android app is asked to depend on anything in `web/`**, which would end
  the separation that keeps 0007 true.
- **Hosting moves off GitHub Pages.** The CSP and the offline behaviour travel,
  but the logging disclosure is about a specific host and would need rewriting.
