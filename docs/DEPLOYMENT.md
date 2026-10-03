# Deploying

Two artefacts come out of this repository: an Android App Bundle for Google
Play, and a static web app for GitHub Pages. Both are built by Actions; neither
needs anything run by hand.

## Repository access

The remote is `git@github.com:deepl0w/nobs_tuner.git`.

Pushing needs an account with write access to **`deepl0w`**. A machine
authenticated as a different GitHub account gets `pull: true, push: false` and
the push is refused with "Permission denied", which reads like a missing key but
is not — check with:

```bash
gh api repos/deepl0w/nobs_tuner --jq '.permissions'
```

Fix it by adding that account as a collaborator with write access, or by
authenticating the machine as `deepl0w`.

## Turning on Pages

Once, in the repository settings:

**Settings → Pages → Build and deployment → Source: GitHub Actions.**

Not "Deploy from a branch" — [`pages.yml`](../.github/workflows/pages.yml)
uploads an artifact and calls `actions/deploy-pages`, which only works with the
Actions source. The site then publishes on every push to `main` at
`https://deepl0w.github.io/nobs_tuner/`.

The workflow does three things worth knowing about:

- It runs the shared pitch suite against the **JavaScript** compilation, so a
  divergence between the two platforms fails in CI rather than on someone's
  instrument.
- It stamps the version into the service worker's cache name. Without that a
  returning visitor keeps being served the previous release's shell.
- It checks every file the service worker precaches by name actually exists,
  because a missing one costs offline support silently.

## Signing the Android bundle

[`android.yml`](../.github/workflows/android.yml) builds a release bundle on
`main`. It signs it only if these repository secrets exist, and produces an
unsigned bundle otherwise:

| Secret | What it holds |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the upload keystore, `base64 -w0 upload-keystore.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | its password |
| `ANDROID_KEY_ALIAS` | the key alias, `upload` by convention |
| `ANDROID_KEY_PASSWORD` | the key's password |

Add them under **Settings → Secrets and variables → Actions**. Generating the
keystore is covered in [PLAY_STORE.md](PLAY_STORE.md); it is the one piece of
this that cannot be regenerated if lost.

## What CI runs

| Workflow | On | Does |
| --- | --- | --- |
| Android | push to `main`, every PR | core and app tests, lint, documentation links, then a release bundle on `main` |
| Web app | push to `main`, every PR | the shared suite on Node, builds the site, checks it is complete, deploys on `main` |

The Android job fetches the instrument recordings before testing and continues
without them if the download fails — those tests skip rather than fail, and the
run reports the skips.

## Before the first public release

Two placeholders are visible in a public repository and both need real values:
the `applicationId` in [`app/build.gradle.kts`](../app/build.gradle.kts), which
is permanent once published to Play, and the contact address at the end of
[`PRIVACY_POLICY.md`](../PRIVACY_POLICY.md), which Play requires to be reachable
because the app asks for the microphone.
