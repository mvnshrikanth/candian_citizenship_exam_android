# Next steps — Android app

Continuation spec for `D:/projects/Canadian_Citizenship_Exam_Android`, branch
`feat/android-native-app`. The companion document for the other half of the work is
`web-app-changes.md`; the contract they share is `sync-schema.md`.

Last commit: `cc2b830` — Firebase auth and Firestore sync. Working tree clean.

---

## Where this stands

**Built and verified:** the whole app — onboarding, home, practice, topics and topic
detail, quiz, practice results, the mock flow (intro, timed run, navigator, results,
review), weak list, bookmarks, search, progress, settings with dialogs and theme
switching. 82 JVM tests and 106 instrumented tests were green as of commit `fce1763`.

**Built but NOT verified on a device:** everything from `5555dc9` onward — the derived-
statistics refactor and the entire sync feature. It compiles and the JVM tests pass. See
"Verify first" below; do not treat sync as working until those pass.

**Not started:** Tasks 15 and 16 of the original plan
(`docs/superpowers/plans/2026-08-18-android-native-app.md`).

---

## 1. Verify first — nothing below matters until these pass

### 1.1 Sign-in has never completed a real round trip

The wiring is right (project `canadiancitizenshipexam`, package `com.mvnsh.citizenship`,
`google-services.json` in place) and the screen renders, but **no credential has ever been
sent**. A test with a deliberately non-existent account was cut short when the emulator
dropped.

Cheapest first check, which creates no data: enter a fake address and any password on
Settings → Account → Sign in to sync. Expect *"There is no account for that email
address."* — that alone proves the app reaches Firebase Auth. If it reports a connection
problem instead, suspect the `INTERNET` permission or emulator networking, not the code.

Then the real one: sign in with an account that has web progress and confirm it arrives.

### 1.2 Re-run the instrumented suite

It has not run since the Account section was inserted into Settings. `SettingsTest` is the
likely casualty: it addresses grouped-list rows, and a new group now sits **above** the
Data and About groups it asserts on.

```bash
# In two halves — see "Environment" below.
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mvnsh.citizenship.LaunchTest,com.mvnsh.citizenship.NavigationTest,com.mvnsh.citizenship.data.ProgressRepositoryTest,com.mvnsh.citizenship.ui.OnboardingTest,com.mvnsh.citizenship.ui.HomeTest,com.mvnsh.citizenship.ui.PracticeAndTopicsTest,com.mvnsh.citizenship.ui.ProgressScreenTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mvnsh.citizenship.ui.QuizTest,com.mvnsh.citizenship.ui.ResultsTest,com.mvnsh.citizenship.ui.MockTest,com.mvnsh.citizenship.ui.ListsAndSearchTest,com.mvnsh.citizenship.ui.SettingsTest
```

### 1.3 End-to-end sync

1. **Signed out, offline.** Airplane mode, fresh install, onboard, answer questions, take a
   mock. Nothing blocks or errors. This replaces the deleted "no `INTERNET` permission"
   guarantee and is now a test that can actually fail.
2. **Web → Android.** Study on the web, sign in on Android, confirm the same per-question
   history, bookmarks, totals and streak.
3. **Android → web.** Answer on Android, reload the web app, confirm they land and that the
   web's streak counts the phone's study.
4. **Streak agreement.** Answer on both on the same day; both must show the same streak and
   the same weekly chart. This is the whole point of the `5555dc9` refactor.
5. **Both sides non-empty at first sign-in.** The choice dialog must appear, and the chosen
   side must win on both platforms.
6. **Offline then reconnect.** Airplane mode signed in, answer, restore network, confirm
   the writes reach Firestore.
7. **Sign out.** Local progress remains and the app still works.

There is **no automated coverage of `SyncRepository` or `AuthRepository`** — only the pure
mapper (`ProgressDocumentTest`, 13 tests) is tested. Consider a Firebase-emulator test if
this is going to be maintained.

---

## 2. Task 15 — reminder notifications

Not started; `notify/` does not exist. Spec is in the original plan, Task 15. Shape:

