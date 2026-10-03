# Privacy Policy for Nobs Tuner

**Last updated: 3 October 2026**

## Which Nobs Tuner is this about?

There are two, and the honest answer is not identical for both. Read the part
that applies to the one you are using.

- **The Android app**, installed from Google Play or from an APK. It has no
  network permission at all.
- **The web app**, opened in a browser. It is a web page, so loading it is a
  network request — but nothing about you travels over one, and the section
  below says how that is enforced rather than merely promised.

Everything in "The short version", "What it stores" and "What it never does"
is true of both. The two sections after that are where they differ.

## The short version

Nobs Tuner listens to your instrument through the microphone and works out what
note it is hearing. That is all it does with the audio, and it all happens on
your device. Nothing is recorded, stored, or sent anywhere.

## The microphone

Nobs Tuner needs microphone access to hear the instrument you are tuning. Audio
is read in short fragments, measured for pitch, and discarded immediately. It is
never written to storage, never sent off your device, and never shared with
anyone.

The microphone is only open while the tuner is in front of you. Leaving the app,
switching away, or locking the screen closes it.

## What it stores

Nobs Tuner saves the following, on your device only:

- your custom tunings,
- which tunings you have marked as favourites,
- the tuning you last selected,
- your settings (reference pitch, sharps or flats, tolerance, theme, and so on).

The Android app keeps these in its own private storage. The web app keeps them
in your browser's local storage for this site. Neither is sent anywhere, and
neither is readable by anyone else.

If you have Android's backup enabled, the app's settings may be included in your
device backup under your own Google account, in the same way as other apps'
settings. The developer has no access to those backups.

Uninstalling the app, or clearing this site's data in your browser, removes all
of it.

## What it never does

- It does not collect, transmit, or sell personal data.
- It does not contain analytics, advertising, or tracking of any kind.
- It does not create an account or ask who you are.

## The Android app: no network permission

The app declares no internet permission. It cannot send anything anywhere even
in principle, whatever its code might try to do, because the operating system
would refuse.

You do not have to take that on trust. The permissions of any Android package
can be listed with the standard tools:

```bash
aapt2 dump permissions app-release.apk
```

It reports `android.permission.RECORD_AUDIO` and one self-defined, app-private
entry that `androidx.core` declares for its own internal use. There is no
`android.permission.INTERNET`.

## The web app: nothing reaches another origin

A web page has to be fetched, so your browser does make a request to the server
hosting it, and that server — currently GitHub Pages — records the sort of
information every web server records about a request, including your IP address.
That is outside our control, and it is the one difference that matters between
the two versions.

After that, nothing. Specifically:

- The page carries a Content Security Policy that permits connections only to
  its own origin. No analytics endpoint, no font service, no error reporter, no
  third-party script of any kind can be contacted, because the browser itself
  blocks it. Audio could not be uploaded even if some future version tried.
- Your audio is analysed inside the page and discarded frame by frame, exactly
  as on Android.
- After the first visit the app works with the network switched off entirely.
  Turn on airplane mode and reload it: it still tunes. That is the simplest
  demonstration that nothing it needs comes from anywhere else.

You can check all of this yourself. The policy is visible in the page source,
and your browser's developer tools list every request the page actually made.

## Children

Nobs Tuner collects no data from anyone, including children.

## Changes

If this policy ever changes, the updated version will be published at the same
address and the date above will change.

## Contact

Questions about this policy can be sent to: **<ADD YOUR CONTACT EMAIL>**
