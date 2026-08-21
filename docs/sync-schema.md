# Sync schema

The cross-platform contract for one user's progress. The web app and the Android app both
read and write this document; a future iOS app should implement exactly this and nothing
more.

**Project:** `canadiancitizenshipexam` · **Auth:** Firebase Auth, email + password only
**Document:** `users/{uid}` — one document per user, no subcollections.

Security rules allow a signed-in user to read and write only their own document, and deny
everything else:

```
match /users/{userId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

## Fields

| Field | Type | Written by | Notes |
|---|---|---|---|
| `email` | string | both | set at sign-up |
| `displayName` | string | both | `""` when unset, never null |
| `createdAt` | Timestamp | both | server timestamp, set once at sign-up |
| `progress` | map | both | **the core of the sync** — see below |
| `study` | map | both | `goalTarget: int`, `testDate: string \| null` (`"YYYY-MM-DD"`) |
| `mocks` | array | both | newest last — `{ pct: int, date: ISO-8601, passed: bool }` |
| `schemaVersion` | int | both | `2` |
| `updatedAt` | Timestamp | both | server timestamp, stamped on every write |
| `lastDevice` | string | both | `"Android"` / `"Web"` / `"iOS"` |

### `progress`

A map keyed by **question id as a string** (`"1"` … `"535"`). Each value:

| Key | Type | Meaning |
|---|---|---|
| `attempts` | int | times answered, all time |
| `correct` | int | correct answers |
| `incorrect` | int | wrong answers |
| `lastAttempted` | string | ISO-8601 UTC instant of the most recent answer, e.g. `"2026-08-21T14:03:22.481Z"` |
| `bookmarked` | bool | saved by the user |

`attempts == correct + incorrect`. Android stores attempts and misses and derives
`correct`, but **writes all three**, because the web reads `correct` directly.

A question that is only bookmarked still gets a record, with `attempts: 0`. Both platforms
do this; dropping it loses the bookmark.

## Derived, never stored

These are computed from `progress` on every platform. Storing them is what let two devices
disagree, so they are deliberately absent from the document:

- **totals** — answered is the sum of `attempts`, correct the sum of `correct`
- **accuracy** — correct ÷ answered
- **streak** — consecutive local days, ending **today**, present in the set of
  `lastAttempted` days. Zero if nothing was answered today.
- **best streak** — the longest such run on record (Android only; the web has no such UI)
- **weekly activity** — for each of the last 7 days, the count of questions whose
  `lastAttempted` falls on that day

### A known imprecision, shared on purpose

A question remembers only its *most recent* attempt. Re-answering an old question moves its
day forward, and the day it used to occupy vanishes from the set unless another question
still points there — so a streak can shrink retroactively.

This is the web app's long-standing behaviour. Android matches it deliberately, because a
streak that differs between two devices on the same account is worse than one that is
occasionally generous or stingy on both. Fixing it properly means recording study days
explicitly (say, a `days: string[]` field) and changing both clients together.

## Device-local, never synced

| Value | Why |
|---|---|
| theme | a device preference — dark on the phone, light on the desktop is a reasonable thing to want |
| notification prefs | they control Android notifications the web cannot deliver |
| onboarding completion | per install |
| in-flight session | you are mid-session on *one* device |
| today's goal count (`goalDone`/`goalDate`) | a daily counter that resets anyway; `goalTarget`, the setting, **does** sync |

## Conflict resolution

**Last write wins.** Fine for one person studying on one device at a time, which is the
normal case. Writes use `set(..., merge)` at field granularity so a field only one platform
writes is never erased by the other.

It has one sharp edge, and it is not a rare race — it is the *first* thing that happens:
signing in on a device that already has local progress, to an account that already has
cloud progress. Silently picking a side there would destroy real work, so the client asks
once which to keep, then runs under last-write-wins from then on. If either side is empty,
it adopts the non-empty one without asking.

`updatedAt` and `lastDevice` exist so a client can tell the user when and from where
progress last synced, rather than leaving them to guess.

## Implementation notes

Android's mapper is `app/src/main/java/com/mvnsh/citizenship/data/ProgressDocument.kt` — a
pure object with no Android or Firebase types, covered by `ProgressDocumentTest`. It is the
file to port. Its defensive reads are not decoration: this document is written by another
application on another platform, and a field can be absent, null, or a `Long` where an
`Int` was expected. None of those is a reason to lose someone's progress.
