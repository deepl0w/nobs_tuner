# Nobs Tuner on the web

The same tuner as the Android app, as a page you can open and install to a home
screen. It is a static site: no build step of its own, no framework, no npm
dependencies.

The tuning is not implemented here. Pitch detection, note maths and the sixty-one
presets come from `core/`, the Kotlin Multiplatform module the Android app also
uses, compiled to JavaScript — see
[ADR 0010](../docs/adr/0010-one-tuner-core-two-platforms.md) for why, and
[ADR 0011](../docs/adr/0011-a-web-tuner-alongside-the-app.md) for what shipping
a second artefact does to the privacy claim.

## Running it

The compiled core is build output, so produce it first:

```bash
./gradlew :core:syncWebCore     # writes web/vendor/
```

Then serve the directory over HTTP. Opening `index.html` as a `file://` URL will
not work: ES modules, `AudioWorklet` and `getUserMedia` all need an origin, and
the microphone needs a *secure* one.

```bash
python3 -m http.server 8000 --directory web
```

`http://localhost:8000` counts as secure, so the microphone works there. Any
other host needs HTTPS.

**The service worker will serve you a stale copy.** It caches the shell by
design, keyed on a version that only changes at deploy time, so during
development an edit can be invisible. Use a hard reload, or untick *Update on
reload* → *Bypass for network* in the Application panel, or work in a private
window.

## How it fits together

```
index.html ── src/main.js            state, routing, the microphone's lifetime
                ├── audio.js         getUserMedia + AudioWorklet
                │     └── hop-worklet.js      regroups 128-sample quanta into hops
                ├── meters.js        the four dials, on canvas
                ├── store.js         localStorage, mirroring TunerRepository
                ├── ui.js            DOM helpers and the Material icons
                ├── views/           tuner, library, editor, settings
                └── ../vendor/       the compiled Kotlin core
sw.js                                offline support
```

Audio arrives from the worklet in 2048-sample hops, about 23 a second, and each
is handed straight to the shared pipeline. The analysis runs on the main thread
rather than in a worker because a hop costs around a millisecond and arrives
every forty-three; a worker would add a message hop and a second copy of the
state for no measurable gain.

`main.js` owns all mutable state. Views are rebuilt when something structural
changes and patched in place when only the reading does — a full re-render
twenty times a second would lose focus and close menus.

## Things worth knowing before you change it

- **Inline `style` attributes do not work.** The page's Content-Security-Policy
  forbids them, which is what lets it promise that nothing reaches another
  origin ([0011](../docs/adr/0011-a-web-tuner-alongside-the-app.md)). Use a
  class, or set properties through the CSSOM (`node.style.width = ...`), which
  is allowed.
- **A Kotlin top-level `val` is not a number in JavaScript.** It arrives as
  `{ get(): number }`, so `x === core.someConstant` is quietly always false.
  Constants cross inside `defaultsJson()` instead. Anything new crossing the
  boundary deserves the same suspicion — the compiler will not help you here.
- **Changing the shared core means re-running `:core:syncWebCore`.** Nothing
  tells you that `web/vendor/` is stale.
- **The browser may ignore the audio constraints.** `autoGainControl: false` and
  friends are requests. Android can insist on an unprocessed input; the web
  cannot.

## Deploying

`.github/workflows/pages.yml` builds the core, runs the shared pitch suite
against the JavaScript compilation, stamps the version into `version.js` and
`sw.js`, checks every file the service worker precaches actually exists, and
publishes `web/` to GitHub Pages.

Everything is relative — `start_url`, the manifest scope, every import — so the
site works from a project subpath (`/nobstuner/`) as happily as from a domain
root.
