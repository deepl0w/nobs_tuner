# ADR 0005 — Persist with DataStore and a JSON blob, not Room

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

The app has to remember four things across launches: the user's custom tunings,
which tunings they have starred, which tuning is selected, and their settings.

The built-in catalog is 61 presets and is compiled in, not stored. User data is
whatever they add on top, which in practice is a handful of tunings.

Room is the default answer for persistence on Android, and it brings a schema,
a compiler plugin, migrations, and DAOs.

## Decision

Use `androidx.datastore:datastore-preferences` for everything, and store custom
tunings as a single JSON string under one preference key, serialised by
`kotlinx.serialization`. All of it lives in
[`TunerRepository`](../../app/src/main/java/io/github/deeplow/nobstuner/data/TunerRepository.kt).

The data is tens of records at most, is always read and written whole, and is
never queried by anything but identity. None of what a database offers is
needed.

## Consequences

There is no schema to migrate. Adding a field to `Tuning` is a Kotlin change;
`ignoreUnknownKeys` means an older build reading a newer blob degrades instead
of crashing.

Everything is a `Flow`, so the UI follows writes without any explicit
invalidation.

A corrupt or unreadable store falls back to defaults rather than crashing — the
decode is wrapped and `IOException` on the stream emits empty preferences.

No KSP or annotation processor in the build, which keeps the Kotlin and AGP
versions free to move independently.

### What this costs

Every write rewrites every custom tuning. At tens of records that is
imperceptible; at thousands it would not be, and nothing in the code warns you
where the line is.

There is no query. "Which tunings use a dropped D?" means loading all of them
and filtering in Kotlin — fine today, and a wall if the catalog ever became user
data too.

The blob is schemaless, so a bad write is only caught at read time, and the
recovery is to discard it. `TunerRepositoryTest` carries 10 tests over this,
which is the only thing standing between a serialisation slip and silent data
loss.

Preferences are not a good place for anything large or binary. If recordings or
per-string history ever get stored, this is the wrong mechanism and should be
replaced rather than stretched.

## Revisit when

- **Custom tunings become shareable or syncable** — an export format, a backup
  beyond Android's own, or anything that gives a tuning an identity outside this
  device. That needs a schema and probably a version field, which is the point
  at which a blob stops being enough.
- **Anything per-use gets stored** — a tuning history, usage counts, a log of
  sessions. Those grow without bound and want a database.
