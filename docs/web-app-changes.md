# Web app changes for cross-device sync

Spec for `D:/projects/Canadian_Citizenship_Exam`. Nothing here has been applied — this is
the list to work through in that repo. Read `sync-schema.md` first; it is the contract both
apps implement.

**The headline: core progress sync needs no web change at all.** Android writes the
existing `users/{uid}.progress` shape, so per-question history, bookmarks, totals and the
streak already cross between the two apps today. Everything below is either a latent bug
worth closing or an addition for full parity.

Items are ordered by how much they matter.

---

## 1. Fix `setDoc` without `merge` — do this first

**`js/auth.js:32-41`** and **`js/progress.js:15-33`** both write the whole document:

```js
await setDoc(userRef, {
  email,
  displayName: name || "",
  createdAt: serverTimestamp(),
  progress: {},
});
```

Harmless today, because the document only ever holds those four fields. It stops being
harmless the moment Android writes `study` and `mocks`: either path firing on a document
that is momentarily absent would erase them silently.

`js/progress.js` is the riskier of the two — it runs at **every sign-in**, guarded only by
`snap.exists()`.

**Change:** add `{ merge: true }` to both calls.

```js
await setDoc(userRef, { … }, { merge: true });
```

Worth doing even if you adopt nothing else here.

---

## 2. Move mock history into Firestore

Today: `js/history.js` keeps the last 3 attempts in `localStorage` under
`cp-mock-history:<uid>`, written from `js/app.js:1081-1083`. It is per-device, so a mock
taken on the phone never appears on the desktop.

**Change:** read and write the `mocks` array on `users/{uid}` instead.

```js
// { pct: number, date: ISO-8601 string, passed: boolean }, newest last
await updateDoc(doc(db, "users", uid), { mocks: arrayUnion(attempt) });
```

Two details:

- The cloud array is the **full** history, not capped at 3. Keep showing the last 3 —
  `mocks.slice(-3).reverse()` — but do not truncate what you store; Android's Progress
  screen lists them all.
- The field is `pct`, not `percentage`. Renaming on the web side is the smaller change.

Note the ordering flip: `localStorage` held newest-first, the cloud array is newest-last.

---

## 3. Read the `study` block

```
study: { goalTarget: int, testDate: string|null }   // testDate is "YYYY-MM-DD"
```

**Be honest about what this buys today:** the web app has no daily-goal or test-date UI, so
until it grows one this is Android writing and the web preserving. It is specced now so the
field names are agreed once rather than twice.

The only thing required of the web immediately is *not to clobber it* — which item 1
already covers.

If you do add the UI later: `goalTarget` is questions per day (the Android picker offers
10/20/30) and `testDate` drives a countdown.

---

## 4. Stamp `updatedAt` and `lastDevice`

On every write that touches progress:

```js
updatedAt: serverTimestamp(),
lastDevice: "Web",
```

Conflict resolution is last-write-wins, so these are what let either client tell the user
when and from where progress last synced instead of leaving them to guess. Android already
writes both.

---

## 5. Set `schemaVersion: 2`

Include it on the bootstrap write. Cheap now, and the thing that makes a future migration
diagnosable rather than archaeological.

---

## 6. Optional: offline persistence

`js/firebase.js:29` calls `getFirestore(app)` with no local cache, so the web app needs a
live connection to load progress. Android enables offline persistence.

```js
import { initializeFirestore, persistentLocalCache } from ".../firebase-firestore.js";
export const db = initializeFirestore(app, { localCache: persistentLocalCache() });
```

Not required for sync to work. Worth it if you want the web app usable on a bad connection.

---

## Deliberately not proposed

- **Sign-in stays required on the web.** Android's is optional because it ships the bank as
  a bundled asset and can study offline; the web fetches its bank and gates on auth. Making
  the web work signed-out is a much larger change and is not needed for sync.
- **Theme and notification preferences stay device-local** — see `sync-schema.md`.
- **No change to the `progress` shape.** It is the one thing both apps already agree on;
  changing it would be the only way to break sync that currently works.

## How to verify

1. Apply item 1, deploy, confirm the web app still signs in and records answers.
2. Answer questions on the web; sign in on Android; confirm the same history, bookmarks,
   accuracy and streak.
3. Answer on Android; reload the web app; confirm they appear and that the web's streak
   counts the phone's study.
4. After item 2: take a mock on each platform and confirm both appear on both.