- `notify/Channels.kt` — one channel, created in `CitizenshipApp.onCreate`
- `notify/ReminderDecision.kt` — **pure**, so every branch is JVM-testable: daily nudge when
  today's goal is unmet, streak warning at 3+ about to break, countdown at 14/7/1 days
- `notify/ReminderWorker.kt` — one `CoroutineWorker` at 20:00 evaluating all three, since
  they share a trigger and a data source
- `notify/ReminderScheduler.kt` — `enqueueUniquePeriodicWork`; WorkManager survives reboot,
  so no `BOOT_COMPLETED` receiver

The Settings toggles and `POST_NOTIFICATIONS` request already exist and persist — only the
delivery half is missing, so the switches currently promise something that never arrives.
That is the strongest argument for doing this next.

Note the streak reminder must now read the **derived** streak (`Stats.streak(p, today)`),
not a stored field.

---

## 3. Task 16 — shell states, accessibility, release

- **Loading and error states.** `view_loading.xml` / `view_error.xml` do not exist. Every
  bank-dependent screen should gate on `vm.bank == null` / `vm.loadFailed`.
  `AppViewModel.forceLoadFailureForTest()` already exists for the test.
  Use the honest copy: the bank is bundled, so there is nothing to "reconnect" to.
- **Accessibility sweep.** `espresso-accessibility` is already a dependency but no
  `AccessibilityTest` exists. Expect it to flag sub-48dp touch targets (quiz flag, topic
  flags, search clear), missing `contentDescription` on icon-only buttons, and decorative
  icons that should be `importantForAccessibility="no"`. Fix rather than suppress.
- **Release build.** `./gradlew :app:assembleRelease` has **never been run**. This is what
  catches R8 stripping the kotlinx.serialization serializers — if `ProgressState.decode`
  returns defaults in release but works in debug, check `proguard-rules.pro`. Install the
  release APK, answer questions, force-stop, relaunch, confirm progress persisted.

---

## 4. Known issues and deliberate decisions

Do not "fix" these without reading why they are the way they are.

- **The streak can shrink retroactively.** A question stores only its most recent attempt,
  so re-answering an old one moves its day and that day leaves the set. This is the web's
  behaviour, matched deliberately so the two never disagree. `StatsTest` has a test named
  after it. Fixing it properly means recording study days explicitly and changing both
  clients plus the schema together.
- **Theme, notification prefs, onboarding and the in-flight session are device-local**, as
  is today's goal *count* (`goalDone`/`goalDate`). The goal *target* syncs.
- **`app/google-services.json` is git-ignored**, matching the web repo's treatment of
  `js/firebase-config.js`. A fresh clone must re-download it or the build fails.
- **`Stats.PASS_PCT` is 75** and the mock pass rule is the literal "15 correct", so a
  banner can only be asserted against a full 20-question mock.

---

## 5. Environment

Recorded because several hours were lost to these.

- **The emulator is unstable.** `citizenship_api36` has crashed its `system_server` three
  times mid-run (`INSTRUMENTATION_ABORTED: System has crashed`, or later
  `Can't find service: package`). **Run instrumented tests in two halves** and treat an
  abort as infrastructure until a per-class rerun reproduces it. Recovery: `adb emu kill`,
  relaunch with `-no-snapshot -memory 3072`, wait for `service check package`, then
  re-apply the three animation scales = 0 — they do not survive a restart and Espresso
  needs them.
- **AGP 9 dropped `--tests`** on `connectedAndroidTest`. Filter with
  `-Pandroid.testInstrumentationRunnerArguments.class=a,b,c`.
- **A tap that does nothing, with no crash, is probably an inset bug.** Hit-test the click
  point — walk the decor view for the topmost view containing it. If it returns
  `navigationBarBackground`, the row is parked under the system bar. Fixed once already in
  `fce1763`; the rule is that a scroll container must end above the bar, so the bottom
  inset goes on the screen root, not on the scroll view's padding.
- Toolchain is AGP 9.3.1 / Gradle 9.5 / Kotlin 2.2.10 on Temurin 21.
