# Publishing StringTune to Google Play

## 0. Before the first upload

Two things are permanent once an app is published, so change them first:

1. **`applicationId`** in `app/build.gradle.kts`. It currently reads
   `io.github.deeplow.stringtune`, which is a placeholder. Use a domain you
   control, or `io.github.<your-github-username>.stringtune`.
2. **The upload key** (below). If you lose it you can ask Google to reset it, but
   it is a slow process — back it up somewhere durable.

Also fill in the contact address at the bottom of `PRIVACY_POLICY.md` and host it
at a public URL; Play requires a reachable privacy policy link because the app
requests microphone access.

## 1. Create an upload key

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep `upload-keystore.jks` outside the repository. It is git-ignored, but the
safest place is not in the project at all.

## 2. Tell the build about it

Either create `keystore.properties` in the project root (git-ignored — copy
`keystore.properties.example`):

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

…or set these environment variables, which is what CI should do:

```
ANDROID_KEYSTORE_FILE
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The build uses the file when present and falls back to the environment. With
neither, `bundleRelease` still succeeds but produces an **unsigned** bundle.

## 3. Build the bundle

```bash
./gradlew clean :app:bundleRelease
```

The result is `app/build/outputs/bundle/release/app-release.aab` — around 4 MB,
with R8 and resource shrinking on. Play requires an App Bundle, not an APK.

To sanity-check what a device will actually install, use
[bundletool](https://github.com/google/bundletool):

```bash
bundletool build-apks --bundle=app-release.aab --output=app.apks \
  --ks=upload-keystore.jks --ks-key-alias=upload
bundletool install-apks --apks=app.apks
```

## 4. Raising the version for each release

In `app/build.gradle.kts`:

- `versionCode` — an integer that must increase with every upload.
- `versionName` — what users see, e.g. `1.0.1`.

## 5. Store listing

Assets in `docs/play-assets/`:

| Asset | Requirement | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | `play-icon-512.png` |
| Feature graphic | 1024×500 PNG or JPEG | `feature-graphic.png` |
| Phone screenshots | 2–8, min 320 px, 16:9 or 9:16 | `screenshot-phone-*.png` |
| 7" tablet screenshots | up to 8 | `screenshot-tablet7-*.png` |
| 10" tablet screenshots | up to 8 | `screenshot-tablet10-*.png` |

Suggested text:

**Short description** (80 characters max)

> Accurate tuner for guitar, bass, ukulele, banjo, violin and more.

**Full description**

> StringTune is a precise, no-nonsense tuner for string instruments.
>
> • 60+ built-in tunings — guitar (6, 7 and 8 string), bass, ukulele, banjo,
>   mandolin, violin, viola, cello, double bass, bouzouki, resonator, lap steel
>   and more
> • Build and save your own tunings, with any number of strings and any notes
> • Star the tunings you use, so they are always at the top
> • Chromatic mode for anything else
> • Plays nothing, records nothing, sends nothing — all analysis is on-device
> • Adjustable reference pitch from 415 Hz to 466 Hz
> • Sharps or flats, adjustable tolerance, light and dark themes
>
> No ads, no accounts, no internet permission.

## 6. Data safety form

The app has no internet permission, so the answers are short:

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Not
  applicable — no data leaves the device.
- **Do you provide a way for users to request that their data is deleted?** Not
  applicable — nothing is collected. Uninstalling removes local settings.

Declare the microphone permission as used for app functionality (tuning), with
no audio recorded or transmitted.

## 7. Content rating and category

- Category: Music & Audio
- Content rating questionnaire: no sensitive content; the result is typically
  "Everyone" / PEGI 3.
- Ads: none.
- In-app purchases: none.

## 8. Target API level

`targetSdk` is 36 (Android 16), which is what Play requires for new apps and
updates as of 2026. `compileSdk` is 37 so the newest libraries can be used; that
is independent of the runtime behaviour `targetSdk` opts into. When Play raises
the requirement, bump `targetSdk`, read that release's behaviour changes, and
test before shipping.

## 9. Pre-launch checklist

```bash
./gradlew :app:testDebugUnitTest   # must be green
./gradlew :app:lintRelease         # must be clean
./gradlew :app:bundleRelease
```

Then install the signed build on a real device and confirm the microphone
permission prompt, the tuner reading, and that a custom tuning survives a
restart.
