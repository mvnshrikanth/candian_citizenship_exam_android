# Citizenship Coach — Android Native App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app for the Canadian Citizenship Exam study tool, implementing the "Quiet Card" Android design from the Claude Design project pixel-faithfully, running fully offline on the real 501-question bank.

**Architecture:** Single-Activity + Navigation Component with one Fragment per screen (XML Views + ViewBinding). One activity-scoped `AppViewModel` exposes `StateFlow`s that Fragments render; all study logic lives in pure-Kotlin `domain/` objects so it is unit-testable without Android. Persistence is a single JSON blob in Preferences DataStore (debounced writes). The question bank ships as a bundled asset — no network at runtime.

**Tech Stack:** Kotlin, XML Views + ViewBinding, Material Components for Android 1.12 (Material 3 theming), Navigation Component, Preferences DataStore, kotlinx.serialization, WorkManager, JUnit4 + Espresso.

**Spec:** Claude Design project `2087e144-c0e6-44d3-807f-a59a927a9eab`
- `Citizenship Android Screen.dc.html` — all 20 screens' markup (the visual spec)
- `Citizenship Android.dc.html` — the `DCLogic` class (the behavioural spec)
- `data/bank.json` — the design's derived bank; **the only source of the 7-topic taxonomy**
- `data/explanations.json` — the 12 authored explanations
- `android-frame.jsx` — canvas-only device mock. **Not app content. Do not port it.**

**Question source of truth:** `data/questions.json` on `main` in `github.com/mvnshrikanth/candian_citizenship_exam` (private).

Read remote spec files with the `DesignSync` tool: `method: "get_file"`, `projectId: "2087e144-c0e6-44d3-807f-a59a927a9eab"`, `path: "<path>"`.

---

## Context

The web app already exists at `github.com/mvnshrikanth/candian_citizenship_exam` (Bootstrap + vanilla JS: `index.html`, `css/styles.css`, `js/app.js`, `js/quiz.js`, `js/progress.js`, `data/questions.json`). This plan does **not** touch it. It builds a separate native Android app in this repo (`D:\projects\Canadian_Citizenship_Exam_Android`, currently empty apart from `.idea/` and `README.md`), driven by the Android-native design that was authored in Claude Design against that web app's data and behaviour.

### Where the question data comes from

The bundled asset is **built by joining two sources**, not copied from either:

- **`data/questions.json` in the web repo** is the authoritative question text, options, answer keys and raw `category`. It is the file to trust for content.
- **`data/bank.json` in the design project** is the only place the **7-topic taxonomy** exists. The design mapped the repo's 9 raw categories down to the 7 topics the Android design is built around (Topics screen, topic detail, per-topic accuracy). Six categories map cleanly, but three compound ones were split **per question**:

| Raw `category` in `questions.json` | Mapped `topic` |
|---|---|
| `Rights & Responsibilities` | `rights` (all 38) |
| `Indigenous Peoples & Communities` | `indigenous` (all 61) |
| `History` | `history` (all 100) |
| `Symbols & Modern Canada` | `symbols` (all 60) |
| `Government` | `government` (all 40) |
| `Government & Justice` | `government` (all 55) |
| `Government & Economy` (52) | split: government 21, geography 8, symbols 6, economy 5, rights 5, history 4, indigenous 3 |
| `History & Geography` (50) | split: history 26, geography 15, government 7, symbols 2 |
| `Regions & Geography` (45) | split: history 16, economy 9, geography 9, government 8, symbols 3 |

Those three compound categories cover **147 questions** whose topic cannot be recomputed from the category. It survives only as per-question data in `bank.json`. Both files use the same 501 ids, so the join is a straight `id → topic` lookup.

### Known data state

As of the design project's last sync (**2026-08-12**), its copy of `questions.json` had **question 4 at `answer: 0` → "1867"**, which is wrong — the Charter was entrenched in **1982** (index 2), as question 11 in the same bank states. The user reports the live repo bank is now fully updated, and the live file could not be read while planning (the repo is private and returns 404 unauthenticated, and `gh` is not installed on this machine).

So Task 2 treats this as **verify, don't assume**: it patches question 4 only if the live file still has it wrong, reports which branch it took, and asserts the final asset is correct either way. The same applies to the miscategorisation note in the design project's `github.md` — Task 2 reports any id it cannot map rather than guessing.

## Global Constraints

- **minSdk 26, targetSdk 36, compileSdk 36.** minSdk 26 is required — `android:fontVariationSettings` (variable-font weights) is API 26+.
- **AGP 8.13.0, Gradle 8.14, Kotlin 2.1.20.** Run Gradle on **Temurin JDK 21**. The machine has JDK 26 on PATH and Android Studio's bundled JBR is JDK 25; neither is supported by this AGP/Gradle pair. Task 1 installs JDK 21 and pins it via `org.gradle.java.home`.
- **Package / applicationId:** `com.mvnsh.citizenship`
- **No network permission.** The app declares no `INTERNET` permission. Bank and explanations are bundled assets.
- **Question identity is `id`, never list index.** IDs run 1–502 with id 39 absent (501 questions total). Any map, bookmark list, or session must key on `id`.
- **Copy is verbatim from the design.** Every user-facing string in this plan is quoted from `Citizenship Android Screen.dc.html`. Do not paraphrase, do not "improve" wording, do not add punctuation. Note the design uses `·` (U+00B7 middle dot) as its separator and `—` (em dash) in prose; both must survive into `strings.xml`.
- **Colour is only ever referenced through a theme attribute or a named colour resource.** No raw hex in any layout file. Both `values/colors.xml` and `values-night/colors.xml` must define every token in Task 1's table.
- **Every screen handles window insets.** targetSdk 36 means edge-to-edge is enforced on Android 15+. Every fragment root applies top inset as padding; the bottom nav and any bottom-docked CTA apply the bottom inset.
- **Test bar:** all pure logic (`domain/`, serialization, bank parsing) is covered by JVM unit tests written **before** the implementation. Screens are covered by Espresso tests that assert the states the design distinguishes (e.g. revealed vs unrevealed option rows), not pixel positions.

---

## Design Token Reference

Every task below refers to these names. Define them once in Task 1.

### Colours

The light values in the design are exactly Material Theme Builder output for a `#8F4A3C` primary seed with a `#5C6146` sage secondary. The dark column is that same tonal palette's dark scheme — derived, not invented.

| Token (`colors.xml` name) | Light | Dark | Used for |
|---|---|---|---|
| `md_primary` | `#8F4A3C` | `#FFB5A0` | Primary CTAs, progress fill, links, accent numerals |
| `md_on_primary` | `#FFFFFF` | `#561F0F` | Text on primary CTAs |
| `md_primary_container` | `#FFDBD1` | `#723528` | Goal card, Progress hero card, nav pill, selected chips |
| `md_on_primary_container` | `#723528` | `#FFDBD1` | Text/icons on the above; all overline labels |
| `md_secondary` | `#5C6146` | `#C5CBB0` | Sage: coverage bars, correct badge fill, mock submit |
| `md_on_secondary` | `#FFFFFF` | `#2E3420` | Text on sage fills |
| `md_secondary_container` | `#E3E8D3` | `#444B35` | Mock-test tile, topic badge, "correct" option row, pass banner |
| `md_on_secondary_container` | `#3E4530` | `#C5CBB0` | Secondary-weight text on sage containers |
| `md_on_secondary_container_strong` | `#1A1F0C` | `#E3E8D3` | Headline text on sage containers |
| `md_error` | `#BA1A1A` | `#FFB4AB` | Destructive actions, wrong markers, failed mock % |
| `md_on_error` | `#FFFFFF` | `#690005` | Text on error fills |
| `md_error_container` | `#FFDAD6` | `#93000A` | Wrong option row, time-low timer pill |
| `md_on_error_container` | `#93000A` | `#FFDAD6` | "You said:" text, "Your pick" badge |
| `md_surface` | `#FFF8F6` | `#1A1110` | Screen background, sheets, dialogs |
| `md_on_surface` | `#231917` | `#F1DFDB` | Primary text |
| `md_on_surface_variant` | `#534341` | `#D8C2BC` | Secondary text, unselected nav labels |
| `md_text_muted` | `#6F5D59` | `#BCA6A1` | Meta text (`11.5sp` captions, row status lines) |
| `md_outline` | `#85736F` | `#A08C87` | Option letter rings, unmarked flag stroke |
| `md_outline_variant` | `#E4D3CE` | `#534341` | 1dp card strokes, dividers, sheet handle |
| `md_surface_container_lowest` | `#FFFFFF` | `#140C0B` | Bordered card fill, grouped-list row fill |
| `md_surface_container` | `#F5E9E5` | `#271D1B` | Stat tiles, bottom nav, search field, secondary buttons |
| `md_surface_container_high` | `#EFDFDA` | `#322825` | Progress-bar tracks, "off" switch track |
| `md_inverse_surface` | `#372F2D` | `#F1DFDB` | Snackbar background |
| `md_inverse_on_surface` | `#F5E9E5` | `#372F2D` | Snackbar text |
| `md_scrim_32` | `#52231917` | `#52000000` | Sheet/dialog scrim (32% alpha) |
| `md_goal_track` | `#2E723528` | `#2EFFDBD1` | Goal bar track (18% of on-primary-container) |

Wire these into the theme as `colorPrimary`, `colorOnPrimary`, `colorPrimaryContainer`, `colorOnPrimaryContainer`, `colorSecondary`, `colorOnSecondary`, `colorSecondaryContainer`, `colorOnSecondaryContainer`, `colorError`, `colorOnError`, `colorErrorContainer`, `colorOnErrorContainer`, `android:colorBackground`, `colorSurface`, `colorOnSurface`, `colorOnSurfaceVariant`, `colorOutline`, `colorOutlineVariant`, `colorSurfaceContainerLowest`, `colorSurfaceContainer`, `colorSurfaceContainerHigh`, `colorSurfaceInverse`, `colorOnSurfaceInverse`. The four tokens with no M3 attribute (`md_on_secondary_container_strong`, `md_text_muted`, `md_goal_track`, `md_scrim_32`) are referenced directly as `@color/...` — they resolve per-theme via `values-night`.

### Typography

Two bundled variable fonts. `Serif` = `@font/roboto_serif`, `Flex` = `@font/roboto_flex`. Weight is set with `android:fontVariationSettings="'wght' N"`.

| Style name | Font | Size | Weight | Extras |
|---|---|---|---|---|
| `Text.Display.Hero` | Serif | 40sp | 600 | lineSpacingMultiplier 1.05, letterSpacing -0.025 |
| `Text.Display.Score` | Serif | 64sp | 600 | lineSpacingMultiplier 0.9, letterSpacing -0.03, `md_primary` |
| `Text.Display.Accuracy` | Serif | 52sp | 600 | letterSpacing -0.03, `md_on_primary_container` |
| `Text.Headline.Screen` | Serif | 27sp | 600 | letterSpacing -0.015 |
| `Text.Headline.Section` | Serif | 29sp | 600 | lineSpacingMultiplier 1.15, letterSpacing -0.02 |
| `Text.Headline.Dialog` | Serif | 22sp | 600 | lineSpacingMultiplier 1.2 |
| `Text.Headline.Empty` | Serif | 21sp | 600 | empty-state titles |
| `Text.Question` | Serif | 25sp | 500 | lineSpacingMultiplier 1.3, letterSpacing -0.01 |
| `Text.Stat.XL` / `.L` / `.M` / `.S` | Serif | 24 / 22 / 21 / 19sp | 600 | tile numerals |
| `Text.Stat.Inline` | Serif | 17sp | 600 | `md_primary` — row-trailing counts |
| `Text.Title.Card` | Flex | 16sp | 600 | card and app-bar titles |
| `Text.Title.Row` | Flex | 15sp | 600 | settings rows |
| `Text.Option` | Flex | 15.5sp | 400 | lineSpacingMultiplier 1.4 |
| `Text.Body` | Flex | 14.5sp | 400 | lineSpacingMultiplier 1.45 |
| `Text.Body.Loose` | Flex | 14.5sp | 400 | lineSpacingMultiplier 1.55 — prose paragraphs |
| `Text.Body.Small` | Flex | 13sp | 400 | `md_on_surface_variant` |
| `Text.Meta` | Flex | 12.5sp | 400 | `md_on_surface_variant` |
| `Text.Meta.Small` | Flex | 11.5sp | 400 | `md_text_muted` |
| `Text.Overline` | Flex | 11.5sp | 700 | letterSpacing 0.07, `textAllCaps`, `md_on_primary_container` |
| `Text.Badge` | Flex | 11sp | 700 | letterSpacing 0.06, `textAllCaps` |
| `Text.Nav` | Flex | 11.5sp | 400/600 | 600 + `md_on_primary_container` when selected |
| `Text.Cta` | Flex | 16sp | 600 | pill buttons |
| `Text.Cta.Large` | Flex | 16.5sp | 600 | onboarding CTAs |

Timer and question-counter text additionally set `android:fontFeatureSettings="tnum"` (tabular numerals — the design uses `font-variant-numeric: tabular-nums`).

### Shape and spacing

| Token | Value | Applies to |
|---|---|---|
| `radius_pill` | 100dp | all CTAs, chips, search field, timer pill, nav pill |
| `radius_xl` | 28dp | goal card, home hero CTA, Progress hero, sheet top corners, dialogs |
| `radius_lg` | 20dp | topic-detail header card, mock-result banner, onboarding app icon (also 20dp) |
| `radius_md` | 16dp | most cards, stat tiles, option rows, grouped lists |
| `radius_sm` | 14dp | navigator grid chips |
| `radius_xs` | 12dp | topic-detail question rows, mock-attempt rows, snackbar |
| `radius_badge` | 8dp | topic badge, mock-percentage badge, skeleton bars |
| `screen_pad_h` | 16dp | every screen's horizontal padding (onboarding: 22dp) |
| `screen_pad_top` | 12dp | (results/mock-results: 16dp; onboarding: 24dp) |
| `screen_pad_bottom` | 20dp | (onboarding: 26dp) |
| `gap_section` | 14dp | between home/progress cards |
| `gap_list` | 8dp | between list rows (topics list and quiz options use 10dp) |
| `pad_card` | 16dp | standard card padding (20dp for `radius_xl` cards, 18dp for topic header / mock intro, 15dp for list rows) |

A "bordered card" throughout the design is `background: #FFF` + `inset 0 0 0 1px #E4D3CE`. Implement as `MaterialCardView` with `cardBackgroundColor=?attr/colorSurfaceContainerLowest`, `strokeWidth=1dp`, `strokeColor=?attr/colorOutlineVariant`, `cardElevation=0dp`. Define this once as `style="@style/Card.Bordered"`.

A "grouped list" (mock intro facts, Settings Data/About blocks) is a `radius_md` container filled with `md_outline_variant` and children of `md_surface_container_lowest` separated by 1dp gaps — the divider *is* the parent background showing through. Reproduce with a `LinearLayout` whose background is a `radius_md` shape of `colorOutlineVariant` and `1dp` vertical gaps between opaque children.

### Motion

| Name | Spec | Applies to |
|---|---|---|
| `rise` | 220ms, translationY 10dp→0 + alpha 0→1, `PathInterpolator(0.2, 0, 0, 1)` | screen enter |
| `rise_sheet` | 260ms, same curve | bottom sheet, dialogs |
| `pop` | 260ms, scale 0.94 → 1.02 → 1.0 | correct/incorrect marker reveal |
| `fade` | 160ms alpha | scrims |
| bar growth | 320ms (quiz) / 420ms (goal) width animation, same curve | progress bars |

Define `PathInterpolator(0.2f, 0f, 0f, 1f)` once as `Motion.STANDARD` in a `ui/common/Motion.kt`.

### Vector drawables to author

Port these from the design's inline SVGs (all `viewBox="0 0 24 24"`; stroke icons use `strokeWidth=2`, `strokeLineCap=round`, and `?attr/colorOnSurfaceVariant` unless stated). Name them `ic_<name>.xml`:

`ic_search`, `ic_settings`, `ic_flame` (filled), `ic_chevron_right`, `ic_arrow_left`, `ic_warning`, `ic_clock`, `ic_calendar`, `ic_flag_outline`, `ic_flag_filled`, `ic_check`, `ic_close`, `ic_list` (3-line, also the Practice nav icon), `ic_book` (Topics nav), `ic_bar_chart` (Progress nav), `ic_home`, `ic_grid` (navigator button), `ic_download_done`, `ic_open_in_new`, `ic_wifi_off`.

Exact paths are in the spec file; the four bottom-nav icons appear twice (filled/selected and stroked/unselected) — author each once and switch tint + the pill background via a selector on the nav item.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/assets/
  bank.json                      501 questions, bundled
  explanations.json              12 entries
app/src/main/java/com/mvnsh/citizenship/
  CitizenshipApp.kt              Application: DataStore + notification channel init
  MainActivity.kt                NavHost + BottomNavigationView + insets + snackbar host
  data/
    model/Question.kt            Question, Explanation
    model/ProgressState.kt       ProgressState, SeenStat, MockAttempt, NotifPrefs, SessionState
    BankRepository.kt            assets -> List<Question> + Map<Int, Explanation>
    ProgressRepository.kt        DataStore blob, debounced writes, mutate {} helper
  domain/
    Topics.kt                    the 7 topics (key, name, blurb) verbatim
    DateUtils.kt                 iso/today/shiftDay/fmtDate/daysTo/mmss/clip
    StudyEngine.kt               session building, sampling, option order, weak ids
    Stats.kt                     topic stats, accuracy, achievements, streak/goal transitions
  ui/
    AppViewModel.kt              single activity-scoped VM; StateFlow<UiState>
    common/Motion.kt             interpolators + rise/pop helpers
    common/Insets.kt             applyTopInset / applyBottomInset extensions
    common/Snack.kt              design-styled snackbar
    common/BaseDialogFragment.kt shared rounded-28dp dialog scaffold
    onboarding/                  OnboardingFragment + 3 page fragments
    home/HomeFragment.kt
    practice/PracticeFragment.kt, PracticeModeAdapter.kt
    topics/TopicsFragment.kt, TopicRowAdapter.kt
    topics/TopicDetailFragment.kt, TopicQuestionAdapter.kt
    quiz/QuizFragment.kt, OptionAdapter.kt, OptionStyle.kt
    quiz/ResultsFragment.kt, MissedAdapter.kt
    mock/MockIntroFragment.kt, MockFragment.kt, MockResultsFragment.kt
    mock/MockAttemptAdapter.kt, NavigatorSheet.kt, NavChipAdapter.kt
    review/ReviewFragment.kt, ReviewAdapter.kt
    lists/WeakFragment.kt, WeakAdapter.kt
    lists/BookmarksFragment.kt, BookmarkAdapter.kt
    search/SearchFragment.kt, SearchAdapter.kt
    progress/ProgressFragment.kt, TopicBarAdapter.kt, WeekBarsView.kt
    settings/SettingsFragment.kt, ExitSessionDialog.kt, ResetProgressDialog.kt, ClearBookmarksDialog.kt
  notify/Channels.kt, ReminderDecision.kt, ReminderWorker.kt, ReminderScheduler.kt
app/src/main/res/
  font/roboto_flex.ttf, roboto_serif.ttf
  values/{colors,themes,type,shapes,dimens,strings}.xml
  values-night/colors.xml
  drawable/                      ic_*.xml + shape backgrounds
  layout/                        fragment_*.xml, item_*.xml, dialog_*.xml, sheet_*.xml, view_*.xml
  navigation/nav_graph.xml
  menu/bottom_nav.xml
  xml/backup_rules.xml, xml/data_extraction_rules.xml
app/src/test/java/com/mvnsh/citizenship/       JVM unit tests
app/src/androidTest/java/com/mvnsh/citizenship/ Espresso + DataStore instrumented tests
docs/superpowers/plans/2026-08-18-android-native-app.md   (this plan, committed in Task 1)
```

### Design decisions and deliberate deviations

State these in the Task 1 commit message so they are on the record:

1. **`android-frame.jsx` is not ported.** It is a canvas device mock (fake status bar, fake Gboard). The real OS provides all of it.
2. **The canvas props are dropped.** `startScreen` and `gamification` (`Full`/`Quiet`/`Off`) were design-exploration knobs, not user settings. Ship the `Full` behaviour: streak counter and milestones both visible.
3. **The home avatar becomes a settings icon.** The design shows a `#FFDBD1` circle containing the letter `A`. There is no account and no user name in this app, so a letter is meaningless. Use `ic_settings` tinted `md_on_primary_container` on the same circle, same 44dp size, same tap target and destination.
4. **The greeting is time-aware.** The design hardcodes `"Good morning"`. Ship `Good morning` / `Good afternoon` / `Good evening` split at 12:00 and 18:00 local.
5. **The active session is persisted.** The design's exit dialog promises "Your place is kept". In a browser tab that holds; on Android the process is killed routinely. So `SessionState` is stored in the DataStore blob, and the mock timer stores an absolute `deadlineEpochMs` so "the timer keeps running if you leave the app" (the design's own words on the mock intro screen) is literally true.
6. **The demo-data seeder is debug-only.** The canvas has a "Demo data ↔ Fresh install" toggle. It is genuinely useful for QA, so it ships as a Settings row visible only when `BuildConfig.DEBUG`.
7. **Dark theme is real.** The design is light-only and its own Settings copy says "Dark theme ships in the next build". Per the decision for this build, a full dark scheme is derived from the same tonal palette (table above) and the theme selector actually switches. The "ships in the next build" snackbar is therefore **removed** — do not port it.
8. **The week chart rolls.** The design stores a fixed seven-slot array, always increments slot 6, and labels the slots `M T W T F S S` regardless of the actual day — so the bars never shift and the labels are wrong six days out of seven. Task 13 anchors the window to a date and derives labels from it.
9. **The error-state copy changes, because the data model did.** The design says "The bank is stored on your device after the first download — reconnect once and it stays available offline." This app bundles the bank as an asset and declares no `INTERNET` permission, so there is no download and nothing to reconnect to. Task 16 substitutes honest copy in the same voice. Everywhere else, copy stays verbatim.

---

## Task 1: Project scaffold, theme tokens, fonts

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/{colors,themes,type,shapes,dimens,strings}.xml`, `app/src/main/res/values-night/colors.xml`
- Create: `app/src/main/res/font/roboto_flex.ttf`, `app/src/main/res/font/roboto_serif.ttf`
- Create: `app/src/main/java/com/mvnsh/citizenship/{CitizenshipApp.kt,MainActivity.kt}`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/mvnsh/citizenship/ui/common/{Motion.kt,Insets.kt}`
- Create: `docs/superpowers/plans/2026-08-18-android-native-app.md` (copy of this plan)
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/LaunchTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `applicationId com.mvnsh.citizenship`; theme `Theme.Citizenship`; every colour/type/shape/dimen token from the Design Token Reference; `Motion.STANDARD: PathInterpolator`; `View.applyTopInset()`, `View.applyBottomInset()`; `MainActivity` hosting a `FragmentContainerView` with id `nav_host` and a `BottomNavigationView` with id `bottom_nav`.

- [ ] **Step 1: Install a supported JDK and confirm it**

The machine has JDK 26 on PATH and Android Studio's JBR is JDK 25. Gradle 8.14 supports neither.

```bash
winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
ls "/c/Program Files/Eclipse Adoptium"
```

Expected: a `jdk-21.*-hotspot` directory. Note its exact name — it goes into `gradle.properties` in Step 3.

- [ ] **Step 2: Download the two variable fonts**

```bash
mkdir -p app/src/main/res/font
curl -L -o app/src/main/res/font/roboto_flex.ttf \
  "https://github.com/google/fonts/raw/main/ofl/robotoflex/RobotoFlex%5BGRAD%2CXOPQ%2CXTRA%2CYOPQ%2CYTAS%2CYTDE%2CYTFI%2CYTLC%2CYTUC%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
curl -L -o app/src/main/res/font/roboto_serif.ttf \
  "https://github.com/google/fonts/raw/main/ofl/robotoserif/RobotoSerif%5BGRAD%2Copsz%2Cwdth%2Cwght%5D.ttf"
ls -la app/src/main/res/font/
```

Expected: both files present and larger than 100 KB. If either URL 404s (Google Fonts reorganises paths), fetch from `https://fonts.google.com/specimen/Roboto+Flex` and `https://fonts.google.com/specimen/Roboto+Serif` and save under these exact filenames — the filenames are what the rest of the plan references.

- [ ] **Step 3: Write the Gradle build**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.0"
kotlin = "2.1.20"
coreKtx = "1.15.0"
appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.2.1"
recyclerview = "1.4.0"
navigation = "2.8.9"
lifecycle = "2.8.7"
datastore = "1.1.3"
serialization = "1.8.0"
work = "2.10.0"
coroutines = "1.10.1"
junit = "4.13.2"
androidxJunit = "1.2.1"
espresso = "3.6.1"
fragmentTesting = "1.8.6"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
material = { module = "com.google.android.material:material", version.ref = "material" }
constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
recyclerview = { module = "androidx.recyclerview:recyclerview", version.ref = "recyclerview" }
navigation-fragment = { module = "androidx.navigation:navigation-fragment-ktx", version.ref = "navigation" }
navigation-ui = { module = "androidx.navigation:navigation-ui-ktx", version.ref = "navigation" }
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
espresso-contrib = { module = "androidx.test.espresso:espresso-contrib", version.ref = "espresso" }
fragment-testing = { module = "androidx.fragment:fragment-testing", version.ref = "fragmentTesting" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
navigation-safeargs = { id = "androidx.navigation.safeargs.kotlin", version.ref = "navigation" }
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CitizenshipCoach"
include(":app")
```

`build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.navigation.safeargs) apply false
}
```

`gradle.properties` — substitute the exact directory name from Step 1:

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.6.7-hotspot
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.mvnsh.citizenship"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mvnsh.citizenship"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.coroutines.test)
    debugImplementation(libs.fragment.testing)
}
```

`app/proguard-rules.pro` — kotlinx.serialization needs its generated serializers kept:

```proguard
-keepclassmembers class com.mvnsh.citizenship.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.mvnsh.citizenship.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

- [ ] **Step 4: Generate the Gradle wrapper and confirm it runs**

```bash
"/c/Program Files/Eclipse Adoptium/jdk-21.0.6.7-hotspot/bin/java" -version
gradle wrapper --gradle-version 8.14 || echo "no system gradle - see note"
./gradlew --version
```

If no system `gradle` is available to bootstrap the wrapper, create `gradle/wrapper/gradle-wrapper.properties` by hand with `distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip` and copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` from Android Studio's template, or run the project's first sync from Android Studio which generates them.

Expected from `./gradlew --version`: `Gradle 8.14`, and `Launcher JVM: 21.x`. **If the JVM line does not say 21, stop and fix `org.gradle.java.home` before continuing** — every later task's build depends on it.

- [ ] **Step 5: Write the colour tokens**

`app/src/main/res/values/colors.xml` — the Light column of the Design Token Reference table:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="md_primary">#8F4A3C</color>
    <color name="md_on_primary">#FFFFFF</color>
    <color name="md_primary_container">#FFDBD1</color>
    <color name="md_on_primary_container">#723528</color>
    <color name="md_secondary">#5C6146</color>
    <color name="md_on_secondary">#FFFFFF</color>
    <color name="md_secondary_container">#E3E8D3</color>
    <color name="md_on_secondary_container">#3E4530</color>
    <color name="md_on_secondary_container_strong">#1A1F0C</color>
    <color name="md_error">#BA1A1A</color>
    <color name="md_on_error">#FFFFFF</color>
    <color name="md_error_container">#FFDAD6</color>
    <color name="md_on_error_container">#93000A</color>
    <color name="md_surface">#FFF8F6</color>
    <color name="md_on_surface">#231917</color>
    <color name="md_on_surface_variant">#534341</color>
    <color name="md_text_muted">#6F5D59</color>
    <color name="md_outline">#85736F</color>
    <color name="md_outline_variant">#E4D3CE</color>
    <color name="md_surface_container_lowest">#FFFFFF</color>
    <color name="md_surface_container">#F5E9E5</color>
    <color name="md_surface_container_high">#EFDFDA</color>
    <color name="md_inverse_surface">#372F2D</color>
    <color name="md_inverse_on_surface">#F5E9E5</color>
    <color name="md_scrim_32">#52231917</color>
    <color name="md_goal_track">#2E723528</color>
</resources>
```

`app/src/main/res/values-night/colors.xml` — same 26 names, the Dark column:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="md_primary">#FFB5A0</color>
    <color name="md_on_primary">#561F0F</color>
    <color name="md_primary_container">#723528</color>
    <color name="md_on_primary_container">#FFDBD1</color>
    <color name="md_secondary">#C5CBB0</color>
    <color name="md_on_secondary">#2E3420</color>
    <color name="md_secondary_container">#444B35</color>
    <color name="md_on_secondary_container">#C5CBB0</color>
    <color name="md_on_secondary_container_strong">#E3E8D3</color>
    <color name="md_error">#FFB4AB</color>
    <color name="md_on_error">#690005</color>
    <color name="md_error_container">#93000A</color>
    <color name="md_on_error_container">#FFDAD6</color>
    <color name="md_surface">#1A1110</color>
    <color name="md_on_surface">#F1DFDB</color>
    <color name="md_on_surface_variant">#D8C2BC</color>
    <color name="md_text_muted">#BCA6A1</color>
    <color name="md_outline">#A08C87</color>
    <color name="md_outline_variant">#534341</color>
    <color name="md_surface_container_lowest">#140C0B</color>
    <color name="md_surface_container">#271D1B</color>
    <color name="md_surface_container_high">#322825</color>
    <color name="md_inverse_surface">#F1DFDB</color>
    <color name="md_inverse_on_surface">#372F2D</color>
    <color name="md_scrim_32">#52000000</color>
    <color name="md_goal_track">#2EFFDBD1</color>
</resources>
```

- [ ] **Step 6: Write the theme, type, shape and dimen resources**

`values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Citizenship" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/md_primary</item>
        <item name="colorOnPrimary">@color/md_on_primary</item>
        <item name="colorPrimaryContainer">@color/md_primary_container</item>
        <item name="colorOnPrimaryContainer">@color/md_on_primary_container</item>
        <item name="colorSecondary">@color/md_secondary</item>
        <item name="colorOnSecondary">@color/md_on_secondary</item>
        <item name="colorSecondaryContainer">@color/md_secondary_container</item>
        <item name="colorOnSecondaryContainer">@color/md_on_secondary_container</item>
        <item name="colorError">@color/md_error</item>
        <item name="colorOnError">@color/md_on_error</item>
        <item name="colorErrorContainer">@color/md_error_container</item>
        <item name="colorOnErrorContainer">@color/md_on_error_container</item>
        <item name="android:colorBackground">@color/md_surface</item>
        <item name="colorSurface">@color/md_surface</item>
        <item name="colorOnSurface">@color/md_on_surface</item>
        <item name="colorOnSurfaceVariant">@color/md_on_surface_variant</item>
        <item name="colorOutline">@color/md_outline</item>
        <item name="colorOutlineVariant">@color/md_outline_variant</item>
        <item name="colorSurfaceContainerLowest">@color/md_surface_container_lowest</item>
        <item name="colorSurfaceContainer">@color/md_surface_container</item>
        <item name="colorSurfaceContainerHigh">@color/md_surface_container_high</item>
        <item name="colorSurfaceInverse">@color/md_inverse_surface</item>
        <item name="colorOnSurfaceInverse">@color/md_inverse_on_surface</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:fontFamily">@font/roboto_flex</item>
        <item name="android:textColor">@color/md_on_surface</item>
    </style>
</resources>
```

Add `app/src/main/res/values-night/themes.xml` overriding only `android:windowLightStatusBar` to `false`.

`values/type.xml` — every style from the Typography table. Two worked examples; author the remaining 26 identically:

```xml
<style name="Text.Question" parent="">
    <item name="android:fontFamily">@font/roboto_serif</item>
    <item name="android:fontVariationSettings">'wght' 500</item>
    <item name="android:textSize">25sp</item>
    <item name="android:lineSpacingMultiplier">1.3</item>
    <item name="android:letterSpacing">-0.01</item>
    <item name="android:textColor">@color/md_on_surface</item>
</style>

<style name="Text.Overline" parent="">
    <item name="android:fontFamily">@font/roboto_flex</item>
    <item name="android:fontVariationSettings">'wght' 700</item>
    <item name="android:textSize">11.5sp</item>
    <item name="android:letterSpacing">0.07</item>
    <item name="android:textAllCaps">true</item>
    <item name="android:textColor">@color/md_on_primary_container</item>
</style>
```

`values/shapes.xml` defines `Card.Bordered` plus `ShapeAppearance.Pill/Xl/Lg/Md/Sm/Xs/Badge`. `values/dimens.xml` defines every name from the Shape and spacing table.

- [ ] **Step 7: Write `Motion.kt` and `Insets.kt`**

```kotlin
// ui/common/Motion.kt
package com.mvnsh.citizenship.ui.common

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.PathInterpolator

object Motion {
    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** The design's `rise` keyframes: 10dp up + fade in over 220ms. */
    fun rise(view: View, durationMs: Long = 220L) {
        val dy = 10f * view.resources.displayMetrics.density
        view.translationY = dy
        view.alpha = 0f
        view.animate()
            .translationY(0f).alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(STANDARD)
            .start()
    }

    /** The design's `pop` keyframes: 0.94 -> 1.02 -> 1.0 over 260ms. */
    fun pop(view: View) {
        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.02f, 1f)
        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.02f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(view, sx, sy).apply {
            duration = 260L
            interpolator = STANDARD
        }.start()
    }
}
```

```kotlin
// ui/common/Insets.kt
package com.mvnsh.citizenship.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun View.applyTopInset() {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(top = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
        insets
    }
}

fun View.applyBottomInset() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(bottom = base + maxOf(bars.bottom, ime.bottom))
        insets
    }
}
```

- [ ] **Step 8: Write the manifest, Application and MainActivity**

`AndroidManifest.xml` — note there is **no `INTERNET` permission**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".CitizenshipApp"
        android:allowBackup="true"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Citizenship">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`MainActivity.kt` for this task is a stub: `enableEdgeToEdge()`, inflate `activity_main.xml`, and nothing else. `activity_main.xml` is a `CoordinatorLayout` containing a `FragmentContainerView` (`android:id="@+id/nav_host"`, `app:defaultNavHost="true"`) above a `BottomNavigationView` (`android:id="@+id/bottom_nav"`). The nav graph is wired in Task 5.

`CitizenshipApp.kt` for this task is an empty `Application` subclass.

- [ ] **Step 9: Write the launch test**

```kotlin
package com.mvnsh.citizenship

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @Test fun activity_launches_and_inflates_host() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<android.view.View>(R.id.nav_host))
                assertNotNull(activity.findViewById<android.view.View>(R.id.bottom_nav))
            }
        }
    }
}
```

- [ ] **Step 10: Build and run the test**

```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.LaunchTest"
```

Expected: `assembleDebug` succeeds; the instrumented test passes on a running emulator or device. If no device is attached, start one first (`emulator -list-avds`, then `emulator -avd <name>`). If the theme fails to resolve `colorSurfaceContainerLowest`, confirm Material is at 1.12.0 or newer — those roles do not exist in 1.9.

- [ ] **Step 11: Commit**

```bash
mkdir -p docs/superpowers/plans
cp "C:/Users/mvnsh/.claude/plans/structured-dazzling-whale.md" docs/superpowers/plans/2026-08-18-android-native-app.md
git add -A
git commit -m "chore: scaffold Android app with Quiet Card design tokens

Gradle 8.14 / AGP 8.13 on Temurin 21, minSdk 26, Material 3 theming.
Light and dark colour tokens, 28 type styles, shape and motion tokens
derived from the Claude Design 'Quiet Card' Android spec.

Deliberate deviations from the design, recorded here:
- android-frame.jsx not ported (canvas device mock, OS provides it)
- startScreen/gamification canvas props dropped; ships as 'Full'
- home letter avatar -> settings icon (no account exists in this app)
- greeting is time-aware, not hardcoded 'Good morning'
- active session and mock deadline are persisted, honouring the
  design's own 'your place is kept' / 'timer keeps running' copy
- demo-data seeder ships as a debug-only Settings row
- dark theme is real, so the 'ships in the next build' snackbar is gone
"
```

---

## Task 2: Bank data model and repository

**Files:**
- Create: `build-data/{questions.json,bank.json}` — git-ignored upstream inputs, not shipped
- Create: `app/src/main/assets/bank.json` (built by joining the two), `app/src/main/assets/explanations.json` (copied unchanged)
- Modify: `.gitignore` (add `build-data/`), `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/mvnsh/citizenship/data/model/Question.kt`
- Create: `app/src/main/java/com/mvnsh/citizenship/data/BankRepository.kt`
- Create: `app/src/main/java/com/mvnsh/citizenship/domain/Topics.kt`
- Test: `app/src/test/java/com/mvnsh/citizenship/data/BankDataTest.kt`
- Test: `app/src/test/resources/bank.json`, `app/src/test/resources/explanations.json` (symlink or copy of the assets)

**Interfaces:**
- Consumes: Task 1's Gradle config (kotlinx.serialization plugin).
- Produces:
  - `data class Question(val id: Int, val topic: String, val category: String, val difficulty: String, val question: String, val options: List<String>, val answer: Int)`
  - `data class Explanation(val why: String, val tip: String)`
  - `class BankRepository(context: Context)` with `suspend fun load(): Bank` and `data class Bank(val questions: List<Question>, val byId: Map<Int, Question>, val explanations: Map<Int, Explanation>)`
  - `object Topics` with `data class Topic(val key: String, val name: String, val blurb: String)`, `val all: List<Topic>` (7 entries, in the design's order), `fun name(key: String): String`

- [ ] **Step 1: Get the authoritative `questions.json` out of the private repo**

`gh` is not installed on this machine and the repo 404s unauthenticated, so this needs a one-time setup. Ask the user to run these in the session with the `!` prefix (interactive login cannot be automated):

```
! winget install --id GitHub.cli -e --accept-source-agreements --accept-package-agreements
! gh auth login
```

Then fetch the file:

```bash
gh api repos/mvnshrikanth/candian_citizenship_exam/contents/data/questions.json \
  --jq '.content' | base64 -d > build-data/questions.json
python -c "
import json; qs=json.load(open('build-data/questions.json',encoding='utf-8'))
print('questions:', len(qs)); print('ids:', min(q['id'] for q in qs), '-', max(q['id'] for q in qs))
q4=[q for q in qs if q['id']==4]
print('q4 answer:', q4[0]['answer'], '->', q4[0]['options'][q4[0]['answer']] if q4 else 'ABSENT')
"
```

`build-data/` is a git-ignored working directory — it holds the two upstream inputs, not shipped output.

**If `gh` auth cannot be completed**, fall back to the design project's synced copy (`DesignSync` `get_file`, path `data/questions.json`) and **say so explicitly in the commit message and to the user** — that copy is from 2026-08-12 and predates the user's reported updates. Do not present a fallback build as if it used the live data.

- [ ] **Step 2: Pull the topic map and the explanations from the design project**

Use `DesignSync` with `method: "get_file"`, `projectId: "2087e144-c0e6-44d3-807f-a59a927a9eab"`:
- path `data/bank.json` → write to `build-data/bank.json` (the `id → topic` source, not shipped as-is)
- path `data/explanations.json` → write straight to `app/src/main/assets/explanations.json` (shipped unchanged)

- [ ] **Step 3: Build the shipped asset by joining the two, and fix question 4 if needed**

```bash
mkdir -p app/src/main/assets
python - <<'PY'
import json, pathlib, collections

qs = json.loads(pathlib.Path("build-data/questions.json").read_text(encoding="utf-8"))
bank = json.loads(pathlib.Path("build-data/bank.json").read_text(encoding="utf-8"))
topic_by_id = {q["id"]: q["topic"] for q in bank}

# Six categories map cleanly. The three compound ones were split per question by the
# design and cannot be recomputed, so an id missing from bank.json falls back to its
# category's dominant topic and is reported for a human to assign.
CLEAN = {
    "Rights & Responsibilities": "rights",
    "Indigenous Peoples & Communities": "indigenous",
    "History": "history",
    "Symbols & Modern Canada": "symbols",
    "Government": "government",
    "Government & Justice": "government",
}
DOMINANT = {
    "Government & Economy": "government",
    "History & Geography": "history",
    "Regions & Geography": "history",
}

unmapped = []
out = []
for q in qs:
    topic = topic_by_id.get(q["id"])
    if topic is None:
        cat = q.get("category", "")
        topic = CLEAN.get(cat) or DOMINANT.get(cat)
        if topic is None:
            raise SystemExit(f"id {q['id']} has unknown category {cat!r} - add it to CLEAN/DOMINANT")
        unmapped.append((q["id"], cat, topic))
    out.append({
        "id": q["id"], "topic": topic, "category": q.get("category", ""),
        "difficulty": q.get("difficulty", ""), "question": q["question"],
        "options": q["options"], "answer": q["answer"],
    })

# Question 4: the Charter was entrenched in 1982. Patch only if upstream still has it wrong.
q4 = next((q for q in out if q["id"] == 4), None)
if q4 and q4["options"] == ["1867", "1921", "1982", "2015"]:
    if q4["answer"] != 2:
        q4["answer"] = 2
        print("PATCHED q4 -> 1982 (upstream still had it wrong)")
    else:
        print("q4 already correct upstream - no patch needed")
elif q4:
    print(f"q4 options changed upstream {q4['options']} - REVIEW BY HAND")

pathlib.Path("app/src/main/assets/bank.json").write_text(
    json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

print("shipped questions:", len(out))
print("topics:", dict(collections.Counter(q["topic"] for q in out)))
if unmapped:
    print(f"\n{len(unmapped)} ids were NOT in the design's topic map and used a category fallback.")
    print("Report these to the user - they need a real topic assigned:")
    for i, cat, t in unmapped:
        print(f"  id {i}: category {cat!r} -> guessed {t!r}")
else:
    print("every question's topic came from the design's map - no guesses")
PY
```

Expected on an unchanged bank: `shipped questions: 501`, the seven-topic counts `history 146, government 131, symbols 71, indigenous 64, rights 43, geography 32, economy 14`, and `no guesses`.

**If any id used a category fallback, surface that list to the user before continuing.** A wrong topic silently misfiles a question on the Topics screen and skews that topic's accuracy.

- [ ] **Step 4: Ignore the working directory**

Add `build-data/` to `.gitignore`. The upstream inputs are reproducible from Steps 1–2; only the built asset is committed.

- [ ] **Step 5: Write the failing data test**

The count assertion is a deliberate regression guard. If the live bank legitimately changes size, update the number here **and** derive the two UI strings that quote it (see Step 8) — never hardcode a stale count in copy.

```kotlin
package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.Explanation
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.domain.Topics
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDataTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun res(name: String) = checkNotNull(javaClass.classLoader!!.getResourceAsStream(name)) {
        "$name missing from test resources"
    }.bufferedReader().readText()

    private val bank: List<Question> = json.decodeFromString(res("bank.json"))
    private val explanations: Map<String, Explanation> = json.decodeFromString(res("explanations.json"))

    @Test fun bank_has_501_questions() {
        assertEquals(501, bank.size)
    }

    @Test fun every_question_has_four_options_and_an_in_range_answer() {
        bank.forEach { q ->
            assertEquals("q${q.id} option count", 4, q.options.size)
            assertTrue("q${q.id} answer out of range: ${q.answer}", q.answer in q.options.indices)
            assertTrue("q${q.id} has blank text", q.question.isNotBlank())
        }
    }

    @Test fun ids_are_unique_and_must_not_be_assumed_contiguous() {
        val ids = bank.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        // As shipped the bank runs 1..502 with 39 absent, so id != index. Anything keyed
        // by list position would silently shift for every question after 38. This asserts
        // the gap still exists so nobody "tidies" the data and hides the hazard; if the
        // upstream bank ever becomes contiguous, delete this assertion rather than
        // reintroducing index-based lookups anywhere.
        assertTrue("ids are expected to be sparse", ids.max() - ids.min() + 1 > ids.size)
    }

    @Test fun every_question_has_a_topic_from_the_designs_taxonomy() {
        // The 7-topic taxonomy is joined in from the design's bank.json at build time,
        // so a blank or unknown topic means the join in Task 2 Step 3 silently missed a row.
        val known = Topics.all.map { it.key }.toSet()
        bank.forEach { q ->
            assertTrue("q${q.id} has topic '${q.topic}' outside $known", q.topic in known)
        }
    }

    @Test fun question_4_answer_key_is_corrected_to_1982() {
        val q = bank.first { it.id == 4 }
        assertEquals("1982", q.options[q.answer])
    }

    @Test fun every_topic_key_is_known() {
        val known = Topics.all.map { it.key }.toSet()
        val unknown = bank.map { it.topic }.toSet() - known
        assertTrue("unknown topic keys: $unknown", unknown.isEmpty())
    }

    @Test fun all_seven_topics_are_populated() {
        val counts = bank.groupingBy { it.topic }.eachCount()
        Topics.all.forEach { t ->
            assertTrue("topic ${t.key} is empty", (counts[t.key] ?: 0) > 0)
        }
    }

    @Test fun explanation_keys_all_reference_real_questions() {
        val ids = bank.map { it.id }.toSet()
        explanations.keys.forEach { k ->
            val id = k.toIntOrNull()
            assertTrue("explanation key '$k' is not an int", id != null)
            assertTrue("explanation $id has no question", id in ids)
        }
    }
}
```

- [ ] **Step 6: Run it to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.data.BankDataTest"
```

Expected: compilation failure — `Question`, `Explanation` and `Topics` do not exist yet.

- [ ] **Step 7: Write the models and Topics**

```kotlin
// data/model/Question.kt
package com.mvnsh.citizenship.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: Int,
    val topic: String,
    val category: String = "",
    val difficulty: String = "",
    val question: String,
    val options: List<String>,
    val answer: Int,
)

@Serializable
data class Explanation(
    val why: String = "",
    val tip: String = "",
)
```

```kotlin
// domain/Topics.kt
package com.mvnsh.citizenship.domain

object Topics {

    data class Topic(val key: String, val name: String, val blurb: String)

    /** Verbatim from the design's TOPICS constant, in its order. */
    val all: List<Topic> = listOf(
        Topic("rights", "Rights & Responsibilities", "Rule of law, the Charter, the Oath"),
        Topic("indigenous", "Indigenous Peoples", "First Nations, Inuit and Métis histories"),
        Topic("history", "Canadian History", "Confederation, the wars, milestones"),
        Topic("symbols", "Symbols & Modern Canada", "Flag, anthem, sport and the arts"),
        Topic("government", "Government", "Parliament, elections, the courts"),
        Topic("economy", "Economy", "Trade, industry and the regions"),
        Topic("geography", "Geography", "Provinces, capitals, landscapes"),
    )

    private val byKey = all.associateBy { it.key }

    fun find(key: String): Topic? = byKey[key]

    /** Falls back to the raw key, matching the design's topicName(). */
    fun name(key: String): String = byKey[key]?.name ?: key
}
```

- [ ] **Step 8: Derive the two UI strings that quote the bank size**

The design hardcodes `501` twice — the search hint `Search 501 questions` and the Settings row `501 questions · works offline`. Hardcoding it means the copy silently lies the moment the bank changes. Declare them as format strings instead:

```xml
<string name="search_hint">Search %1$d questions</string>
<string name="bank_downloaded_sub">%1$d questions · works offline</string>
```

and fill them from `bank.questions.size` at render time in Task 12's `SearchFragment` and Task 14's `SettingsFragment`. With the current bank both render exactly the design's text.

- [ ] **Step 9: Copy the assets into test resources**

The unit test reads from the classpath so it can run on the JVM without an emulator. Keep the copies in sync by copying, not editing:

```bash
mkdir -p app/src/test/resources
cp app/src/main/assets/bank.json app/src/test/resources/bank.json
cp app/src/main/assets/explanations.json app/src/test/resources/explanations.json
```

- [ ] **Step 10: Run the test to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.data.BankDataTest"
```

Expected: 8 tests, all passing.

- [ ] **Step 11: Write `BankRepository`**

```kotlin
// data/BankRepository.kt
package com.mvnsh.citizenship.data

import android.content.Context
import com.mvnsh.citizenship.data.model.Explanation
import com.mvnsh.citizenship.data.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BankRepository(private val context: Context) {

    data class Bank(
        val questions: List<Question>,
        val byId: Map<Int, Question>,
        val explanations: Map<Int, Explanation>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Bank? = null

    suspend fun load(): Bank = cached ?: withContext(Dispatchers.IO) {
        val questions: List<Question> = json.decodeFromString(read("bank.json"))
        // Explanations are authored for only a handful of questions; a missing entry is a
        // supported state (the quiz screen shows its "Answer" panel instead of "Why").
        val explanations: Map<Int, Explanation> = runCatching {
            json.decodeFromString<Map<String, Explanation>>(read("explanations.json"))
                .mapKeys { it.key.toInt() }
        }.getOrElse { emptyMap() }

        Bank(questions, questions.associateBy { it.id }, explanations)
            .also { cached = it }
    }

    private fun read(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
```

- [ ] **Step 12: Commit**

State in the message which source Step 1 actually used, and whether question 4 needed patching. Do not copy this message blind — fill in what happened.

```bash
git add app/src/main/assets app/src/main/java/com/mvnsh/citizenship/data \
        app/src/main/java/com/mvnsh/citizenship/domain/Topics.kt app/src/test .gitignore
git commit -m "feat: build the bundled question bank from the upstream repo

Source: data/questions.json from mvnshrikanth/candian_citizenship_exam@main
(<FILL IN: live via gh, or the design project's 2026-08-12 sync>).

The 7-topic taxonomy is joined in from the design project's bank.json by
question id. Six of the repo's nine raw categories map cleanly to a
topic, but three compound ones (Government & Economy, History &
Geography, Regions & Geography - 147 questions) were split per question
by the design and cannot be recomputed from the category alone, so the
join is the only correct source. Ids that miss the map fall back to the
category's dominant topic and are reported, never silently guessed.

Question 4: <FILL IN: patched to 1982, or already correct upstream>.

Tests assert ids are unique and sparse (id != index), and that every
question's topic is one of the seven."
```

---

## Task 3: Pure study domain — dates, engine, stats

**Files:**
- Create: `app/src/main/java/com/mvnsh/citizenship/domain/DateUtils.kt`
- Create: `app/src/main/java/com/mvnsh/citizenship/domain/StudyEngine.kt`
- Create: `app/src/main/java/com/mvnsh/citizenship/domain/Stats.kt`
- Test: `app/src/test/java/com/mvnsh/citizenship/domain/DateUtilsTest.kt`
- Test: `app/src/test/java/com/mvnsh/citizenship/domain/StudyEngineTest.kt`
- Test: `app/src/test/java/com/mvnsh/citizenship/domain/StatsTest.kt`

**Interfaces:**
- Consumes: `Question`, `Topics` (Task 2); `ProgressState`, `SeenStat`, `MockAttempt`, `NotifPrefs`, `SessionState`.
- **Ordering:** the domain reads `ProgressState`, which Task 4 owns. Before Step 1 of this task, create `data/model/ProgressState.kt` exactly as written in **Task 4, Step 2** — all five classes plus the `encode`/`decode` companion. Task 4 then only adds `ProgressRepository` on top. Do not write a temporary stand-in version; a second definition drifting from Task 4's is the failure mode this note exists to prevent.
- Produces:
  - `object DateUtils`: `fun today(clock: Clock = Clock.systemDefaultZone()): String`, `fun shiftDay(n: Int, clock: Clock = ...): String`, `fun fmtDate(iso: String?): String`, `fun daysTo(iso: String, clock: Clock = ...): Int`, `fun mmss(seconds: Int): String`, `fun clip(text: String, max: Int): String`
  - `object StudyEngine`: `enum class Mode { CONTINUE, QUICK, ALL, WEAK, MARKS, UNSEEN, TOPIC, MOCK }`, `fun buildSession(mode: Mode, bank: List<Question>, progress: ProgressState, topicKey: String? = null, random: Random = Random): List<Int>`, `fun label(mode: Mode, topicKey: String?): String`, `fun optionOrder(q: Question): List<Int>`, `fun weakIds(progress: ProgressState): List<Int>`
  - `object Stats`: `data class TopicStat(...)`, `fun topicStats(bank, progress): List<TopicStat>`, `fun accuracy(progress): Int`, `fun achievements(bank, progress): Set<String>`, `fun registerAnswer(progress, questionId, correct, today, yesterday): ProgressState`, `fun registerMock(progress, ids, marks, byId, today, yesterday): Pair<ProgressState, Int>`, `val MILESTONES: List<Milestone>`

**Note on `Clock`:** the design's date helpers read `new Date()` directly. Every function here takes an injectable `java.time.Clock` defaulting to the system clock, so streak transitions are testable without sleeping or mocking statics. This is the single most bug-prone area of the app — the streak logic has three distinct branches — so it must be directly testable.

- [ ] **Step 1: Write the failing `DateUtilsTest`**

```kotlin
package com.mvnsh.citizenship.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    private val toronto = ZoneId.of("America/Toronto")
    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), toronto)

    @Test fun today_is_the_local_calendar_date_not_utc() {
        // 03:30 UTC on the 19th is still 23:30 on the 18th in Toronto.
        assertEquals("2026-08-18", DateUtils.today(clockAt("2026-08-19T03:30:00Z")))
    }

    @Test fun shiftDay_moves_by_calendar_days() {
        val c = clockAt("2026-08-18T16:00:00Z")
        assertEquals("2026-08-17", DateUtils.shiftDay(-1, c))
        assertEquals("2026-08-19", DateUtils.shiftDay(1, c))
        assertEquals("2026-09-25", DateUtils.shiftDay(38, c))
    }

    @Test fun daysTo_never_returns_negative() {
        val c = clockAt("2026-08-18T16:00:00Z")
        assertEquals(38, DateUtils.daysTo("2026-09-25", c))
        assertEquals(0, DateUtils.daysTo("2026-08-18", c))
        assertEquals(0, DateUtils.daysTo("2026-08-01", c))
    }

    @Test fun fmtDate_uses_the_designs_en_CA_long_form() {
        assertEquals("25 September 2026", DateUtils.fmtDate("2026-09-25"))
        assertEquals("", DateUtils.fmtDate(null))
    }

    @Test fun mmss_pads_seconds() {
        assertEquals("30:00", DateUtils.mmss(1800))
        assertEquals("4:59", DateUtils.mmss(299))
        assertEquals("0:07", DateUtils.mmss(7))
        assertEquals("0:00", DateUtils.mmss(0))
    }

    @Test fun clip_adds_an_ellipsis_only_when_it_shortens() {
        assertEquals("abc", DateUtils.clip("abc", 5))
        assertEquals("ab…", DateUtils.clip("abcdef", 3))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.domain.DateUtilsTest"
```

Expected: compilation failure — `DateUtils` does not exist.

- [ ] **Step 3: Write `DateUtils`**

```kotlin
// domain/DateUtils.kt
package com.mvnsh.citizenship.domain

import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    /** The design's en-CA "day month year" long form, e.g. "25 September 2026". */
    private val LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.CANADA)

    fun today(clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).toString()

    fun shiftDay(n: Int, clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).plusDays(n.toLong()).toString()

    fun fmtDate(iso: String?): String =
        iso?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).format(LONG) } ?: ""

    /** Clamped at zero, matching the design's Math.max(0, ...). */
    fun daysTo(iso: String, clock: Clock = Clock.systemDefaultZone()): Int {
        val days = LocalDate.parse(iso).toEpochDay() - LocalDate.now(clock).toEpochDay()
        return days.coerceAtLeast(0L).toInt()
    }

    fun mmss(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun clip(text: String, max: Int): String =
        if (text.length > max) text.take(max - 1).trim() + "…" else text
}
```

- [ ] **Step 4: Run to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.domain.DateUtilsTest"
```

Expected: 6 tests passing.

- [ ] **Step 5: Write the failing `StudyEngineTest`**

```kotlin
package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEngineTest {

    private fun q(id: Int, topic: String = "history", options: List<String> = listOf("a", "b", "c", "d")) =
        Question(id = id, topic = topic, question = "q$id", options = options, answer = 0)

    private val bank = (1..40).map { q(it, if (it <= 10) "rights" else "history") }

    @Test fun quick_session_is_ten_questions_from_the_bank() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.QUICK, bank, ProgressState(), random = Random(7))
        assertEquals(10, ids.size)
        assertEquals("must not repeat a question", 10, ids.toSet().size)
        assertTrue(ids.all { it in bank.map { q -> q.id } })
    }

    @Test fun all_session_is_the_whole_bank_in_order() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.ALL, bank, ProgressState())
        assertEquals(bank.map { it.id }, ids)
    }

    @Test fun continue_session_is_the_first_twenty_in_order() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.CONTINUE, bank, ProgressState())
        assertEquals((1..20).toList(), ids)
    }

    @Test fun weak_session_is_questions_missed_twice_or_more() {
        val p = ProgressState(seen = mapOf(1 to SeenStat(3, 2), 2 to SeenStat(5, 1), 3 to SeenStat(2, 4)))
        assertEquals(listOf(1, 3), StudyEngine.buildSession(StudyEngine.Mode.WEAK, bank, p))
    }

    @Test fun unseen_session_skips_answered_questions_and_caps_at_twenty() {
        val p = ProgressState(seen = (1..5).associateWith { SeenStat(1, 0) })
        val ids = StudyEngine.buildSession(StudyEngine.Mode.UNSEEN, bank, p)
        assertEquals(20, ids.size)
        assertEquals((6..25).toList(), ids)
    }

    @Test fun topic_session_is_capped_at_twenty_of_that_topic() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.TOPIC, bank, ProgressState(), topicKey = "rights")
        assertEquals((1..10).toList(), ids)
    }

    @Test fun bookmark_session_preserves_bookmark_order() {
        val p = ProgressState(bookmarks = listOf(7, 2, 30))
        assertEquals(listOf(7, 2, 30), StudyEngine.buildSession(StudyEngine.Mode.MARKS, bank, p))
    }

    @Test fun mock_session_is_twenty_unique_questions() {
        val ids = StudyEngine.buildSession(StudyEngine.Mode.MOCK, bank, ProgressState(), random = Random(1))
        assertEquals(20, ids.size)
        assertEquals(20, ids.toSet().size)
    }

    @Test fun empty_source_yields_an_empty_session() {
        assertEquals(emptyList<Int>(), StudyEngine.buildSession(StudyEngine.Mode.WEAK, bank, ProgressState()))
    }

    @Test fun option_order_rotates_deterministically_by_id() {
        // The design rotates by `id % optionCount` so the correct answer is not always
        // in the same slot, while staying stable for a given question.
        assertEquals(listOf(1, 2, 3, 0), StudyEngine.optionOrder(q(1)))
        assertEquals(listOf(2, 3, 0, 1), StudyEngine.optionOrder(q(2)))
        assertEquals(listOf(0, 1, 2, 3), StudyEngine.optionOrder(q(4)))
        assertEquals(StudyEngine.optionOrder(q(17)), StudyEngine.optionOrder(q(17)))
    }

    @Test fun option_order_is_left_alone_when_an_option_refers_to_the_others() {
        // "Both A and B" only makes sense if A and B stayed put.
        listOf("Both A and B", "None of the above", "All of these", "none of them").forEach { text ->
            val question = q(3, options = listOf("x", "y", text, "z"))
            assertEquals("must not shuffle '$text'", listOf(0, 1, 2, 3), StudyEngine.optionOrder(question))
        }
    }
}
```

- [ ] **Step 6: Run to confirm it fails, then write `StudyEngine`**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.domain.StudyEngineTest"
```

Expected: compilation failure. Then:

```kotlin
// domain/StudyEngine.kt
package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import kotlin.random.Random

object StudyEngine {

    enum class Mode { CONTINUE, QUICK, ALL, WEAK, MARKS, UNSEEN, TOPIC, MOCK }

    const val SESSION_CAP = 20
    const val QUICK_SIZE = 10
    const val MOCK_SIZE = 20
    const val MOCK_SECONDS = 1800
    const val MOCK_PASS_CORRECT = 15

    /**
     * Options whose text refers to the other options ("Both A and B", "None of the above")
     * break if the order changes, so those questions are never rotated.
     */
    private val POSITIONAL = Regex("both|none of|all of|above", RegexOption.IGNORE_CASE)

    fun optionOrder(q: Question): List<Int> {
        val n = q.options.size
        if (n == 0) return emptyList()
        if (q.options.any { POSITIONAL.containsMatchIn(it) }) return q.options.indices.toList()
        val r = q.id % n
        return q.options.indices.map { (it + r) % n }
    }

    fun weakIds(progress: ProgressState): List<Int> =
        progress.seen.filterValues { it.m >= 2 }.keys.sorted()

    fun buildSession(
        mode: Mode,
        bank: List<Question>,
        progress: ProgressState,
        topicKey: String? = null,
        random: Random = Random,
    ): List<Int> = when (mode) {
        Mode.CONTINUE -> bank.map { it.id }.take(SESSION_CAP)
        Mode.QUICK -> sample(bank.map { it.id }, QUICK_SIZE, random)
        Mode.ALL -> bank.map { it.id }
        Mode.WEAK -> weakIds(progress).filter { it in bank.idSet() }
        Mode.MARKS -> progress.bookmarks.filter { it in bank.idSet() }
        Mode.UNSEEN -> bank.filter { it.id !in progress.seen }.map { it.id }.take(SESSION_CAP)
        Mode.TOPIC -> bank.filter { it.topic == topicKey }.map { it.id }.take(SESSION_CAP)
        Mode.MOCK -> sample(bank.map { it.id }, MOCK_SIZE, random)
    }

    /** Verbatim from the design's session labels. */
    fun label(mode: Mode, topicKey: String? = null): String = when (mode) {
        Mode.CONTINUE -> "Continue"
        Mode.QUICK -> "Quick practice"
        Mode.ALL -> "All questions"
        Mode.WEAK -> "Weak questions"
        Mode.MARKS -> "Bookmarked"
        Mode.UNSEEN -> "New questions"
        Mode.TOPIC -> Topics.name(topicKey ?: "")
        Mode.MOCK -> "Mock test"
    }

    private fun List<Question>.idSet(): Set<Int> = mapTo(HashSet()) { it.id }

    private fun sample(ids: List<Int>, n: Int, random: Random): List<Int> =
        ids.shuffled(random).take(n)
}
```

- [ ] **Step 7: Write the failing `StatsTest`**

```kotlin
package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun q(id: Int, topic: String) =
        Question(id = id, topic = topic, question = "q$id", options = listOf("a", "b", "c", "d"), answer = 1)

    private val bank = listOf(
        q(1, "rights"), q(2, "rights"), q(3, "rights"), q(4, "rights"),
        q(5, "history"), q(6, "history"),
    )

    // ---- accuracy -------------------------------------------------------

    @Test fun accuracy_is_zero_before_any_answer() {
        assertEquals(0, Stats.accuracy(ProgressState()))
    }

    @Test fun accuracy_rounds_to_a_whole_percent() {
        assertEquals(83, Stats.accuracy(ProgressState(answered = 6, correct = 5)))
    }

    // ---- topic stats ----------------------------------------------------

    @Test fun topic_accuracy_uses_attempts_not_distinct_questions() {
        // q1 attempted 4x, missed 1 -> 3/4. q2 attempted 2x, missed 1 -> 1/2.
        // Combined: (6 attempts - 2 misses) / 6 attempts = 67%.
        val p = ProgressState(seen = mapOf(1 to SeenStat(4, 1), 2 to SeenStat(2, 1)))
        val rights = Stats.topicStats(bank, p).first { it.key == "rights" }
        assertEquals(67, rights.accuracy)
        assertEquals("67%", rights.accuracyText)
        assertEquals(4, rights.total)
        assertEquals(2, rights.done)
        assertEquals(50, rights.coveragePct)
    }

    @Test fun an_untouched_topic_shows_an_em_dash_not_zero_percent() {
        val history = Stats.topicStats(bank, ProgressState()).first { it.key == "history" }
        assertEquals("—", history.accuracyText)
        assertEquals(0, history.done)
        assertFalse("an untouched topic is not 'weakish'", history.weakish)
    }

    @Test fun topic_stats_are_returned_in_the_designs_topic_order() {
        assertEquals(Topics.all.map { it.key }, Stats.topicStats(bank, ProgressState()).map { it.key })
    }

    // ---- streak ---------------------------------------------------------

    @Test fun first_ever_answer_starts_the_streak_at_one() {
        val out = Stats.registerAnswer(ProgressState(), 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(1, out.streak)
        assertEquals("2026-08-18", out.lastDay)
        assertEquals(1, out.best)
    }

    @Test fun answering_on_a_consecutive_day_extends_the_streak() {
        val p = ProgressState(streak = 4, best = 4, lastDay = "2026-08-17")
        val out = Stats.registerAnswer(p, 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(5, out.streak)
        assertEquals(5, out.best)
    }

    @Test fun answering_after_a_gap_resets_the_streak_but_keeps_the_best() {
        val p = ProgressState(streak = 9, best = 14, lastDay = "2026-08-10")
        val out = Stats.registerAnswer(p, 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(1, out.streak)
        assertEquals("best is a high-water mark", 14, out.best)
    }

    @Test fun answering_twice_in_one_day_does_not_double_the_streak() {
        val once = Stats.registerAnswer(ProgressState(streak = 3, best = 3, lastDay = "2026-08-17"), 1,
            correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        val twice = Stats.registerAnswer(once, 2, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(4, twice.streak)
    }

    // ---- goal, counters, weak list -------------------------------------

    @Test fun a_stale_goal_date_resets_the_daily_count() {
        val p = ProgressState(goalDone = 17, goalDate = "2026-08-17")
        val out = Stats.registerAnswer(p, 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(1, out.goalDone)
        assertEquals("2026-08-18", out.goalDate)
    }

    @Test fun a_wrong_answer_increments_seen_and_missed_but_not_correct() {
        val out = Stats.registerAnswer(ProgressState(), 1, correct = false, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(1, out.answered)
        assertEquals(0, out.correct)
        assertEquals(1, out.seen[1]!!.s)
        assertEquals(1, out.seen[1]!!.m)
    }

    @Test fun missing_a_question_twice_puts_it_on_the_weak_list() {
        var p = ProgressState()
        p = Stats.registerAnswer(p, 1, correct = false, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(emptyList<Int>(), StudyEngine.weakIds(p))
        p = Stats.registerAnswer(p, 1, correct = false, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals(listOf(1), StudyEngine.weakIds(p))
    }

    // ---- mock submission ------------------------------------------------

    @Test fun submitting_a_mock_records_the_percentage_and_folds_in_every_answer() {
        val ids = listOf(1, 2, 3, 4)
        val marks = mapOf(1 to 1, 2 to 1, 3 to 0, 4 to 2)   // answer is 1, so 1 and 2 are right
        val (out, right) = Stats.registerMock(
            ProgressState(), ids, marks, bank.associateBy { it.id },
            today = "2026-08-18", yesterday = "2026-08-17",
        )
        assertEquals(2, right)
        assertEquals(listOf(MockAttempt(50, "2026-08-18")), out.mocks)
        assertEquals(4, out.answered)
        assertEquals(2, out.correct)
        assertEquals(0, out.seen[1]!!.m)
        assertEquals(1, out.seen[3]!!.m)
    }

    @Test fun an_unanswered_mock_question_counts_as_missed() {
        val (out, right) = Stats.registerMock(
            ProgressState(), listOf(1, 2), mapOf(1 to 1), bank.associateBy { it.id },
            today = "2026-08-18", yesterday = "2026-08-17",
        )
        assertEquals(1, right)
        assertEquals(1, out.seen[2]!!.m)
    }

    // ---- milestones -----------------------------------------------------

    @Test fun milestones_unlock_at_the_designs_thresholds() {
        assertEquals(emptySet<String>(), Stats.achievements(bank, ProgressState()))

        val p = ProgressState(
            answered = 200, correct = 150, streak = 7, best = 7,
            mocks = listOf(MockAttempt(70, "2026-08-10"), MockAttempt(80, "2026-08-14")),
            seen = bank.associate { it.id to SeenStat(1, 0) },
        )
        val got = Stats.achievements(bank, p)
        assertTrue(got.containsAll(setOf("first", "s3", "q50", "s7", "q200", "mock1", "pass")))
        assertFalse("only two of seven topics are present in this test bank", "all7" in got)
    }

    @Test fun a_mock_below_seventy_five_percent_does_not_unlock_pass() {
        val p = ProgressState(answered = 20, mocks = listOf(MockAttempt(70, "2026-08-10")))
        assertTrue("mock1" in Stats.achievements(bank, p))
        assertFalse("pass" in Stats.achievements(bank, p))
    }

    @Test fun there_are_exactly_eight_milestones_in_the_designs_order() {
        assertEquals(
            listOf("first", "s3", "q50", "s7", "q200", "mock1", "pass", "all7"),
            Stats.MILESTONES.map { it.key },
        )
        assertEquals(
            listOf("First session", "3-day streak", "50 questions", "7-day streak",
                   "200 questions", "First mock", "Mock passed", "All 7 topics"),
            Stats.MILESTONES.map { it.label },
        )
    }
}
```

- [ ] **Step 8: Run to confirm it fails, then write `Stats`**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.domain.StatsTest"
```

Expected: compilation failure. Then:

```kotlin
// domain/Stats.kt
package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.data.model.SeenStat
import kotlin.math.max
import kotlin.math.roundToInt

object Stats {

    data class Milestone(val key: String, val label: String)

    /** Verbatim from the design's MILES constant, in its order. */
    val MILESTONES = listOf(
        Milestone("first", "First session"),
        Milestone("s3", "3-day streak"),
        Milestone("q50", "50 questions"),
        Milestone("s7", "7-day streak"),
        Milestone("q200", "200 questions"),
        Milestone("mock1", "First mock"),
        Milestone("pass", "Mock passed"),
        Milestone("all7", "All 7 topics"),
    )

    data class TopicStat(
        val key: String,
        val name: String,
        val blurb: String,
        val total: Int,
        val done: Int,
        /** Share of the topic's questions seen at least once, 0..100. */
        val coveragePct: Int,
        /** Correct share of all attempts on this topic, 0..100. */
        val accuracy: Int,
        /** "—" when the topic has never been attempted, so an untouched topic is not shown as 0%. */
        val accuracyText: String,
        val weakish: Boolean,
    )

    fun accuracy(p: ProgressState): Int =
        if (p.answered == 0) 0 else (p.correct * 100.0 / p.answered).roundToInt()

    fun topicStats(bank: List<Question>, p: ProgressState): List<TopicStat> =
        Topics.all.map { topic ->
            val qs = bank.filter { it.topic == topic.key }
            val seenQs = qs.filter { p.seen.containsKey(it.id) }
            var attempts = 0
            var misses = 0
            seenQs.forEach { q ->
                val rec = p.seen[q.id]!!
                attempts += rec.s
                misses += rec.m
            }
            val acc = if (attempts == 0) 0 else ((attempts - misses) * 100.0 / attempts).roundToInt()
            TopicStat(
                key = topic.key,
                name = topic.name,
                blurb = topic.blurb,
                total = qs.size,
                done = seenQs.size,
                coveragePct = if (qs.isEmpty()) 0 else (seenQs.size * 100.0 / qs.size).roundToInt(),
                accuracy = acc,
                accuracyText = if (attempts == 0) "—" else "$acc%",
                weakish = attempts > 0 && acc < 70,
            )
        }

    fun touchedTopics(bank: List<Question>, p: ProgressState): Int {
        val byId = bank.associateBy { it.id }
        return p.seen.keys.mapNotNullTo(HashSet()) { byId[it]?.topic }.size
    }

    fun achievements(bank: List<Question>, p: ProgressState): Set<String> = buildSet {
        if (p.answered > 0) add("first")
        if (p.streak >= 3) add("s3")
        if (p.answered >= 50) add("q50")
        if (p.streak >= 7) add("s7")
        if (p.answered >= 200) add("q200")
        if (p.mocks.isNotEmpty()) add("mock1")
        if (p.mocks.any { it.pct >= 75 }) add("pass")
        if (touchedTopics(bank, p) >= 7) add("all7")
    }

    fun registerAnswer(
        p: ProgressState,
        questionId: Int,
        correct: Boolean,
        today: String,
        yesterday: String,
    ): ProgressState {
        val rec = p.seen[questionId] ?: SeenStat()
        val seen = p.seen + (questionId to SeenStat(s = rec.s + 1, m = rec.m + if (correct) 0 else 1))

        val goalReset = p.goalDate != today
        val week = p.week.toMutableList().also { w ->
            if (w.size == 7) w[6] = w[6] + 1
        }

        return advanceDay(p, today, yesterday).copy(
            seen = seen,
            answered = p.answered + 1,
            correct = p.correct + if (correct) 1 else 0,
            goalDate = today,
            goalDone = if (goalReset) 1 else p.goalDone + 1,
            week = week,
        )
    }

    fun registerMock(
        p: ProgressState,
        ids: List<Int>,
        marks: Map<Int, Int>,
        byId: Map<Int, Question>,
        today: String,
        yesterday: String,
    ): Pair<ProgressState, Int> {
        val right = ids.count { id -> marks[id] != null && marks[id] == byId[id]?.answer }
        val pct = if (ids.isEmpty()) 0 else (right * 100.0 / ids.size).roundToInt()

        val seen = p.seen.toMutableMap()
        ids.forEach { id ->
            val rec = seen[id] ?: SeenStat()
            // An unanswered question counts as missed — the real test marks it wrong too.
            val wrong = marks[id] == null || marks[id] != byId[id]?.answer
            seen[id] = SeenStat(s = rec.s + 1, m = rec.m + if (wrong) 1 else 0)
        }

        val out = advanceDay(p, today, yesterday).copy(
            seen = seen,
            mocks = p.mocks + MockAttempt(pct = pct, date = today),
            answered = p.answered + ids.size,
            correct = p.correct + right,
        )
        return out to right
    }

    /**
     * The streak's three branches, in one place: a new consecutive day extends it,
     * a gap restarts it at one, and a second visit on the same day changes nothing.
     */
    private fun advanceDay(p: ProgressState, today: String, yesterday: String): ProgressState {
        if (p.lastDay == today) return p
        val streak = if (p.lastDay == yesterday) p.streak + 1 else 1
        return p.copy(streak = streak, lastDay = today, best = max(p.best, streak))
    }
}
```

- [ ] **Step 9: Run the whole unit-test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BankDataTest` (7) + `DateUtilsTest` (6) + `StudyEngineTest` (11) + `StatsTest` (18) all passing.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/mvnsh/citizenship/domain app/src/test
git commit -m "feat: port the study engine as pure, clock-injectable Kotlin

Session building, deterministic option rotation (skipped for options
that refer to the others, e.g. 'Both A and B'), weak-list threshold,
topic accuracy over attempts rather than distinct questions, the eight
milestones, and the streak's three branches.

Every date helper takes an injectable java.time.Clock so streak
transitions are tested directly instead of by sleeping."
```

---

## Task 4: Progress persistence

**Files:**
- Create: `app/src/main/java/com/mvnsh/citizenship/data/model/ProgressState.kt` — Task 3 was told to create this from Step 2 below. If it already exists, diff it against Step 2 and reconcile rather than rewriting; the serialization test then passes on the first run, which is the expected outcome, not a reason to weaken the test.
- Create: `app/src/main/java/com/mvnsh/citizenship/data/ProgressRepository.kt`
- Modify: `app/src/main/java/com/mvnsh/citizenship/CitizenshipApp.kt` (own the DataStore instance)
- Test: `app/src/test/java/com/mvnsh/citizenship/data/ProgressStateSerializationTest.kt`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/data/ProgressRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing beyond Task 1.
- Produces:
  - `@Serializable data class ProgressState(...)` with the fields listed in Step 2, `@Serializable data class SeenStat(val s: Int = 0, val m: Int = 0)`, `@Serializable data class MockAttempt(val pct: Int, val date: String)`, `@Serializable data class NotifPrefs(val daily: Boolean = true, val streak: Boolean = false, val test: Boolean = false)`, `@Serializable data class SessionState(...)`
  - `class ProgressRepository(dataStore: DataStore<Preferences>, scope: CoroutineScope)` with `val state: StateFlow<ProgressState>`, `fun mutate(transform: (ProgressState) -> ProgressState)`, `suspend fun flush()`, `suspend fun replace(next: ProgressState)`

- [ ] **Step 1: Write the failing serialization test**

```kotlin
package com.mvnsh.citizenship.data

import com.mvnsh.citizenship.data.model.MockAttempt
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SeenStat
import com.mvnsh.citizenship.data.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressStateSerializationTest {

    @Test fun round_trips_without_loss() {
        val original = ProgressState(
            onboarded = true, answered = 340, correct = 279,
            seen = mapOf(1 to SeenStat(2, 1), 502 to SeenStat(1, 0)),
            mocks = listOf(MockAttempt(70, "2026-08-09"), MockAttempt(85, "2026-08-17")),
            bookmarks = listOf(2, 7, 10, 44, 120),
            goalTarget = 30, goalDone = 14, goalDate = "2026-08-18",
            streak = 12, best = 14, lastDay = "2026-08-17", testDate = "2026-09-25",
            theme = "Dark", week = listOf(12, 16, 20, 18, 22, 14, 14),
            session = SessionState(
                mode = "MOCK", label = "Mock test", ids = listOf(3, 9, 12),
                index = 1, marks = mapOf(3 to 2), startedAtEpochMs = 1_760_000_000_000,
                timed = true, deadlineEpochMs = 1_760_000_180_000,
            ),
        )
        assertEquals(original, ProgressState.decode(ProgressState.encode(original)))
    }

    @Test fun a_missing_blob_decodes_to_the_fresh_default() {
        val fresh = ProgressState.decode(null)
        assertEquals(ProgressState(), fresh)
        assertEquals(false, fresh.onboarded)
        assertEquals(20, fresh.goalTarget)
        assertEquals(0, fresh.streak)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), fresh.week)
        assertNull(fresh.session)
        assertEquals(true, fresh.notif.daily)
        assertEquals(false, fresh.notif.streak)
        assertEquals("System", fresh.theme)
    }

    @Test fun corrupt_json_decodes_to_the_fresh_default_instead_of_crashing() {
        assertEquals(ProgressState(), ProgressState.decode("{not json"))
        assertEquals(ProgressState(), ProgressState.decode(""))
    }

    @Test fun an_older_blob_missing_newer_fields_still_loads() {
        val old = """{"onboarded":true,"answered":5,"correct":4,"goalTarget":10}"""
        val out = ProgressState.decode(old)
        assertEquals(true, out.onboarded)
        assertEquals(5, out.answered)
        assertEquals(10, out.goalTarget)
        assertEquals("defaults fill the gap", "System", out.theme)
    }

    @Test fun an_unknown_field_from_a_newer_build_is_ignored() {
        val future = """{"answered":3,"somethingNew":{"a":1}}"""
        assertEquals(3, ProgressState.decode(future).answered)
    }

    @Test fun the_encoded_blob_omits_defaults_to_stay_small() {
        // Defaults are not written, so a fresh install's blob is tiny and a full bank of
        // seen records is the only thing that grows the file.
        assertEquals("{}", ProgressState.encode(ProgressState()))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails, then write the models**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.data.ProgressStateSerializationTest"
```

Expected: compilation failure. Then:

```kotlin
// data/model/ProgressState.kt
package com.mvnsh.citizenship.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SeenStat(val s: Int = 0, val m: Int = 0)

@Serializable
data class MockAttempt(val pct: Int, val date: String)

@Serializable
data class NotifPrefs(
    val daily: Boolean = true,
    val streak: Boolean = false,
    val test: Boolean = false,
)

/**
 * The in-flight practice or mock session. Persisted so the design's promise on the exit
 * dialog ("Your place is kept") survives process death, and so the mock's absolute
 * [deadlineEpochMs] keeps the timer running while the app is backgrounded.
 */
@Serializable
data class SessionState(
    val mode: String,
    val topicKey: String? = null,
    val label: String,
    val ids: List<Int>,
    val index: Int = 0,
    /** Practice only: the option index that revealed the current answer. */
    val picked: Int? = null,
    val marks: Map<Int, Int> = emptyMap(),
    val right: Int = 0,
    val startedAtEpochMs: Long,
    val timed: Boolean = false,
    val deadlineEpochMs: Long? = null,
    val submitted: Boolean = false,
    val finalRight: Int? = null,
    val finalPct: Int? = null,
)

@Serializable
data class ProgressState(
    val onboarded: Boolean = false,
    val answered: Int = 0,
    val correct: Int = 0,
    val seen: Map<Int, SeenStat> = emptyMap(),
    val mocks: List<MockAttempt> = emptyList(),
    val bookmarks: List<Int> = emptyList(),
    val goalTarget: Int = 20,
    val goalDone: Int = 0,
    val goalDate: String = "",
    val streak: Int = 0,
    val best: Int = 0,
    val lastDay: String? = null,
    val testDate: String? = null,
    val notif: NotifPrefs = NotifPrefs(),
    val theme: String = "System",
    val week: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
    val session: SessionState? = null,
    val schemaVersion: Int = 1,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun encode(state: ProgressState): String = json.encodeToString(state)

        /** Never throws: a missing, empty or corrupt blob is a fresh install. */
        fun decode(raw: String?): ProgressState =
            if (raw.isNullOrBlank()) ProgressState()
            else runCatching { json.decodeFromString<ProgressState>(raw) }.getOrElse { ProgressState() }
    }
}
```

- [ ] **Step 3: Run to confirm it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.data.ProgressStateSerializationTest"
```

Expected: 6 tests passing.

- [ ] **Step 4: Write `ProgressRepository`**

The whole blob is rewritten on every change. At one answer per tap that is a file write per tap, so writes are conflated through a `MutableStateFlow` — reads are always instant from memory, and the file catches up.

```kotlin
// data/ProgressRepository.kt
package com.mvnsh.citizenship.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mvnsh.citizenship.data.model.ProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ProgressRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val key = stringPreferencesKey("progress_json")

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            _state.value = ProgressState.decode(dataStore.data.map { it[key] }.first())
            _loaded.value = true
            // drop(1) so restoring the persisted value does not immediately rewrite it.
            _state.drop(1).debounce(250L).collect { persist(it) }
        }
    }

    /** Updates memory immediately; the file write is conflated and lands within ~250ms. */
    fun mutate(transform: (ProgressState) -> ProgressState) {
        _state.value = transform(_state.value)
    }

    /** Writes the current value now. Call from onStop and at session boundaries. */
    suspend fun flush() = persist(_state.value)

    /** Replaces everything at once (reset, demo seed) and writes immediately. */
    suspend fun replace(next: ProgressState) {
        _state.value = next
        persist(next)
    }

    private suspend fun persist(state: ProgressState) {
        dataStore.edit { it[key] = ProgressState.encode(state) }
    }
}
```

In `CitizenshipApp`, expose the DataStore and repository:

```kotlin
// CitizenshipApp.kt
package com.mvnsh.citizenship

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

private val Context.progressStore by preferencesDataStore(name = "progress")

class CitizenshipApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bankRepository by lazy { BankRepository(this) }
    val progressRepository by lazy { ProgressRepository(progressStore, appScope) }
}
```

- [ ] **Step 5: Write the instrumented repository test**

DataStore needs a real filesystem, so this one runs on device.

```kotlin
package com.mvnsh.citizenship.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mvnsh.citizenship.data.model.SeenStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {

    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private val scope = CoroutineScope(Dispatchers.IO)

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(ctx.cacheDir, "test-progress-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { file }
    }

    @After fun tearDown() { file.delete() }

    @Test fun a_mutation_survives_a_new_repository_instance() = runTest {
        val first = ProgressRepository(store, scope)
        waitUntil { first.loaded.value }
        first.mutate { it.copy(answered = 7, seen = mapOf(4 to SeenStat(1, 1))) }
        first.flush()

        val second = ProgressRepository(store, scope)
        waitUntil { second.loaded.value }
        assertEquals(7, second.state.value.answered)
        assertEquals(SeenStat(1, 1), second.state.value.seen[4])
    }

    @Test fun a_burst_of_mutations_settles_on_the_last_value() = runTest {
        val repo = ProgressRepository(store, scope)
        waitUntil { repo.loaded.value }
        repeat(50) { n -> repo.mutate { it.copy(answered = n + 1) } }
        assertEquals("memory is immediate", 50, repo.state.value.answered)
        repo.flush()

        val reloaded = ProgressRepository(store, scope)
        waitUntil { reloaded.loaded.value }
        assertEquals(50, reloaded.state.value.answered)
    }

    private suspend fun waitUntil(timeoutMs: Long = 3_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(20)
        }
        assert(predicate()) { "condition not met within ${timeoutMs}ms" }
    }
}
```

- [ ] **Step 6: Run both test suites**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.data.ProgressRepositoryTest"
```

Expected: all unit tests pass; both instrumented tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mvnsh/citizenship/data app/src/main/java/com/mvnsh/citizenship/CitizenshipApp.kt app/src/test app/src/androidTest
git commit -m "feat: persist progress as one debounced JSON blob in DataStore

Reads are instant from memory; writes are conflated at 250ms so a burst
of taps is one file write. Corrupt, empty and older blobs all decode to
sane defaults rather than crashing, and defaults are not encoded so a
fresh install's blob is '{}'.

The active session and the mock's absolute deadline are part of the
blob, which is what makes the design's 'your place is kept' and 'the
timer keeps running if you leave the app' literally true."
```

---

## Task 5: Navigation shell and `AppViewModel`

**Files:**
- Create: `app/src/main/java/com/mvnsh/citizenship/ui/AppViewModel.kt`
- Create: `app/src/main/res/navigation/nav_graph.xml`, `app/src/main/res/menu/bottom_nav.xml`
- Modify: `app/src/main/java/com/mvnsh/citizenship/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml`
- Create: placeholder fragments + layouts for `home`, `practice`, `topics`, `progress` (each a single `TextView` naming itself; replaced in Tasks 7, 8 and 13)
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/NavigationTest.kt`

**Interfaces:**
- Consumes: `BankRepository.Bank`, `ProgressRepository`, `StudyEngine`, `Stats`, `DateUtils`.
- Produces:
  - `class AppViewModel(app: Application) : AndroidViewModel(app)` with:
    - `val bank: StateFlow<BankRepository.Bank?>` (null until loaded), `val loadFailed: StateFlow<Boolean>`, `val progress: StateFlow<ProgressState>`, `val session: StateFlow<SessionState?>`, `val mockSecondsLeft: StateFlow<Int>`, `val snacks: SharedFlow<String>`
    - `fun retryLoad()`, `fun startSession(mode: StudyEngine.Mode, topicKey: String? = null)`, `fun startMock()`, `fun resumeOrStart()`, `fun pick(optionIndex: Int)`, `fun next()`, `fun goTo(index: Int)`, `fun submitMock(auto: Boolean = false)`, `fun toggleBookmark(id: Int)`, `fun clearWeak(id: Int)`, `fun keepSessionAndExit()`, `fun discardSession()`, `fun finishOnboarding()`, `fun setGoalTarget(n: Int)`, `fun setTestDate(iso: String?)`, `fun setTheme(name: String)`, `fun setNotif(daily: Boolean? = null, streak: Boolean? = null, test: Boolean? = null)`, `fun resetProgress()`, `fun clearBookmarks()`, `fun seedDemo()`, `fun freshInstall()`, `fun toast(msg: String)`
  - Nav destination ids: `homeFragment`, `practiceFragment`, `topicsFragment`, `topicDetailFragment` (arg `topicKey: String`), `progressFragment`, `onboardingFragment`, `quizFragment`, `resultsFragment`, `mockIntroFragment`, `mockFragment`, `mockResultsFragment`, `reviewFragment`, `weakFragment`, `bookmarksFragment`, `searchFragment`, `settingsFragment`
  - `fun Fragment.appViewModel(): AppViewModel` extension using `activityViewModels()`

- [ ] **Step 1: Write `AppViewModel`**

The design's `route`/`tab` state machine becomes the nav graph; the ViewModel keeps only what the design kept in `state`: the loaded bank, the persisted progress, and the live session.

```kotlin
// ui/AppViewModel.kt
package com.mvnsh.citizenship.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvnsh.citizenship.CitizenshipApp
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.NotifPrefs
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.domain.StudyEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as CitizenshipApp
    private val progressRepo = appCtx.progressRepository

    private val _bank = MutableStateFlow<BankRepository.Bank?>(null)
    val bank: StateFlow<BankRepository.Bank?> = _bank.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    val progress: StateFlow<ProgressState> = progressRepo.state

    private val _mockSecondsLeft = MutableStateFlow(0)
    val mockSecondsLeft: StateFlow<Int> = _mockSecondsLeft.asStateFlow()

    private val _snacks = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snacks: SharedFlow<String> = _snacks

    /** Derived view of the persisted session, so screens can observe it directly. */
    val session: StateFlow<SessionState?> = progress
        .map { it.session }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        retryLoad()
        viewModelScope.launch { tickMockTimer() }
    }

    fun retryLoad() {
        _loadFailed.value = false
        viewModelScope.launch {
            runCatching { appCtx.bankRepository.load() }
                .onSuccess { _bank.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    fun toast(msg: String) { _snacks.tryEmit(msg) }

    // ---- sessions -------------------------------------------------------

    fun startSession(mode: StudyEngine.Mode, topicKey: String? = null) {
        val b = _bank.value ?: return
        val ids = StudyEngine.buildSession(mode, b.questions, progress.value, topicKey)
        if (ids.isEmpty()) {
            toast("Nothing to practise here yet")
            return
        }
        progressRepo.mutate {
            it.copy(session = SessionState(
                mode = mode.name,
                topicKey = topicKey,
                label = StudyEngine.label(mode, topicKey),
                ids = ids,
                startedAtEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    fun startMock() {
        val b = _bank.value ?: return
        val ids = StudyEngine.buildSession(StudyEngine.Mode.MOCK, b.questions, progress.value)
        val now = System.currentTimeMillis()
        progressRepo.mutate {
            it.copy(session = SessionState(
                mode = StudyEngine.Mode.MOCK.name,
                label = "Mock test",
                ids = ids,
                startedAtEpochMs = now,
                timed = true,
                deadlineEpochMs = now + StudyEngine.MOCK_SECONDS * 1000L,
            ))
        }
    }

    /** Home's single big button: resume an unfinished session, else start one. */
    fun resumeOrStart() {
        val s = progress.value.session
        if (s == null || s.submitted) startSession(StudyEngine.Mode.CONTINUE)
    }

    fun pick(optionIndex: Int) {
        val b = _bank.value ?: return
        val s = progress.value.session ?: return
        val q = b.byId[s.ids.getOrNull(s.index) ?: return] ?: return

        if (s.mode == StudyEngine.Mode.MOCK.name) {
            // No feedback during a mock; the answer stays changeable until submit.
            progressRepo.mutate {
                it.copy(session = s.copy(marks = s.marks + (q.id to optionIndex)))
            }
            return
        }
        if (s.picked != null) return   // already revealed

        val correct = optionIndex == q.answer
        val before = Stats.achievements(b.questions, progress.value).size
        progressRepo.mutate { p ->
            Stats.registerAnswer(p, q.id, correct, DateUtils.today(), DateUtils.shiftDay(-1)).copy(
                session = s.copy(
                    picked = optionIndex,
                    right = s.right + if (correct) 1 else 0,
                    marks = s.marks + (q.id to optionIndex),
                ),
            )
        }
        announceProgress(before)
    }

    private fun announceProgress(milestonesBefore: Int) {
        val b = _bank.value ?: return
        val p = progress.value
        val unlocked = Stats.achievements(b.questions, p)
        if (unlocked.size > milestonesBefore) {
            val label = Stats.MILESTONES.last { it.key in unlocked }.label
            toast("Milestone reached — $label")
        } else if (p.goalDone == p.goalTarget) {
            toast("Daily goal met — streak is safe")
        }
    }

    fun next() {
        val s = progress.value.session ?: return
        if (s.index + 1 >= s.ids.size) {
            progressRepo.mutate { it.copy(session = s.copy(submitted = true, finalRight = s.right)) }
            return
        }
        progressRepo.mutate { it.copy(session = s.copy(index = s.index + 1, picked = null)) }
    }

    fun goTo(index: Int) {
        val s = progress.value.session ?: return
        progressRepo.mutate { it.copy(session = s.copy(index = index.coerceIn(s.ids.indices), picked = null)) }
    }

    fun submitMock(auto: Boolean = false) {
        val b = _bank.value ?: return
        val s = progress.value.session ?: return
        if (s.submitted) return
        progressRepo.mutate { p ->
            val (next, right) = Stats.registerMock(
                p, s.ids, s.marks, b.byId, DateUtils.today(), DateUtils.shiftDay(-1),
            )
            val pct = if (s.ids.isEmpty()) 0 else Math.round(right * 100f / s.ids.size)
            next.copy(session = s.copy(submitted = true, finalRight = right, finalPct = pct))
        }
        viewModelScope.launch { progressRepo.flush() }
        if (auto) toast("Time up — test submitted")
    }

    /** Keeps the session so home can resume it. */
    fun keepSessionAndExit() { viewModelScope.launch { progressRepo.flush() } }

    fun discardSession() { progressRepo.mutate { it.copy(session = null) } }

    private suspend fun tickMockTimer() {
        while (true) {
            val s = progress.value.session
            if (s != null && s.timed && !s.submitted && s.deadlineEpochMs != null) {
                val left = ((s.deadlineEpochMs - System.currentTimeMillis()) / 1000L).toInt()
                _mockSecondsLeft.value = left.coerceAtLeast(0)
                if (left <= 0) submitMock(auto = true)
            } else {
                _mockSecondsLeft.value = 0
            }
            delay(500L)
        }
    }

    // ---- bookmarks and weak list ---------------------------------------

    fun toggleBookmark(id: Int) {
        var added = false
        progressRepo.mutate { p ->
            if (id in p.bookmarks) p.copy(bookmarks = p.bookmarks - id)
            else { added = true; p.copy(bookmarks = p.bookmarks + id) }
        }
        toast(if (added) "Saved to bookmarks" else "Removed from bookmarks")
    }

    fun clearWeak(id: Int) {
        progressRepo.mutate { p ->
            val rec = p.seen[id] ?: return@mutate p
            p.copy(seen = p.seen + (id to rec.copy(m = 0)))
        }
        toast("Cleared from weak list")
    }

    // ---- settings and onboarding ---------------------------------------

    fun finishOnboarding() = progressRepo.mutate { it.copy(onboarded = true) }
    fun setGoalTarget(n: Int) = progressRepo.mutate { it.copy(goalTarget = n) }
    fun setTestDate(iso: String?) = progressRepo.mutate { it.copy(testDate = iso) }
    fun setTheme(name: String) = progressRepo.mutate { it.copy(theme = name) }

    fun setNotif(daily: Boolean? = null, streak: Boolean? = null, test: Boolean? = null) =
        progressRepo.mutate { p ->
            p.copy(notif = NotifPrefs(
                daily = daily ?: p.notif.daily,
                streak = streak ?: p.notif.streak,
                test = test ?: p.notif.test,
            ))
        }

    fun resetProgress() {
        val keptBookmarks = progress.value.bookmarks
        viewModelScope.launch {
            // The design's reset dialog: "Bookmarks are kept."
            progressRepo.replace(ProgressState(onboarded = true, bookmarks = keptBookmarks))
            toast("Progress reset")
        }
    }

    fun clearBookmarks() {
        progressRepo.mutate { it.copy(bookmarks = emptyList()) }
        toast("Bookmarks cleared")
    }
}
```

`seedDemo()` and `freshInstall()` are added in Task 14, where the debug-only Settings rows that call them are built.

Add the accessor extension in the same file:

```kotlin
fun androidx.fragment.app.Fragment.appViewModel(): AppViewModel {
    val vm: AppViewModel by androidx.fragment.app.activityViewModels()
    return vm
}
```

- [ ] **Step 2: Write the nav graph and bottom nav menu**

`res/navigation/nav_graph.xml` — `app:startDestination="@id/homeFragment"`. Include every destination id from the Interfaces block. Give `topicDetailFragment` an `<argument android:name="topicKey" app:argType="string" />`. Actions are not needed: navigation is by destination id (`findNavController().navigate(R.id.weakFragment)`), which keeps fragments from needing to know each other.

`res/menu/bottom_nav.xml` — four items whose ids **exactly match** the four top-level destination ids so `setupWithNavController` wires them automatically:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/homeFragment" android:icon="@drawable/ic_home" android:title="@string/nav_home" />
    <item android:id="@+id/practiceFragment" android:icon="@drawable/ic_list" android:title="@string/nav_practice" />
    <item android:id="@+id/topicsFragment" android:icon="@drawable/ic_book" android:title="@string/nav_topics" />
    <item android:id="@+id/progressFragment" android:icon="@drawable/ic_bar_chart" android:title="@string/nav_progress" />
</menu>
```

Strings: `Home`, `Practice`, `Topics`, `Progress`.

- [ ] **Step 3: Wire `MainActivity`**

Responsibilities: edge-to-edge; hook the bottom nav to the graph; show the nav bar on exactly the four top-level destinations (the design's `showNav`); route the onboarding gate; apply the persisted theme; host the snackbar; flush on stop.

```kotlin
// MainActivity.kt (essential body)
private val vm: AppViewModel by viewModels()

override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val navController = (supportFragmentManager
        .findFragmentById(R.id.nav_host) as NavHostFragment).navController
    binding.bottomNav.setupWithNavController(navController)
    binding.bottomNav.applyBottomInset()

    val topLevel = setOf(
        R.id.homeFragment, R.id.practiceFragment, R.id.topicsFragment, R.id.progressFragment,
    )
    navController.addOnDestinationChangedListener { _, destination, _ ->
        binding.bottomNav.isVisible = destination.id in topLevel
    }

    // Onboarding gate: navigate once, the first time we know the persisted answer.
    lifecycleScope.launch {
        val first = vm.progress.first()
        if (!first.onboarded) navController.navigate(R.id.onboardingFragment)
    }

    lifecycleScope.launch {
        vm.progress.map { it.theme }.distinctUntilChanged().collect(::applyTheme)
    }
    lifecycleScope.launch {
        vm.snacks.collect { showDesignSnackbar(binding.root, it) }
    }
}

override fun onStop() {
    super.onStop()
    lifecycleScope.launch { (application as CitizenshipApp).progressRepository.flush() }
}

private fun applyTheme(name: String) = AppCompatDelegate.setDefaultNightMode(
    when (name) {
        "Light" -> AppCompatDelegate.MODE_NIGHT_NO
        "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
)
```

Style the `BottomNavigationView` to the design: `app:itemBackground` a pill selector of `colorPrimaryContainer`, `app:itemIconTint` and `app:itemTextColor` a selector of `colorOnPrimaryContainer` (checked) / `colorOnSurfaceVariant` (unchecked), `android:background="?attr/colorSurfaceContainer"`, `app:elevation="0dp"`, plus a 1dp top divider of `colorOutlineVariant` (a `View` above it in the layout), `app:labelVisibilityMode="labeled"`, and `Text.Nav` as the label appearance.

`showDesignSnackbar` lives in `ui/common/Snack.kt`: a `Snackbar` with `colorSurfaceInverse` background, `colorOnSurfaceInverse` text, `radius_xs` shape, 16dp side margins, 88dp bottom offset and `Text.Body` — matching the design's `#372F2D` pill.

- [ ] **Step 4: Write the navigation test**

```kotlin
package com.mvnsh.citizenship

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.action.ViewActions.click
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test fun the_four_tabs_each_reach_their_destination() {
        listOf(
            R.id.practiceFragment to "Practice",
            R.id.topicsFragment to "Topics",
            R.id.progressFragment to "Progress",
            R.id.homeFragment to "Home",
        ).forEach { (tabId, label) ->
            onView(withId(tabId)).perform(click())
            onView(withText(label)).check(matches(isDisplayed()))
        }
    }

    @Test fun the_bottom_nav_hides_on_a_non_top_level_destination_and_returns() {
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
        rule.scenario.onActivity { it.findNavController(R.id.nav_host).navigate(R.id.settingsFragment) }
        onView(withId(R.id.bottom_nav)).check(matches(not(isDisplayed())))
        pressBack()
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
    }
}
```

The onboarding gate will fire on a first-ever install and can make this test navigate away. Guard it: the test's `@Before` writes `ProgressState(onboarded = true)` through the app's `ProgressRepository` before the activity is created, using `ApplicationProvider.getApplicationContext<CitizenshipApp>().progressRepository.replace(...)` inside `runBlocking`. Do the same in every later Espresso test that is not specifically testing onboarding — factor it into an `@Before` in a shared `BaseUiTest` class.

- [ ] **Step 5: Run and commit**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
git add -A
git commit -m "feat: navigation shell, bottom nav and the single app ViewModel

The design's route/tab state machine becomes the nav graph; the
ViewModel keeps only bank, persisted progress and the live session.
Bottom nav appears on exactly the four top-level destinations, matching
the design's showNav."
```

---

## Task 6: Onboarding

**Files:**
- Create: `ui/onboarding/{OnboardingFragment,OnboardWelcomeFragment,OnboardPaceFragment,OnboardHowFragment}.kt`
- Create: `res/layout/{fragment_onboarding,fragment_onboard_welcome,fragment_onboard_pace,fragment_onboard_how}.xml`
- Modify: `res/values/strings.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/OnboardingTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.setGoalTarget`, `setTestDate`, `finishOnboarding`, `progress`; `DateUtils.shiftDay`.
- Produces: nav destination `onboardingFragment`. On finish it calls `finishOnboarding()` then `navigate(R.id.homeFragment)` with `popUpTo(R.id.onboardingFragment) { inclusive = true }`.

`OnboardingFragment` hosts a `ViewPager2` with `isUserInputEnabled = false` (the design moves only via its buttons) over the three page fragments. Padding 22dp horizontal, 24dp top, 26dp bottom; root applies both insets.

- [ ] **Step 1: Add the copy to `strings.xml`, verbatim**

Page 1 (`ob0`): title `Pass the citizenship test.` (`Text.Display.Hero`); body `Short daily practice on the real question bank, with the questions you keep missing brought back to you.` (`Text.Body` 16sp, `md_on_surface_variant`, max 32ch); three feature rows at 14sp with a leading check icon — `501 questions across seven topics`, `Works offline once downloaded`, `Timed mock tests, marked like the real one`; primary CTA `Get started`; text button `I've used this before` (calls `finishOnboarding` and skips straight to home). A 64dp `radius_lg` `md_primary` tile holds the app glyph.

Page 2 (`ob1`): back arrow; title `Set a pace`; subtitle `Both optional. You can change them any time in settings.`; overline `When is your test?` with three full-width radio rows — `In about a month` (`shiftDay(30)`), `In about 3 months` (`shiftDay(90)`), `Not scheduled yet` (`null`); overline `Daily goal` with three equal chips `10 a day` / `20 a day` / `30 a day`; CTA `Continue`; text button `Skip for now`.

Selected radio row: `md_primary_container` fill, `md_on_primary_container` text at weight 600, leading filled check circle. Unselected: `Card.Bordered` + a 20dp ring of `md_outline` at 1.5dp. Selected goal chip: `md_secondary` fill, `md_on_secondary` text, weight 600. Unselected: `Card.Bordered`, `md_on_surface_variant`.

Page 3 (`ob2`): back arrow; title `How this works`; three numbered rows (34dp `md_primary_container` circle, numeral in `Text.Stat.S`, `md_on_primary_container`):
1. `Practise a little, daily` — `Ten minutes beats an hour on Sunday. Your goal and streak are there to make the habit visible, nothing more.`
2. `Missed questions come back` — `Anything you get wrong twice joins your weak list and returns more often until it sticks.`
3. `Mock test when you're ready` — `Twenty questions, thirty minutes, fifteen to pass — the same shape as the real test.`

CTA `Start studying` → `finishOnboarding()` + navigate to home.

- [ ] **Step 2: Write the failing onboarding test**

```kotlin
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @Before fun freshInstall() = runBlocking {
        app().progressRepository.replace(ProgressState(onboarded = false))
    }

    @get:Rule val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test fun a_fresh_install_lands_on_onboarding_not_home() {
        onView(withText("Pass the citizenship test.")).check(matches(isDisplayed()))
    }

    @Test fun choosing_a_pace_persists_and_finishing_reaches_home() {
        onView(withText("Get started")).perform(click())
        onView(withText("In about a month")).perform(click())
        onView(withText("30 a day")).perform(click())
        onView(withText("Continue")).perform(click())
        onView(withText("Start studying")).perform(click())

        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
        val p = app().progressRepository.state.value
        assertTrue(p.onboarded)
        assertEquals(30, p.goalTarget)
        assertEquals(DateUtils.shiftDay(30), p.testDate)
    }

    @Test fun the_shortcut_skips_straight_to_home_and_marks_onboarded() {
        onView(withText("I've used this before")).perform(click())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
        assertTrue(app().progressRepository.state.value.onboarded)
    }

    @Test fun back_from_page_two_returns_to_page_one() {
        onView(withText("Get started")).perform(click())
        onView(withText("Set a pace")).check(matches(isDisplayed()))
        onView(withId(R.id.ob_back)).perform(click())
        onView(withText("Pass the citizenship test.")).check(matches(isDisplayed()))
    }
}
```

`app()` is a helper on the shared `BaseUiTest`: `ApplicationProvider.getApplicationContext<CitizenshipApp>()`.

- [ ] **Step 3: Run to confirm it fails, build the three pages, run again**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.OnboardingTest"
```

Expected first: failure — no onboarding destination content. After implementing: 4 tests passing.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: three-page onboarding with pace selection"
```

---

## Task 7: Home screen

**Files:**
- Modify: `ui/home/HomeFragment.kt`, `res/layout/fragment_home.xml`
- Create: `res/layout/view_goal_card.xml`, `res/drawable/{bg_progress_track,bg_goal_track}.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/HomeTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.progress`, `session`, `bank`, `resumeOrStart()`, `startSession()`, `Stats.topicStats`, `Stats.accuracy`, `StudyEngine.weakIds`, `DateUtils`.
- Produces: nothing consumed by later tasks. This task establishes **the Fragment pattern every later screen follows** — copy it.

### The Fragment pattern (all screens follow this)

```kotlin
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm by lazy { appViewModel() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)
        binding.root.applyTopInset()
        binding.scroll.applyBottomInset()
        Motion.rise(binding.root)

        binding.continueCta.setOnClickListener { vm.resumeOrStart() }
        // ...remaining click listeners, set once, never inside the render pass

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.progress, vm.session, vm.bank) { p, s, b -> Triple(p, s, b) }
                    .collect { (p, s, b) -> render(p, s, b) }
            }
        }
    }

    private fun render(p: ProgressState, s: SessionState?, b: BankRepository.Bank?) { /* ... */ }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
```

Rules that apply to every screen: bind in `onViewCreated`, null the binding in `onDestroyView`, register listeners exactly once outside `render`, and drive all text/visibility from a single `render(state)` function so there is one place to read.

- [ ] **Step 1: Write the failing home test**

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeTest : BaseUiTest() {

    @Test fun the_goal_card_shows_progress_towards_the_target() = withProgress(
        ProgressState(onboarded = true, goalTarget = 20, goalDone = 14,
            goalDate = DateUtils.today(), streak = 12, answered = 340, correct = 279)
    ) {
        onView(withText("Today's goal")).check(matches(isDisplayed()))
        onView(withId(R.id.goal_count)).check(matches(withText("14")))
        onView(withId(R.id.goal_target)).check(matches(withText("/ 20 questions")))
        onView(withText("6 to go · about 3 minutes")).check(matches(isDisplayed()))
        onView(withText("12 days")).check(matches(isDisplayed()))
        onView(withText("Day 12 of studying")).check(matches(isDisplayed()))
    }

    @Test fun a_met_goal_swaps_the_line() = withProgress(
        ProgressState(onboarded = true, goalTarget = 20, goalDone = 20, goalDate = DateUtils.today())
    ) {
        onView(withText("Goal met · well done")).check(matches(isDisplayed()))
    }

    @Test fun with_no_test_date_the_prompt_replaces_the_countdown() = withProgress(
        ProgressState(onboarded = true, testDate = null)
    ) {
        onView(withText("Add your test date")).check(matches(isDisplayed()))
        onView(withText("We'll pace the practice to it")).check(matches(isDisplayed()))
    }

    @Test fun with_a_test_date_the_countdown_shows_days_remaining() = withProgress(
        ProgressState(onboarded = true, testDate = DateUtils.shiftDay(38), answered = 100, correct = 85)
    ) {
        onView(withId(R.id.days_left)).check(matches(withText("38")))
        onView(withText("On pace — 85% accuracy")).check(matches(isDisplayed()))
    }

    @Test fun a_fresh_install_invites_a_first_session() = withProgress(ProgressState(onboarded = true)) {
        onView(withText("Ready when you are")).check(matches(isDisplayed()))
        onView(withText("Start studying")).check(matches(isDisplayed()))
        onView(withText("20 questions · about 10 minutes")).check(matches(isDisplayed()))
    }

    @Test fun an_unfinished_session_becomes_a_resume_prompt() = withProgress(
        ProgressState(onboarded = true, session = SessionState(
            mode = "QUICK", label = "Quick practice", ids = listOf(1, 2, 3, 4, 5),
            index = 2, startedAtEpochMs = System.currentTimeMillis()))
    ) {
        onView(withText("Resume session")).check(matches(isDisplayed()))
        onView(withText("Quick practice · question 3 of 5")).check(matches(isDisplayed()))
    }

    @Test fun the_two_tiles_navigate_to_weak_and_mock() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(3, 2), 5 to SeenStat(4, 3)))
    ) {
        onView(withId(R.id.weak_count)).check(matches(withText("2")))
        onView(withId(R.id.weak_tile)).perform(click())
        onView(withText("Weak questions")).check(matches(isDisplayed()))
        pressBack()
        onView(withId(R.id.mock_tile)).perform(click())
        onView(withText("Same shape as the real test")).check(matches(isDisplayed()))
    }
}
```

Write `BaseUiTest` in this task; every later UI test extends it. Its three helpers are the only setup those tests need:

```kotlin
package com.mvnsh.citizenship.ui

abstract class BaseUiTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    protected fun app(): CitizenshipApp = ApplicationProvider.getApplicationContext()

    /** Seeds persisted state, launches the activity, runs the block, then tears down. */
    protected fun withProgress(state: ProgressState, block: () -> Unit) {
        runBlocking { app().progressRepository.replace(state) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        try { block() } finally { scenario?.close(); scenario = null }
    }

    /** Jumps straight to a destination that is not reachable from the current screen. */
    protected fun navigateTo(destinationId: Int) {
        scenario!!.onActivity { it.findNavController(R.id.nav_host).navigate(destinationId) }
    }

    /** For the few assertions that need the live activity. */
    protected val rule: ActivityScenario<MainActivity> get() = scenario!!
}
```

Tests that need a genuinely fresh install (only `OnboardingTest`) seed `ProgressState(onboarded = false)`; everything else seeds `onboarded = true` so the onboarding gate does not intercept.

- [ ] **Step 2: Run to confirm it fails**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.HomeTest"
```

Expected: failures — the placeholder home fragment has none of this.

- [ ] **Step 3: Build the layout, top to bottom**

Root: `NestedScrollView` (id `scroll`) over a vertical `LinearLayout`, 16dp horizontal / 12dp top / 20dp bottom padding, 14dp gaps.

| Element | Spec | Copy / binding |
|---|---|---|
| Greeting | `Text.Body.Small`, `md_on_surface_variant` | `Good morning` / `Good afternoon` / `Good evening` — split at 12:00 and 18:00 |
| Day line | `Text.Headline.Screen` | `Day {streak} of studying`, or `Ready when you are` when `streak == 0` |
| Search button | 44dp circle, `md_surface_container`, `ic_search` | → `searchFragment` |
| Settings button | 44dp circle, `md_primary_container`, `ic_settings` tinted `md_on_primary_container` | → `settingsFragment` (see deviation 3) |
| Goal card | `md_primary_container`, `radius_xl`, 20dp padding, 13dp gaps | overline `Today's goal`; count `Text.Stat.L` + `/ {target} questions` |
| Goal bar | 8dp tall, `radius_pill`, track `md_goal_track`, fill `md_primary`, 420ms width animation | `min(100, round(goalDone / goalTarget * 100))` |
| Goal footer | `Text.Meta`, `md_on_primary_container` | `Goal met · well done` when met, else `{left} to go · about {minutes} minutes` where `minutes = max(1, round(left * 0.5))` |
| Streak | `ic_flame` + `{streak} days` | right-aligned in the footer row |
| Hero CTA | `md_primary` / `md_on_primary`, `radius_xl`, 20dp × 22dp, trailing 44dp 18%-white circle with `ic_chevron_right` | title 19sp/600 `Resume session` or `Start studying`; sub 13sp at 85% alpha — `{label} · question {index+1} of {size}` when resuming, else `{goalTarget} questions · about {round(goalTarget*0.5)} minutes` |
| Weak tile | half width, `md_surface_container`, `radius_md`, 16dp | `ic_warning`; count `Text.Stat.XL`; label `Weak questions` |
| Mock tile | half width, `md_secondary_container`, `radius_md`, 16dp | `ic_clock` tinted `md_on_secondary_container`; title 15.5sp/600 in `md_on_secondary_container_strong` `Mock test`; sub `20 questions · 30 min` |
| Countdown card | `Card.Bordered`, `radius_md`, 16dp; 52dp numeral column, 1dp × 34dp divider | `{daysLeft}` in `Text.Stat.XL` `md_primary`, `DAYS` overline-ish 10.5sp; `Test on {fmtDate}`; accuracy line |
| No-date card | same shape, tappable | `ic_calendar`; `Add your test date` / `We'll pace the practice to it` → `settingsFragment` |
| Weakest topic | `md_surface_container`, `radius_md`, 16dp | overline `Weakest topic`; `{name}`; `{accuracy}% accuracy`; trailing chevron → that topic's detail |

Accuracy line: `On pace — {acc}% accuracy` when `acc >= 80`; `Keep going — {acc}% accuracy` when `acc > 0`; `No answers yet` otherwise.

Weakest topic = the lowest-accuracy topic among those with `done > 0`; the card is hidden when nothing has been practised.

- [ ] **Step 4: Run to confirm it passes**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.HomeTest"
```

Expected: 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: home screen — goal card, resume CTA, weak/mock tiles, countdown"
```

---

## Task 8: Practice, Topics and Topic detail

**Files:**
- Modify: `ui/practice/PracticeFragment.kt`, `ui/topics/TopicsFragment.kt`; create `ui/practice/PracticeModeAdapter.kt`, `ui/topics/TopicRowAdapter.kt`, `ui/topics/TopicDetailFragment.kt`, `ui/topics/TopicQuestionAdapter.kt`
- Create: `res/layout/{fragment_practice,fragment_topics,fragment_topic_detail,item_practice_mode,item_topic_row,item_topic_question}.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/PracticeAndTopicsTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.startSession`, `progress`, `bank`, `toggleBookmark`; `Stats.topicStats`; `StudyEngine.weakIds`; `DateUtils.clip`.
- Produces: `topicDetailFragment` accepting a `topicKey: String` argument (Safe Args `TopicDetailFragmentArgs`).

### The adapter pattern (all lists follow this)

```kotlin
class PracticeModeAdapter(
    private val onClick: (PracticeMode) -> Unit,
) : ListAdapter<PracticeMode, PracticeModeAdapter.VH>(DIFF) {

    class VH(val binding: ItemPracticeModeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemPracticeModeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.binding) {
        val row = getItem(position)
        title.text = row.title
        subtitle.text = row.subtitle
        meta.text = row.meta
        root.setOnClickListener { onClick(row) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PracticeMode>() {
            override fun areItemsTheSame(a: PracticeMode, b: PracticeMode) = a.id == b.id
            override fun areContentsTheSame(a: PracticeMode, b: PracticeMode) = a == b
        }
    }
}
```

Every list in this app is a `ListAdapter` over an immutable row data class with a stable identity field, submitted from the fragment's `render`. Never mutate a row in place; never put business logic in a ViewHolder.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class PracticeAndTopicsTest : BaseUiTest() {

    @Test fun practice_lists_the_six_modes_with_live_counts() = withProgress(
        ProgressState(onboarded = true, bookmarks = listOf(2, 7),
            seen = mapOf(1 to SeenStat(3, 2), 5 to SeenStat(2, 2), 9 to SeenStat(1, 0)))
    ) {
        onView(withId(R.id.practiceFragment)).perform(click())
        onView(withText("Quick practice")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.meta), hasSibling(withText("All questions")))).check(matches(withText("501")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("Weak questions")))).check(matches(withText("2")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("Bookmarked")))).check(matches(withText("2")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("New to you")))).check(matches(withText("498")))
        onView(allOf(withId(R.id.meta), hasSibling(withText("By topic")))).check(matches(withText("7")))
    }

    @Test fun quick_practice_starts_a_ten_question_session() = withProgress(ProgressState(onboarded = true)) {
        onView(withId(R.id.practiceFragment)).perform(click())
        onView(withText("Quick practice")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/10")))
    }

    @Test fun topics_lists_all_seven_in_the_designs_order() = withProgress(ProgressState(onboarded = true)) {
        onView(withId(R.id.topicsFragment)).perform(click())
        onView(withText("Seven topics, drawn from the official study guide.")).check(matches(isDisplayed()))
        Topics.all.forEach { onView(withText(it.name)).check(matches(isDisplayed())) }
    }

    @Test fun an_unpractised_topic_shows_an_em_dash_not_zero_percent() = withProgress(ProgressState(onboarded = true)) {
        onView(withId(R.id.topicsFragment)).perform(click())
        onView(allOf(withId(R.id.accuracy), hasSibling(withText("Economy")))).check(matches(withText("—")))
    }

    @Test fun topic_detail_shows_counts_and_a_capped_question_list() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(2, 0), 2 to SeenStat(3, 2)))
    ) {
        onView(withId(R.id.topicsFragment)).perform(click())
        onView(withText("Rights & Responsibilities")).perform(click())
        onView(withId(R.id.cat_total)).check(matches(withText("43")))
        onView(withId(R.id.cat_done)).check(matches(withText("2")))
        onView(withText("Practise 20 questions")).check(matches(isDisplayed()))
        onView(withText("Missed 2×")).check(matches(isDisplayed()))
    }

    @Test fun topic_detail_starts_a_topic_session_of_that_topic_only() = withProgress(ProgressState(onboarded = true)) {
        onView(withId(R.id.topicsFragment)).perform(click())
        onView(withText("Economy")).perform(click())
        onView(withText("Practise 20 questions")).perform(click())
        // Economy has only 14 questions, so the cap of 20 does not apply.
        onView(withId(R.id.q_counter)).check(matches(withText("1/14")))
        onView(withId(R.id.q_topic)).check(matches(withText("Economy")))
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then build the three screens**

**Practice** — 16/12/20 padding, 14dp gaps. Title `Practice` in `Text.Headline.Screen`. A pill search entry (`md_surface_container`, `radius_pill`, 14dp × 18dp) reading `Search all questions` → `searchFragment`. Then the six mode rows (`Card.Bordered`, `radius_md`, 17dp × 18dp, 8dp gaps): title 16sp/600, sub `Text.Meta`, trailing count in `Text.Stat.Inline`, then `ic_chevron_right` in `md_outline`:

| Title | Subtitle | Meta | Action |
|---|---|---|---|
| `Quick practice` | `10 questions, mixed topics` | `10` | `startSession(QUICK)` |
| `All questions` | `Work through the whole bank in order` | bank size | `startSession(ALL)` |
| `Weak questions` | `Missed two or more times` | weak count | → `weakFragment` |
| `Bookmarked` | `Questions you saved` | bookmark count | → `bookmarksFragment` |
| `New to you` | `Questions you have never seen` | unseen count | `startSession(UNSEEN)` |
| `By topic` | `Choose one of seven topics` | `7` | → `topicsFragment` |

Footer card: `md_secondary_container`, `radius_md`, 18dp — `Take a mock test` / `Timed, marked like the real thing` → `mockIntroFragment`.

**Topics** — title `Topics`, subtitle `Seven topics, drawn from the official study guide.` Seven rows (`Card.Bordered`, `radius_md`, 16dp × 18dp, 10dp gaps), each: name 16sp/600 + blurb `Text.Meta` on the left; on the right `accuracyText` in `Text.Stat.Inline` over `accuracy` in 11sp `md_text_muted`; below, a 6dp coverage bar (track `md_surface_container_high`, fill `md_secondary`) at `coveragePct` and `{done} / {total}` in 11.5sp.

**Topic detail** — back arrow + name in `Text.Title.Card`. Header card `md_surface_container`, `radius_lg`, 18dp: blurb; three numerals (`Text.Stat.XL`) labelled `questions` / `practised` / `accuracy` (the third tinted `md_primary`); a 6dp `md_secondary` coverage bar over an `md_outline_variant` track. Pill CTA `Practise 20 questions` → `startSession(TOPIC, topicKey)`. Overline `Questions`, then up to 24 rows (`Card.Bordered`, `radius_xs`, 14dp, 8dp gaps): a 9dp status dot — `md_secondary` when seen with no misses, `md_error` when missed, a 1.5dp `md_outline` ring when unseen — then `clip(question, 74)` at 14sp with a status line (`New` / `Correct` / `Missed {m}×`) in 11.5sp, then a flag toggle.

- [ ] **Step 3: Run to confirm it passes and commit**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.PracticeAndTopicsTest"
git add -A && git commit -m "feat: practice modes, topic list and topic detail"
```

---

## Task 9: Quiz screen

**Files:**
- Create: `ui/quiz/QuizFragment.kt`, `ui/quiz/OptionAdapter.kt`, `ui/quiz/OptionStyle.kt`
- Create: `res/layout/{fragment_quiz,item_option}.xml`, `res/drawable/bg_option.xml`
- Test: `app/src/test/java/com/mvnsh/citizenship/ui/OptionStyleTest.kt`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/QuizTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.session`, `progress`, `bank`, `pick`, `next`, `toggleBookmark`; `StudyEngine.optionOrder`.
- Produces:
  - `data class OptionRow(val optionIndex: Int, val letter: String, val text: String, val marker: Marker, val badge: Badge, val style: OptionStyle)` with `enum class Marker { LETTER, LETTER_SELECTED, CHECK, CROSS }` and `enum class Badge { NONE, CORRECT, YOURS }`
  - `enum class OptionStyle { PLAIN, SELECTED, CORRECT, WRONG }` with `fun OptionStyle.background(): Int`, `.stroke(): Int`, `.textColor(): Int`, `.weight(): Int` returning theme attrs
  - `object OptionRows` with `fun build(q: Question, pickedIndex: Int?, revealed: Boolean, mock: Boolean): List<OptionRow>`

Extracting row construction into a pure `OptionRows.build` is the point of this task — the design's option styling has four visual states across two modes, and it is the most error-prone thing in the app. Test it on the JVM, not through the UI.

- [ ] **Step 1: Write the failing `OptionStyleTest`**

```kotlin
package com.mvnsh.citizenship.ui

import com.mvnsh.citizenship.data.model.Question
import com.mvnsh.citizenship.ui.quiz.Badge
import com.mvnsh.citizenship.ui.quiz.Marker
import com.mvnsh.citizenship.ui.quiz.OptionRows
import com.mvnsh.citizenship.ui.quiz.OptionStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionStyleTest {

    // id 4 with 4 options rotates by 0, so display order == source order.
    private val q = Question(
        id = 4, topic = "rights", question = "In what year?",
        options = listOf("1867", "1921", "1982", "2015"), answer = 2,
    )

    @Test fun before_answering_every_row_is_plain_and_lettered() {
        val rows = OptionRows.build(q, pickedIndex = null, revealed = false, mock = false)
        assertEquals(4, rows.size)
        assertEquals(listOf("A", "B", "C", "D"), rows.map { it.letter })
        assertEquals(List(4) { OptionStyle.PLAIN }, rows.map { it.style })
        assertEquals(List(4) { Marker.LETTER }, rows.map { it.marker })
        assertEquals(List(4) { Badge.NONE }, rows.map { it.badge })
    }

    @Test fun a_correct_reveal_marks_the_answer_and_nothing_else() {
        val rows = OptionRows.build(q, pickedIndex = 2, revealed = true, mock = false)
        val correct = rows.first { it.optionIndex == 2 }
        assertEquals(OptionStyle.CORRECT, correct.style)
        assertEquals(Marker.CHECK, correct.marker)
        assertEquals(Badge.CORRECT, correct.badge)
        rows.filter { it.optionIndex != 2 }.forEach {
            assertEquals(OptionStyle.PLAIN, it.style)
            assertEquals(Badge.NONE, it.badge)
        }
    }

    @Test fun a_wrong_reveal_marks_both_the_pick_and_the_answer() {
        val rows = OptionRows.build(q, pickedIndex = 0, revealed = true, mock = false)
        val yours = rows.first { it.optionIndex == 0 }
        val answer = rows.first { it.optionIndex == 2 }
        assertEquals(OptionStyle.WRONG, yours.style)
        assertEquals(Marker.CROSS, yours.marker)
        assertEquals(Badge.YOURS, yours.badge)
        assertEquals(OptionStyle.CORRECT, answer.style)
        assertEquals(Marker.CHECK, answer.marker)
        assertEquals(Badge.CORRECT, answer.badge)
    }

    @Test fun in_a_mock_a_pick_is_only_highlighted_never_graded() {
        val rows = OptionRows.build(q, pickedIndex = 0, revealed = false, mock = true)
        val picked = rows.first { it.optionIndex == 0 }
        assertEquals(OptionStyle.SELECTED, picked.style)
        assertEquals(Marker.LETTER_SELECTED, picked.marker)
        assertEquals("a mock never reveals the answer", Badge.NONE, picked.badge)
        rows.filter { it.optionIndex != 0 }.forEach { assertEquals(OptionStyle.PLAIN, it.style) }
    }

    @Test fun rows_are_presented_in_the_rotated_order_with_letters_following_position() {
        // id 1 rotates by 1, so the source order 0,1,2,3 displays as 1,2,3,0.
        val rotated = q.copy(id = 1)
        val rows = OptionRows.build(rotated, pickedIndex = null, revealed = false, mock = false)
        assertEquals(listOf(1, 2, 3, 0), rows.map { it.optionIndex })
        assertEquals(listOf("A", "B", "C", "D"), rows.map { it.letter })
        assertEquals(listOf("1921", "1982", "2015", "1867"), rows.map { it.text })
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then write `OptionStyle.kt`**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.ui.OptionStyleTest"
```

Then implement `OptionRows.build` exactly as the tests describe: iterate `StudyEngine.optionOrder(q)` with the position index supplying `letter = "ABCD"[position]`; `revealed` is only ever true outside mock mode; `CORRECT`/`WRONG` are assigned only when `revealed`.

The four styles map to these token pairs (fill / 1.5dp stroke / text / weight):

| Style | Fill | Stroke | Text | Weight |
|---|---|---|---|---|
| `PLAIN` | `md_surface_container_lowest` | `md_outline_variant` at 1dp | `md_on_surface` | 400 |
| `SELECTED` | `md_primary_container` | `md_primary` | `md_on_primary_container` | 600 |
| `CORRECT` | `md_secondary_container` | `md_secondary` | `md_on_secondary_container_strong` | 600 |
| `WRONG` | `md_error_container` | `md_error` | `md_on_error_container` | 500 |

Apply them by mutating a `MaterialShapeDrawable`/`MaterialCardView` on the row, with a 180ms background colour animation (`ValueAnimator` + `ArgbEvaluator`, `Motion.STANDARD`) — the design animates `background 180ms`.

- [ ] **Step 3: Write the failing quiz UI test**

```kotlin
@RunWith(AndroidJUnit4::class)
class QuizTest : BaseUiTest() {

    private fun startedQuiz(vararg ids: Int) = ProgressState(
        onboarded = true,
        session = SessionState(mode = "ALL", label = "All questions",
            ids = ids.toList(), startedAtEpochMs = System.currentTimeMillis()),
    )

    @Test fun the_header_shows_topic_counter_and_progress() = withProgress(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.q_counter)).check(matches(withText("1/3")))
        onView(withId(R.id.q_topic)).check(matches(withText("Rights & Responsibilities")))
        onView(withText("Who is regulated by laws in Canada?")).check(matches(isDisplayed()))
        onView(withText("Skip")).check(matches(isDisplayed()))
    }

    @Test fun a_correct_answer_reveals_the_explanation_and_the_next_button() = withProgress(startedQuiz(1, 2)) {
        onView(withText("Both A and B")).perform(click())
        onView(withText("Why")).check(matches(isDisplayed()))
        onView(withText(containsString("The rule of law means everyone is regulated by law"))).check(matches(isDisplayed()))
        onView(withText("Next question")).check(matches(isDisplayed()))
        onView(withText("Skip")).check(matches(doesNotExist()))
        assertEquals(1, app().progressRepository.state.value.correct)
    }

    @Test fun a_wrong_answer_is_recorded_and_the_answer_is_shown() = withProgress(startedQuiz(1, 2)) {
        onView(withText("Individuals")).perform(click())
        onView(withText("Your pick")).check(matches(isDisplayed()))
        onView(withText("Correct")).check(matches(isDisplayed()))
        val p = app().progressRepository.state.value
        assertEquals(1, p.answered)
        assertEquals(0, p.correct)
        assertEquals(1, p.seen[1]!!.m)
    }

    @Test fun tapping_a_second_option_after_revealing_changes_nothing() = withProgress(startedQuiz(1, 2)) {
        onView(withText("Individuals")).perform(click())
        onView(withText("Governments")).perform(click())
        assertEquals("a revealed question is locked", 1, app().progressRepository.state.value.answered)
    }

    @Test fun a_question_with_no_authored_explanation_shows_the_answer_panel_instead() = withProgress(startedQuiz(4)) {
        onView(withText("1982")).perform(click())
        onView(withText("Answer")).check(matches(isDisplayed()))
        onView(withText(containsString("An explanation for this question hasn't been written yet"))).check(matches(isDisplayed()))
    }

    @Test fun the_last_question_offers_results_and_reaches_them() = withProgress(startedQuiz(1)) {
        onView(withText("Both A and B")).perform(click())
        onView(withText("See results")).perform(click())
        onView(withText("All questions · session complete")).check(matches(isDisplayed()))
    }

    @Test fun skip_advances_without_recording_an_answer() = withProgress(startedQuiz(1, 2)) {
        onView(withText("Skip")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("2/2")))
        assertEquals(0, app().progressRepository.state.value.answered)
    }

    @Test fun the_flag_toggles_the_bookmark_for_this_question() = withProgress(startedQuiz(1, 2)) {
        onView(withId(R.id.bookmark)).perform(click())
        assertEquals(listOf(1), app().progressRepository.state.value.bookmarks)
        onView(withId(R.id.bookmark)).perform(click())
        assertEquals(emptyList<Int>(), app().progressRepository.state.value.bookmarks)
    }

    @Test fun leaving_offers_to_keep_or_discard_the_session() = withProgress(startedQuiz(1, 2, 3)) {
        onView(withId(R.id.exit)).perform(click())
        onView(withText("Leave this session?")).check(matches(isDisplayed()))
        onView(withText("Save and exit")).perform(click())
        onView(withText("Resume session")).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 4: Build the quiz layout**

16/12/20 padding, 16dp gaps, `min-height` filling the screen so the footer sits at the bottom.

Header row: `ic_arrow_left` exit button (opens `ExitSessionDialog`); a 6dp `radius_pill` progress bar (track `md_surface_container_high`, fill `md_primary`, 320ms animation) at `round((index + if (revealed) 1 else 0) / size * 100)`; counter `{index+1}/{size}` in `Text.Body.Small` with tabular numerals; a flag toggle (`ic_flag_filled` tinted `md_primary` when bookmarked, else `ic_flag_outline` stroked `md_outline`).

Body: topic badge (`md_secondary_container`, `radius_badge`, 5dp × 12dp, 12sp/600, `md_on_secondary_container`), then the question in `Text.Question`.

Options: a `RecyclerView` (or a plain `LinearLayout` — four fixed rows, no recycling needed; a `LinearLayout` avoids nested-scroll friction and is the better choice here) of `item_option`: 15dp padding, `radius_md`, 14dp gap, a 28dp leading marker, then the text in `Text.Option`, then the badge in `Text.Badge` (`Correct` in `md_on_secondary_container`, `Your pick` in `md_on_error_container`). Markers: 28dp circle — `LETTER` is a 1.5dp `md_outline` ring with the letter in 13sp/600; `LETTER_SELECTED` is a filled `md_primary` circle with white letter; `CHECK` is filled `md_secondary` with `ic_check`; `CROSS` is filled `md_error` with `ic_close`. `CHECK` and `CROSS` play `Motion.pop`.

Explanation panel (`md_surface_container`, `radius_md`, 16dp, `Motion.rise` on appear), two variants:
- With an authored explanation: overline `Why`, the `why` text at 14.5sp/1.55, then the `tip` at 13sp above a 1dp `md_outline_variant` top border.
- Without: overline `Answer`, the correct option text at 15sp/600, then `An explanation for this question hasn't been written yet. {seenLine}.` at 12.5sp in `md_text_muted`.

`seenLine` is `Seen {s}×` plus ` · missed {m}×` when `m > 0`, or `First time seeing this` when the question has no record.

Footer: when revealed, a full-width `md_primary` pill (17dp) reading `Next question`, or `See results` on the last question. When not revealed, the `seenLine` on the left in 12.5sp `md_text_muted` and a text button `Skip` in `md_primary` on the right.

- [ ] **Step 5: Run both suites, then commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.ui.OptionStyleTest"
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.QuizTest"
git add -A && git commit -m "feat: quiz screen with four-state option styling

Row construction is a pure function (OptionRows.build) covered by JVM
tests, because the design distinguishes four option states across two
modes and getting it wrong is silent."
```

---

## Task 10: Practice results

**Files:**
- Create: `ui/quiz/ResultsFragment.kt`, `ui/quiz/MissedAdapter.kt`, `res/layout/{fragment_results,item_missed}.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/ResultsTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.session`, `progress`, `bank`, `startSession`, `discardSession`.
- Produces: `AppViewModel.practiceMissed()` — starts a new session over exactly the ids missed in the session just finished, labelled `Missed questions`. Add it to `AppViewModel` in this task.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ResultsTest : BaseUiTest() {

    // q1 answer is index 2, q2 answer is index 0.
    private fun finished(marks: Map<Int, Int>, right: Int) = ProgressState(
        onboarded = true, goalTarget = 20, goalDone = 2, goalDate = DateUtils.today(),
        session = SessionState(mode = "QUICK", label = "Quick practice", ids = listOf(1, 2),
            index = 1, marks = marks, right = right, submitted = true, finalRight = right,
            startedAtEpochMs = System.currentTimeMillis() - 120_000),
    )

    @Test fun the_score_headline_and_tiles_reflect_the_session() = withProgress(finished(mapOf(1 to 2, 2 to 1), 1)) {
        onView(withText("Quick practice · session complete")).check(matches(isDisplayed()))
        onView(withId(R.id.score)).check(matches(withText("1")))
        onView(withText("of 2 correct")).check(matches(isDisplayed()))
        onView(withId(R.id.stat_accuracy)).check(matches(withText("50%")))
        onView(withId(R.id.stat_missed)).check(matches(withText("1")))
    }

    @Test fun a_missed_question_is_listed_with_both_answers() = withProgress(finished(mapOf(1 to 0, 2 to 0), 1)) {
        onView(withText("What you missed")).check(matches(isDisplayed()))
        onView(withText("You said: Individuals")).check(matches(isDisplayed()))
        onView(withText("Correct: Both A and B")).check(matches(isDisplayed()))
        onView(withText("Practise the 1 you missed")).check(matches(isDisplayed()))
    }

    @Test fun a_clean_sweep_swaps_in_the_praise_card() = withProgress(finished(mapOf(1 to 2, 2 to 0), 2)) {
        onView(withText("Every answer correct. Questions you get right twice in a row come back less often."))
            .check(matches(isDisplayed()))
        onView(withText(startsWith("Practise the"))).check(matches(doesNotExist()))
    }

    @Test fun practising_the_missed_set_starts_a_session_of_exactly_those() = withProgress(finished(mapOf(1 to 0, 2 to 0), 1)) {
        onView(withText("Practise the 1 you missed")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/1")))
        onView(withText("Who is regulated by laws in Canada?")).check(matches(isDisplayed()))
    }

    @Test fun back_to_home_clears_the_finished_session() = withProgress(finished(mapOf(1 to 2, 2 to 0), 2)) {
        onView(withText("Back to home")).perform(click())
        onView(withText("Start studying")).check(matches(isDisplayed()))
        assertNull(app().progressRepository.state.value.session)
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then build the screen**

16dp all round, 16dp gaps. Label `{session.label} · session complete` in `Text.Body.Small`. Score: `finalRight` in `Text.Display.Score` beside `of {size} correct` at 16sp. When `goalDone >= goalTarget`, an `md_secondary_container` `radius_md` card with `ic_check` and `Daily goal met · {streak}-day streak safe` at 14sp/600.

Three equal `md_surface_container` `radius_md` tiles (14dp): `{pct}%` / `accuracy`, `{wrong}` / `missed`, `{minutes}m` / `spent`, where `minutes = max(1, round((now - startedAt) / 60000))`.

Missed list under overline `What you missed`: `Card.Bordered` `radius_md` 15dp rows — topic in 11.5sp `md_text_muted`, `clip(question, 88)` at 14.5sp, then above a 1dp top border `You said: {yourOption}` in `md_on_error_container` and `Correct: {answer}` in `md_on_secondary_container` at 600. When nothing was missed, replace the list with an `md_secondary_container` `radius_md` 18dp card reading `Every answer correct. Questions you get right twice in a row come back less often.`

Footer pinned to the bottom: when there were misses, a `md_primary` pill `Practise the {n} you missed`; always an `md_surface_container` pill `Back to home` which calls `discardSession()` and navigates to home popping the quiz.

- [ ] **Step 3: Run and commit**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.ResultsTest"
git add -A && git commit -m "feat: practice results with a missed-question breakdown"
```

---

## Task 11: Mock test — intro, timed run, navigator, results, review

**Files:**
- Create: `ui/mock/{MockIntroFragment,MockFragment,MockResultsFragment,MockAttemptAdapter,NavigatorSheet,NavChipAdapter}.kt`
- Create: `ui/review/{ReviewFragment,ReviewAdapter}.kt`
- Create: `res/layout/{fragment_mock_intro,fragment_mock,fragment_mock_results,fragment_review,sheet_navigator,item_mock_attempt,item_nav_chip,item_review}.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/MockTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.startMock`, `session`, `mockSecondsLeft`, `pick`, `next`, `goTo`, `submitMock`, `progress`, `bank`; `StudyEngine.MOCK_*`.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class MockTest : BaseUiTest() {

    private fun mock(ids: List<Int>, marks: Map<Int, Int> = emptyMap(), secondsLeft: Int = 1800) =
        ProgressState(onboarded = true, session = SessionState(
            mode = "MOCK", label = "Mock test", ids = ids, marks = marks, timed = true,
            startedAtEpochMs = System.currentTimeMillis(),
            deadlineEpochMs = System.currentTimeMillis() + secondsLeft * 1000L))

    @Test fun the_intro_states_the_rules() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.mockIntroFragment)
        onView(withText("Same shape as the real test")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Questions")))).check(matches(withText("20")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Time limit")))).check(matches(withText("30 minutes")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("To pass")))).check(matches(withText("15 correct")))
        onView(allOf(withId(R.id.fact_value), hasSibling(withText("Answers")))).check(matches(withText("Changeable until you submit")))
    }

    @Test fun previous_attempts_are_listed_newest_first_and_colour_coded() = withProgress(
        ProgressState(onboarded = true, mocks = listOf(
            MockAttempt(70, DateUtils.shiftDay(-9)), MockAttempt(85, DateUtils.shiftDay(-1))))
    ) {
        navigateTo(R.id.mockIntroFragment)
        onView(withText("Your last attempts")).check(matches(isDisplayed()))
        onView(withText("Mock test 2")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.pct), hasSibling(withText("Mock test 2")))).check(matches(withText("85%")))
    }

    @Test fun starting_a_test_draws_twenty_questions_and_shows_the_timer() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.mockIntroFragment)
        onView(withText("Start the test")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/20")))
        onView(withId(R.id.timer)).check(matches(withText(startsWith("29:"))))
        assertEquals(20, app().progressRepository.state.value.session!!.ids.size)
    }

    @Test fun a_mock_gives_no_feedback_and_the_answer_stays_changeable() = withProgress(mock(listOf(1, 2, 3))) {
        onView(withText("Individuals")).perform(click())
        onView(withText("Why")).check(matches(doesNotExist()))
        onView(withText("Correct")).check(matches(doesNotExist()))
        onView(withText("Governments")).perform(click())
        assertEquals(1, app().progressRepository.state.value.session!!.marks[1])
        assertEquals("a mock records nothing until submit", 0, app().progressRepository.state.value.answered)
    }

    @Test fun the_navigator_shows_answered_state_and_jumps() = withProgress(mock(listOf(1, 2, 3), marks = mapOf(1 to 0))) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Jump to question")).check(matches(isDisplayed()))
        onView(withText("1 of 3 answered")).check(matches(isDisplayed()))
        onView(withText("3")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("3/3")))
    }

    @Test fun the_submit_button_counts_unanswered_questions() = withProgress(
        mock(listOf(1, 2), marks = mapOf(1 to 0))
    ) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit (1/2)")).check(matches(isDisplayed()))
        onView(withText("Keep going")).perform(click())
        onView(withText("Governments")).perform(click())   // answers q1's screen
        onView(withText("Next")).perform(click())
        onView(withText("Individuals")).perform(click())
        onView(withText("Submit test")).check(matches(isDisplayed()))
    }

    @Test fun a_passing_result_shows_the_pass_banner_and_records_the_attempt() = withProgress(
        mock(listOf(1, 2), marks = mapOf(1 to 2, 2 to 0))   // both correct
    ) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit test")).perform(click())
        onView(withText("You would have passed")).check(matches(isDisplayed()))
        onView(withId(R.id.score)).check(matches(withText("2")))
        assertEquals(listOf(MockAttempt(100, DateUtils.today())), app().progressRepository.state.value.mocks)
    }

    @Test fun a_failing_result_says_how_many_more_were_needed() = withProgress(
        mock(listOf(1, 2), marks = mapOf(1 to 0, 2 to 1))   // both wrong
    ) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText("Submit test")).perform(click())
        onView(withText("Close — 15 of 20 is the bar")).check(matches(isDisplayed()))
        onView(withText(containsString("You needed 15 more"))).check(matches(isDisplayed()))
    }

    @Test fun review_lists_every_question_with_its_outcome() = withProgress(
        mock(listOf(1, 2), marks = mapOf(1 to 0))
    ) {
        onView(withId(R.id.navigator)).perform(click())
        onView(withText(startsWith("Submit"))).perform(click())
        onView(withText("Review all 20 answers")).perform(click())
        onView(withText("Review · 0 of 2")).check(matches(isDisplayed()))
        onView(withText("You said: Individuals")).check(matches(isDisplayed()))
        onView(withText("Correct: Both A and B")).check(matches(isDisplayed()))
        onView(withText("Not answered")).check(matches(isDisplayed()))
    }

    @Test fun an_expired_deadline_auto_submits() = withProgress(mock(listOf(1, 2), marks = mapOf(1 to 2), secondsLeft = 1)) {
        onView(withText("Time up — test submitted")).check(matches(isDisplayed()))
        onView(withId(R.id.score)).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then build the five surfaces**

**Mock intro** — back arrow + `Mock test`; headline `Same shape as the real test` in `Text.Headline.Section`; a grouped list of four fact rows (`Questions` `20`, `Time limit` `30 minutes`, `To pass` `15 correct`, `Answers` `Changeable until you submit`); the paragraph `No feedback during the test. You can move between questions freely, and the timer keeps running if you leave the app.` at 14sp/1.6; under overline `Your last attempts`, the attempts newest-first as `md_surface_container` `radius_xs` 13dp × 15dp rows (`Mock test {n}` + `fmtDate`, with the percentage in `Text.Stat.S` tinted `md_on_secondary_container` when `pct >= 75` else `md_error`); a bottom `md_primary` pill `Start the test`.

The attempt label numbers by original order — the newest of three attempts is `Mock test 3` — so reverse for display but keep the original index for the label.

**Mock run** — reuses `item_option` in `mock = true` mode. Header: exit; a `md_surface_container` `radius_pill` button showing `{index+1}/{size}` + `ic_grid` that opens the navigator; a timer pill on the right — `md_surface_container` / `md_on_surface_variant` normally, `md_error_container` / `md_on_error_container` with under 5 minutes left (`secondsLeft < 300`), always tabular numerals, text `DateUtils.mmss(secondsLeft)`. Same topic badge and question type as the quiz. Footer: an `md_surface_container` `Back` pill (only when `index > 0`), then either a flex `md_primary` `Next` pill or, on the last question, a flex `md_secondary` pill reading `Submit test` when all answered else `Submit ({answered}/{total})`.

Observe `mockSecondsLeft` separately from `session` so the timer redraw does not rebuild the option list.

**Navigator sheet** — a `BottomSheetDialogFragment` with `md_surface` background, 28dp top corners, 12dp/20dp/24dp padding, a 34dp × 4dp `md_outline_variant` handle, title `Jump to question` at 17sp/600 with `{answered} of {total} answered` on the right, then a 5-column `GridLayoutManager` of 14dp chips at 14dp vertical padding: current = `md_primary` / white / 600; answered = `md_primary_container` / `md_on_primary_container` / 600; untouched = `Card.Bordered` / `md_on_surface_variant`. Footer: `md_surface_container` `Keep going` and `md_secondary` submit, side by side.

**Mock results** — a banner card (`radius_lg`, 20dp): on a pass, `md_secondary_container` with a 52dp `md_secondary` circle + `ic_check` playing `Motion.pop`, headline `You would have passed` and note `Fifteen correct is the passing mark. Keep the streak going and take another in a few days.`; on a fail, `md_surface_container` with an `md_primary_container` circle, headline `Close — 15 of 20 is the bar` and note `You needed {max(0, 15 - right)} more. The questions you missed are now in your weak list.` Then `finalRight` in `Text.Display.Score` beside `of {total} · {pct}%`; three tiles (`{right}` `correct` tinted `md_on_secondary_container`, `{wrong}` `wrong` tinted `md_error`, `{minutes}m` `of 30`). Footer: `md_primary` pill `Review all 20 answers`; a row of two `md_surface_container` pills `Practise weak areas` and `Retake`; a text button `Back to home`.

Note the review button's label is literal in the design (`Review all 20 answers`) — keep it, since a mock is always 20 questions.

**Review** — back arrow + `Review · {right} of {total}`; one `Card.Bordered` `radius_md` 15dp card per question: a 22dp status circle (`md_secondary` + `ic_check`, or `md_error` + `ic_close`), `{n} · {topic}` in 11.5sp, the full question at 14.5sp, then above a 1dp top border `You said: {yours}` (or `You said: Not answered`) when wrong, `Correct: {answer}` at 600, and the `why` text at 13sp when one exists.

- [ ] **Step 3: Run and commit**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.MockTest"
git add -A && git commit -m "feat: mock test — intro, timed run, navigator sheet, results, review

The timer is derived from an absolute deadline in persisted state, so it
keeps running while the app is backgrounded and auto-submits on expiry."
```

---

## Task 12: Weak questions, Bookmarks and Search

**Files:**
- Create: `ui/lists/{WeakFragment,WeakAdapter,BookmarksFragment,BookmarkAdapter}.kt`, `ui/search/{SearchFragment,SearchAdapter}.kt`
- Create: `res/layout/{fragment_weak,fragment_bookmarks,fragment_search,item_weak,item_bookmark,item_search_hit}.xml`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/ListsAndSearchTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.progress`, `bank`, `startSession`, `toggleBookmark`, `clearWeak`; `StudyEngine.weakIds`; `DateUtils.clip`.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ListsAndSearchTest : BaseUiTest() {

    @Test fun the_weak_list_headlines_the_count_and_offers_practice() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 2), 3 to SeenStat(3, 3)))
    ) {
        navigateTo(R.id.weakFragment)
        onView(withText("You have 2 questions you keep missing.")).check(matches(isDisplayed()))
        onView(withText("Missed 2×")).check(matches(isDisplayed()))
        onView(withText("Practise these now")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/2")))
    }

    @Test fun i_know_this_now_removes_a_question_from_the_weak_list() = withProgress(
        ProgressState(onboarded = true, seen = mapOf(1 to SeenStat(4, 2)))
    ) {
        navigateTo(R.id.weakFragment)
        onView(withText("I know this now")).perform(click())
        onView(withText("Cleared from weak list")).check(matches(isDisplayed()))
        onView(withText("Nothing to review")).check(matches(isDisplayed()))
        assertEquals(0, app().progressRepository.state.value.seen[1]!!.m)
        assertEquals("the attempt history is kept", 4, app().progressRepository.state.value.seen[1]!!.s)
    }

    @Test fun an_empty_weak_list_explains_how_it_fills() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.weakFragment)
        onView(withText("Nothing to review")).check(matches(isDisplayed()))
        onView(withText(containsString("Questions land here after you miss them twice"))).check(matches(isDisplayed()))
        onView(withText("Quick practice")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/10")))
    }

    @Test fun bookmarks_list_and_unbookmark() = withProgress(
        ProgressState(onboarded = true, bookmarks = listOf(2, 7))
    ) {
        navigateTo(R.id.bookmarksFragment)
        onView(withText("Practise bookmarked")).check(matches(isDisplayed()))
        onView(withId(R.id.remove)).perform(click())
        onView(withText("Removed from bookmarks")).check(matches(isDisplayed()))
        assertEquals(listOf(7), app().progressRepository.state.value.bookmarks)
    }

    @Test fun an_empty_bookmark_list_explains_the_flag() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.bookmarksFragment)
        onView(withText("No bookmarks yet")).check(matches(isDisplayed()))
        onView(withText(containsString("Tap the flag on any question"))).check(matches(isDisplayed()))
    }

    @Test fun search_needs_two_characters_then_matches_question_and_option_text() = withProgress(
        ProgressState(onboarded = true)
    ) {
        navigateTo(R.id.searchFragment)
        onView(withText(containsString("Search the whole bank by wording or by answer"))).check(matches(isDisplayed()))
        onView(withId(R.id.query)).perform(typeText("a"))
        onView(withText(containsString("Search the whole bank"))).check(matches(isDisplayed()))
        onView(withId(R.id.query)).perform(typeText("beas corpus"))
        onView(withText(containsString("Habeas Corpus"))).check(matches(isDisplayed()))
    }

    @Test fun search_caps_at_thirty_hits_and_marks_the_overflow() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.searchFragment)
        onView(withId(R.id.query)).perform(typeText("the"))
        onView(withId(R.id.hit_count)).check(matches(withText("30+ results")))
    }

    @Test fun a_search_with_no_match_offers_the_topic_list() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.searchFragment)
        onView(withId(R.id.query)).perform(typeText("zzzzqqq"))
        onView(withText("No question matches that")).check(matches(isDisplayed()))
        onView(withText("Browse topics")).perform(click())
        onView(withText("Seven topics, drawn from the official study guide.")).check(matches(isDisplayed()))
    }

    @Test fun clearing_the_query_returns_to_the_idle_hint() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.searchFragment)
        onView(withId(R.id.query)).perform(typeText("Charter"))
        onView(withId(R.id.clear)).perform(click())
        onView(withId(R.id.query)).check(matches(withText("")))
        onView(withText(containsString("Search the whole bank"))).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then build the three screens**

**Weak** — back arrow + `Weak questions`. When populated: headline `You have {n} questions you keep missing.` in `Text.Headline.Empty` (23sp variant), a `md_primary` pill `Practise these now`, then `Card.Bordered` `radius_md` 15dp rows — topic in 11.5sp `md_text_muted` on the left with `Missed {m}×` in `md_on_error_container` at 600 on the right, `clip(question, 82)` at 14.5sp, then above a 1dp top border two text buttons: `Bookmark` in `md_primary` and `I know this now` in `md_on_surface_variant`. When empty: a centred 60dp `md_secondary_container` circle with `ic_check`, `Nothing to review`, the paragraph `Questions land here after you miss them twice. Keep practising and this list will fill itself in.` capped at 30ch, and a `md_primary` pill `Quick practice`.

**Bookmarks** — back arrow + `Bookmarks`. When populated: a `md_primary` pill `Practise bookmarked`, then `Card.Bordered` rows with topic + `clip(question, 82)` and a trailing `ic_flag_filled` remove button (id `remove`). When empty: a 60dp `md_surface_container` circle with `ic_flag_outline`, `No bookmarks yet`, and `Tap the flag on any question to keep it here — useful for facts you want to reread the night before.`

A bookmark whose question id is missing from the bank must render as an empty string rather than crash — the design guards this (`s.byId[id] ? ... : ''`), so guard it too.

**Search** — a `md_surface_container` `radius_pill` bar (6dp/8dp/6dp/16dp) holding a back button, an `EditText` (id `query`) whose hint is `@string/search_hint` formatted with `bank.questions.size` (Task 2 Step 8) — rendering `Search 501 questions` with the current bank — plus `imeOptions=actionSearch`, `inputType=text`, no background, `Text.Option` sizing, and a clear button (id `clear`). Below, three mutually exclusive states driven by the trimmed lowercase query:
- length ≤ 1 → the hint paragraph `Search the whole bank by wording or by answer — useful when you half-remember a fact and want the question it belongs to.` at 14sp `md_text_muted`
- matches → `{n}{"+" if n == 30} results` in `Text.Meta.Small` (id `hit_count`), then `Card.Bordered` `radius_md` 15dp rows: topic, `clip(question, 86)`, the correct option at 13sp/600 in `md_on_secondary_container`, and a flag toggle
- no matches → `No question matches that`, `Try a single word — "Charter", "Governor", "treaty" — or browse by topic instead.`, and an `md_surface_container` pill `Browse topics`

Matching is a case-insensitive `contains` over the question text and every option, capped at 30 results, exactly as the design does. Debounce the query by 150ms and run the scan off the main thread (`flowOn(Dispatchers.Default)`) — 501 × 5 string scans is fast but not free on every keystroke.

- [ ] **Step 3: Run and commit**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.ListsAndSearchTest"
git add -A && git commit -m "feat: weak list, bookmarks and full-bank search with empty states"
```

---

## Task 13: Progress screen

**Files:**
- Create: `ui/progress/{ProgressFragment,TopicBarAdapter,WeekBarsView}.kt`
- Create: `res/layout/{fragment_progress,item_topic_bar,item_mock_row}.xml`
- Modify: `data/model/ProgressState.kt` (add `weekDate`), `domain/Stats.kt` (rotate the week window)
- Test: `app/src/test/java/com/mvnsh/citizenship/domain/WeekWindowTest.kt`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/ProgressScreenTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.progress`, `bank`; `Stats.topicStats`, `Stats.accuracy`, `Stats.achievements`, `Stats.MILESTONES`.
- Produces: `Stats.weekWindow(p: ProgressState, today: String): List<DayCount>` where `data class DayCount(val date: String, val count: Int, val label: String)` — seven entries ending today, each labelled with its day-of-week initial.

**Deviation 8 (fix a design bug):** the design stores a fixed seven-slot `week` array, always increments slot 6, and labels the slots `M T W T F S S` regardless of what day it is. So the bars never shift and the labels are wrong on six days out of seven. Fix it: store a `weekDate` anchor, roll the window forward by the number of elapsed days when the day changes, and derive labels from the actual dates.

- [ ] **Step 1: Write the failing week-window test**

```kotlin
package com.mvnsh.citizenship.domain

import com.mvnsh.citizenship.data.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekWindowTest {

    @Test fun the_window_is_seven_days_ending_today() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-18")
        val out = Stats.weekWindow(p, today = "2026-08-18")
        assertEquals(7, out.size)
        assertEquals("2026-08-12", out.first().date)
        assertEquals("2026-08-18", out.last().date)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), out.map { it.count })
    }

    @Test fun labels_come_from_the_real_dates_not_a_fixed_string() {
        // 2026-08-18 is a Tuesday, so the window Wed..Tue.
        val p = ProgressState(week = List(7) { 0 }, weekDate = "2026-08-18")
        assertEquals(listOf("W", "T", "F", "S", "S", "M", "T"), Stats.weekWindow(p, "2026-08-18").map { it.label })
    }

    @Test fun a_one_day_gap_shifts_the_window_and_zeroes_today() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-17")
        val out = Stats.weekWindow(p, today = "2026-08-18")
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 0), out.map { it.count })
        assertEquals("2026-08-18", out.last().date)
    }

    @Test fun a_gap_longer_than_a_week_clears_the_window() {
        val p = ProgressState(week = listOf(9, 9, 9, 9, 9, 9, 9), weekDate = "2026-07-01")
        assertEquals(List(7) { 0 }, Stats.weekWindow(p, today = "2026-08-18").map { it.count })
    }

    @Test fun answering_after_a_gap_credits_today_not_the_stale_slot() {
        val p = ProgressState(week = listOf(1, 2, 3, 4, 5, 6, 7), weekDate = "2026-08-16", goalDate = "2026-08-16")
        val out = Stats.registerAnswer(p, 1, correct = true, today = "2026-08-18", yesterday = "2026-08-17")
        assertEquals("2026-08-18", out.weekDate)
        assertEquals(listOf(3, 4, 5, 6, 7, 0, 1), out.week)
    }

    @Test fun an_absent_anchor_is_treated_as_today_so_old_blobs_do_not_lose_data() {
        val p = ProgressState(week = listOf(0, 0, 0, 0, 0, 0, 5), weekDate = "")
        assertEquals(5, Stats.weekWindow(p, "2026-08-18").last().count)
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then implement**

Add `val weekDate: String = ""` to `ProgressState`. Add to `Stats`:

```kotlin
data class DayCount(val date: String, val count: Int, val label: String)

/** Rolls the stored seven-day window forward to end on [today]. */
fun rollWeek(week: List<Int>, from: String, today: String): List<Int> {
    if (from.isBlank() || from == today) return week.normalisedTo7()
    val gap = (LocalDate.parse(today).toEpochDay() - LocalDate.parse(from).toEpochDay()).toInt()
    if (gap <= 0) return week.normalisedTo7()          // clock moved backwards; leave it alone
    if (gap >= 7) return List(7) { 0 }
    return (week.normalisedTo7().drop(gap) + List(gap) { 0 })
}

fun weekWindow(p: ProgressState, today: String): List<DayCount> {
    val counts = rollWeek(p.week, p.weekDate, today)
    val end = LocalDate.parse(today)
    return counts.mapIndexed { i, n ->
        val date = end.minusDays((6 - i).toLong())
        DayCount(
            date = date.toString(),
            count = n,
            label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CANADA).take(1),
        )
    }
}

private fun List<Int>.normalisedTo7(): List<Int> =
    if (size == 7) this else (this + List(7) { 0 }).take(7)
```

Then change `registerAnswer` to roll before incrementing:

```kotlin
val rolled = rollWeek(p.week, p.weekDate, today).toMutableList()
rolled[6] = rolled[6] + 1
// ... .copy(week = rolled, weekDate = today, ...)
```

- [ ] **Step 3: Write the failing progress-screen test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ProgressScreenTest : BaseUiTest() {

    @Test fun the_empty_state_invites_a_first_session() = withProgress(ProgressState(onboarded = true)) {
        onView(withId(R.id.progressFragment)).perform(click())
        onView(withText("Nothing measured yet")).check(matches(isDisplayed()))
        onView(withText("Start practising")).perform(click())
        onView(withId(R.id.q_counter)).check(matches(withText("1/10")))
    }

    @Test fun the_hero_card_reports_accuracy_and_a_readiness_verdict() = withProgress(
        ProgressState(onboarded = true, answered = 100, correct = 88, best = 14,
            seen = (1..40).associateWith { SeenStat(1, 0) })
    ) {
        onView(withId(R.id.progressFragment)).perform(click())
        onView(withId(R.id.accuracy)).check(matches(withText("88%")))
        onView(withText("Test ready")).check(matches(isDisplayed()))
        onView(withText("You are scoring above the passing mark on recent questions.")).check(matches(isDisplayed()))
        onView(withId(R.id.stat_answered)).check(matches(withText("100")))
        onView(withId(R.id.stat_remaining)).check(matches(withText("461")))
        onView(withId(R.id.stat_best)).check(matches(withText("14")))
    }

    @Test fun the_verdict_steps_down_with_accuracy() {
        mapOf(90 to "Test ready", 78 to "Nearly there", 40 to "Keep practising").forEach { (pct, verdict) ->
            withProgress(ProgressState(onboarded = true, answered = 100, correct = pct)) {
                onView(withId(R.id.progressFragment)).perform(click())
                onView(withText(verdict)).check(matches(isDisplayed()))
            }
        }
    }

    @Test fun milestones_show_earned_and_unearned() = withProgress(
        ProgressState(onboarded = true, answered = 60, correct = 50, streak = 4)
    ) {
        onView(withId(R.id.progressFragment)).perform(click())
        onView(withText("Milestones")).check(matches(isDisplayed()))
        onView(withId(R.id.miles_count)).check(matches(withText("3 of 8")))
        Stats.MILESTONES.forEach { onView(withText(it.label)).check(matches(isDisplayed())) }
    }

    @Test fun mock_attempts_are_listed_newest_first() = withProgress(
        ProgressState(onboarded = true, answered = 40, correct = 30, mocks = listOf(
            MockAttempt(70, DateUtils.shiftDay(-9)), MockAttempt(85, DateUtils.shiftDay(-1))))
    ) {
        onView(withId(R.id.progressFragment)).perform(click())
        onView(withText("Mock tests")).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.pct), hasSibling(withText("Mock test 2")))).check(matches(withText("85%")))
    }

    @Test fun the_week_chart_labels_seven_days_ending_today() = withProgress(
        ProgressState(onboarded = true, answered = 20, correct = 15,
            week = listOf(2, 4, 6, 3, 8, 1, 5), weekDate = DateUtils.today())
    ) {
        onView(withId(R.id.progressFragment)).perform(click())
        onView(withText("This week")).check(matches(isDisplayed()))
        rule.onActivity { activity ->
            val bars = activity.findViewById<ViewGroup>(R.id.week_bars)
            assertEquals(7, bars.childCount)
        }
    }
}
```

- [ ] **Step 4: Build the screen**

Title `Progress` in `Text.Headline.Screen`, 14dp gaps.

Hero card: `md_primary_container`, `radius_xl`, 20dp — `{acc}%` in `Text.Display.Accuracy`, beside it the verdict at 15sp/600 and `overall accuracy` at 12.5sp/85%, then the note at 13sp. Verdict: `Test ready` at `acc >= 85`, `Nearly there` at `>= 75`, `Keep practising` at `> 0`, `Just starting` otherwise. Note: `You are scoring above the passing mark on recent questions.` when `acc >= 75`, else `The passing mark is 15 of 20 — about 75%.`

Three `md_surface_container` tiles: `{answered}` `answered`, `{bank.size - seen.size}` `not seen`, `{max(best, streak)}` `best streak`.

Week card (`Card.Bordered`, `radius_md`, 16dp): title `This week` at 13sp/600, then a 64dp-tall row of seven bars — each `md_primary_container`, top corners 6dp, height `max(6%, count / max(1, maxCount) * 100%)`, with the day label at 10.5sp `md_text_muted` beneath. Implement as a small `WeekBarsView` (a `LinearLayout` subclass taking `List<DayCount>`) rather than a RecyclerView; seven fixed children do not need recycling.

By-topic card: title `By topic`, then all seven rows — a 104dp name column at 12sp, a 6dp bar (track `md_surface_container_high`, fill `md_secondary`) at `accuracy`, and a 34dp right-aligned `accuracyText` at 11.5sp. Note this bar shows **accuracy**, while the Topics screen's bar shows **coverage** — they are deliberately different measures.

Milestones card: title `Milestones` with `{n} of 8` in `md_primary` at 600 (id `miles_count`), then wrapping pill chips (a `ChipGroup`) — earned: `md_secondary_container` fill, `md_on_secondary_container_strong` text at 600, `ic_check`; unearned: `md_surface_container` fill, `md_text_muted` text, an outline circle. 8dp × 14dp padding, 12.5sp.

Mock-tests card: title `Mock tests`, then attempts newest-first — `Mock test {originalIndex}` at 13.5sp over `fmtDate` at 11.5sp, with a `radius_badge` percentage badge (`md_secondary_container`/`md_on_secondary_container_strong` on a pass, `md_error_container`/`md_on_error_container` on a fail). The card is hidden when there are no attempts.

Empty state (`answered == 0`): a centred 60dp `md_surface_container` circle with `ic_bar_chart`, `Nothing measured yet`, `Answer your first ten questions and this page will show accuracy, weak topics and how ready you are.`, and a `md_primary` pill `Start practising`.

- [ ] **Step 5: Run everything and commit**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.ProgressScreenTest"
git add -A && git commit -m "feat: progress screen with a correctly rolling week window

Fixes a bug carried over from the design: it stored a fixed seven-slot
array, always incremented slot 6 and labelled the slots 'M T W T F S S',
so the bars never shifted and the labels were wrong six days out of
seven. The window is now anchored to a date and rolls forward."
```

---

## Task 14: Settings, dialogs and theme switching

**Files:**
- Create: `ui/settings/SettingsFragment.kt`, `ui/settings/{ExitSessionDialog,ResetProgressDialog,ClearBookmarksDialog}.kt`, `ui/common/BaseDialogFragment.kt`
- Create: `res/layout/{fragment_settings,dialog_confirm,view_switch_row}.xml`
- Modify: `ui/AppViewModel.kt` (add `seedDemo()` / `freshInstall()`)
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/ui/SettingsTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.progress`, `setTheme`, `setGoalTarget`, `setTestDate`, `setNotif`, `resetProgress`, `clearBookmarks`, `discardSession`, `keepSessionAndExit`.
- Produces: `BaseDialogFragment` — a `DialogFragment` with an `md_surface` `radius_xl` 24dp card, 28dp horizontal insets, `Text.Headline.Dialog` title, `Text.Body.Loose` body and a vertical stack of pill buttons; `Motion.rise` on show. All three dialogs subclass it.

The design's dialogs are not `MaterialAlertDialog`s — they are 28dp cards with full-width stacked pill buttons and a serif title. Build the scaffold once.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class SettingsTest : BaseUiTest() {

    @Test fun the_theme_selector_persists_and_applies() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Dark")).perform(click())
        assertEquals("Dark", app().progressRepository.state.value.theme)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        onView(withText("Light")).perform(click())
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test fun the_goal_chips_persist_the_target() = withProgress(ProgressState(onboarded = true, goalTarget = 20)) {
        navigateTo(R.id.settingsFragment)
        onView(withText("30")).perform(click())
        assertEquals(30, app().progressRepository.state.value.goalTarget)
    }

    @Test fun the_test_date_row_toggles_between_set_and_unset() = withProgress(ProgressState(onboarded = true, testDate = null)) {
        navigateTo(R.id.settingsFragment)
        onView(allOf(withId(R.id.subtitle), hasSibling(withText("Test date")))).check(matches(withText("Not set")))
        onView(withText("Test date")).perform(click())
        onView(allOf(withId(R.id.subtitle), hasSibling(withText("Test date"))))
            .check(matches(withText(containsString("38 days"))))
    }

    @Test fun each_reminder_switch_persists_independently() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Streak at risk")).perform(click())
        onView(withText("Test date countdown")).perform(click())
        val n = app().progressRepository.state.value.notif
        assertTrue(n.daily); assertTrue(n.streak); assertTrue(n.test)
        onView(withText("Daily study reminder")).perform(click())
        assertFalse(app().progressRepository.state.value.notif.daily)
    }

    @Test fun reset_clears_progress_but_keeps_bookmarks() = withProgress(
        ProgressState(onboarded = true, answered = 340, correct = 279, streak = 12,
            bookmarks = listOf(2, 7), mocks = listOf(MockAttempt(85, DateUtils.today())))
    ) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Reset all progress")).perform(click())
        onView(withText("Reset all progress?")).check(matches(isDisplayed()))
        onView(withText(containsString("Bookmarks are kept"))).check(matches(isDisplayed()))
        onView(withText("Reset everything")).perform(click())
        val p = app().progressRepository.state.value
        assertEquals(0, p.answered); assertEquals(0, p.streak); assertTrue(p.mocks.isEmpty())
        assertEquals("bookmarks survive", listOf(2, 7), p.bookmarks)
        assertTrue("stays past onboarding", p.onboarded)
        onView(withText("Progress reset")).check(matches(isDisplayed()))
    }

    @Test fun cancelling_reset_changes_nothing() = withProgress(ProgressState(onboarded = true, answered = 340)) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Reset all progress")).perform(click())
        onView(withText("Cancel")).perform(click())
        assertEquals(340, app().progressRepository.state.value.answered)
    }

    @Test fun clearing_bookmarks_leaves_progress_alone() = withProgress(
        ProgressState(onboarded = true, answered = 50, bookmarks = listOf(1, 2, 3))
    ) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Clear bookmarks")).perform(click())
        onView(withText("Clear them")).perform(click())
        assertTrue(app().progressRepository.state.value.bookmarks.isEmpty())
        assertEquals(50, app().progressRepository.state.value.answered)
    }

    @Test fun discarding_from_the_exit_dialog_drops_the_session() = withProgress(
        ProgressState(onboarded = true, session = SessionState(mode = "ALL", label = "All questions",
            ids = listOf(1, 2, 3), startedAtEpochMs = System.currentTimeMillis()))
    ) {
        onView(withId(R.id.exit)).perform(click())
        onView(withText("Discard the session")).perform(click())
        assertNull(app().progressRepository.state.value.session)
        onView(withText("Start studying")).check(matches(isDisplayed()))
    }

    @Test fun the_about_block_states_the_offline_and_affiliation_facts() = withProgress(ProgressState(onboarded = true)) {
        navigateTo(R.id.settingsFragment)
        onView(withText("Question bank downloaded")).check(matches(isDisplayed()))
        onView(withText("501 questions · works offline")).check(matches(isDisplayed()))
        onView(withText("Everything stays on device")).check(matches(isDisplayed()))
        onView(withText("English · Français coming")).check(matches(isDisplayed()))
        onView(withText(containsString("Not affiliated with the Government of Canada"))).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then build the screen**

Back arrow + `Settings`, 18dp between groups, each group an overline plus its content.

**Appearance** — a segmented control: `md_surface_container` `radius_pill` track with 4dp padding, three equal pill segments. Selected: `md_surface_container_lowest` fill, `md_on_surface` text at 600, a soft 1dp/3dp shadow. Unselected: transparent, `md_on_surface_variant`. Labels `System` / `Light` / `Dark`. Selecting persists via `setTheme` and `MainActivity`'s existing collector applies the night mode. **Do not** show a "ships in the next build" snackbar (deviation 7).

**Study** — a `Card.Bordered` `radius_md` 16dp row: `Daily goal` at 15sp/600 over `Questions a day` at 12.5sp, with three chips `10` / `20` / `30` on the right (selected `md_primary`/`md_on_primary`/600, else `md_surface_container`/`md_on_surface_variant`, 9dp × 15dp, 13.5sp). A second row `Test date` with subtitle `Not set` or `{fmtDate} · {daysTo} days` (id `subtitle`), tapping toggles between `null` and `shiftDay(38)`, trailing chevron.

The design's toggle is a stand-in for a date picker. Keep the toggle behaviour to match, since a real `MaterialDatePicker` is a different interaction the design does not specify.

**Reminders** — three switch rows (`Card.Bordered` `radius_md` 16dp): label at 15sp/600 over sub at 12.5sp/1.4, with a hand-built 52dp × 32dp track on the right — on: `md_primary` fill, thumb 26dp white, right-aligned; off: `md_surface_container_high` fill with a 1.5dp `md_outline_variant` inset stroke, thumb 22dp `md_outline`, left-aligned. 160ms fill transition. Rows:

| Label | Subtitle |
|---|---|
| `Daily study reminder` | `One nudge at 8pm if you have not studied` |
| `Streak at risk` | `Only when a streak of 3+ is about to break` |
| `Test date countdown` | `Two weeks, one week and the day before` |

Give each row `contentDescription` and a `Switch`-like accessibility role so TalkBack announces state — a hand-drawn track is invisible to it otherwise. Use `ViewCompat.setAccessibilityDelegate` to report `AccessibilityNodeInfo.isCheckable/isChecked`.

**Data** — a grouped list: an info row with `ic_download_done`, `Question bank downloaded` / `501 questions · works offline`; a button row `Clear bookmarks`; a button row `Reset all progress` in `md_error` at 600. In debug builds only, append two more rows, `Load demo data` and `Reset to fresh install`, calling `seedDemo()` / `freshInstall()`.

**About** — a grouped list of four rows: `Language` / `English · Français coming`; `Privacy` / `Everything stays on device`; `Send feedback` with `ic_open_in_new`; `Version` / `1.0 (prototype)`. Then the disclaimer at 12sp `md_text_muted`: `Not affiliated with the Government of Canada. Questions follow the official study guide, Discover Canada.`

Wire `Send feedback` to an `ACTION_SENDTO` `mailto:` intent guarded by `resolveActivity` — the design shows the affordance but specifies no destination, so a guarded mail intent is the honest minimum. If nothing resolves, show a snackbar rather than crashing.

**Dialogs** — all three from `BaseDialogFragment`:

| Dialog | Title | Body | Buttons |
|---|---|---|---|
| Exit session | `Leave this session?` | `Your place is kept — you can pick it up from home. Nothing you've answered is lost.` | `Save and exit` (`md_primary`), `Keep studying` (`md_surface_container`), `Discard the session` (text, `md_error`) |
| Reset progress | `Reset all progress?` | `Accuracy, streak, weak list and mock results are deleted. Bookmarks are kept. This can't be undone.` | `Reset everything` (`md_error`/`md_on_error`), `Cancel` (`md_surface_container`) |
| Clear bookmarks | `Clear bookmarks?` | `Your saved questions are removed. Progress and weak list are untouched.` | `Clear them` (`md_primary`), `Cancel` (`md_surface_container`) |

`seedDemo()` reproduces the design's `demoData`: mark the first 340 bank ids as seen with `s = 1 + (if (i % 3 == 0) 1 else 0)` and `m = if (i % 23 == 0) 2 else if (i % 6 == 0) 1 else 0`, `answered = 340`, `correct = 279`, three mocks at 70/80/85 on `shiftDay(-9)`/`(-4)`/`(-1)`, bookmarks `[2, 7, 10, 44, 120]`, `goalTarget = 20`, `goalDone = 14`, `streak = 12`, `best = 14`, `lastDay = shiftDay(-1)`, `testDate = shiftDay(38)`, `week = [12,16,20,18,22,14,14]`, `weekDate = today()`, `onboarded = true`. `freshInstall()` replaces with `ProgressState()`.

- [ ] **Step 3: Run and commit**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.ui.SettingsTest"
git add -A && git commit -m "feat: settings, custom dialogs and working theme switching"
```

---

## Task 15: Reminder notifications

**Files:**
- Create: `notify/{Channels,ReminderDecision,ReminderWorker,ReminderScheduler}.kt`
- Modify: `CitizenshipApp.kt` (create the channel), `ui/settings/SettingsFragment.kt` (permission request), `MainActivity.kt` (reschedule on preference change)
- Test: `app/src/test/java/com/mvnsh/citizenship/notify/ReminderDecisionTest.kt`
- Test: `app/src/androidTest/java/com/mvnsh/citizenship/notify/ReminderWorkerTest.kt`

**Interfaces:**
- Consumes: `ProgressState`, `DateUtils`.
- Produces:
  - `object Channels` with `const val REMINDERS = "study_reminders"` and `fun ensure(context: Context)`
  - `object ReminderDecision`: `data class Reminder(val id: Int, val title: String, val body: String)`, `fun due(p: ProgressState, today: String, yesterday: String): List<Reminder>`
  - `class ReminderWorker(context, params) : CoroutineWorker`
  - `object ReminderScheduler` with `fun schedule(context: Context)` and `fun cancel(context: Context)`

One periodic worker fires daily at 20:00 and evaluates all three reminders, because they share a trigger time and a data source. Splitting them into three workers would triple the scheduling for no behavioural gain.

- [ ] **Step 1: Write the failing decision test**

The decision is pure, so all the branching is JVM-testable without WorkManager.

```kotlin
package com.mvnsh.citizenship.notify

import com.mvnsh.citizenship.data.model.NotifPrefs
import com.mvnsh.citizenship.data.model.ProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDecisionTest {

    private val today = "2026-08-18"
    private val yesterday = "2026-08-17"
    private fun due(p: ProgressState) = ReminderDecision.due(p, today, yesterday).map { it.id }

    @Test fun nothing_fires_when_every_toggle_is_off() {
        val p = ProgressState(notif = NotifPrefs(daily = false, streak = false, test = false),
            streak = 9, lastDay = yesterday, testDate = "2026-08-25")
        assertTrue(due(p).isEmpty())
    }

    @Test fun the_daily_nudge_fires_only_when_the_goal_is_unmet_today() {
        val base = ProgressState(notif = NotifPrefs(daily = true), goalTarget = 20)
        assertEquals(listOf(ReminderDecision.ID_DAILY), due(base.copy(goalDone = 0, goalDate = today)))
        assertEquals(listOf(ReminderDecision.ID_DAILY), due(base.copy(goalDone = 19, goalDate = today)))
        assertTrue("a met goal is silent", due(base.copy(goalDone = 20, goalDate = today)).isEmpty())
    }

    @Test fun a_stale_goal_date_counts_as_nothing_done_today() {
        val p = ProgressState(notif = NotifPrefs(daily = true), goalTarget = 20, goalDone = 20, goalDate = yesterday)
        assertEquals(listOf(ReminderDecision.ID_DAILY), due(p))
    }

    @Test fun the_streak_warning_needs_a_streak_of_three_and_no_activity_today() {
        val base = ProgressState(notif = NotifPrefs(daily = false, streak = true), lastDay = yesterday)
        assertTrue("a streak of 2 is not worth interrupting for", due(base.copy(streak = 2)).isEmpty())
        assertEquals(listOf(ReminderDecision.ID_STREAK), due(base.copy(streak = 3)))
        assertTrue("already studied today", due(base.copy(streak = 9, lastDay = today)).isEmpty())
        assertTrue("streak is already broken", due(base.copy(streak = 9, lastDay = "2026-08-01")).isEmpty())
    }

    @Test fun the_streak_warning_names_the_streak_length() {
        val p = ProgressState(notif = NotifPrefs(daily = false, streak = true), streak = 12, lastDay = yesterday)
        assertTrue(ReminderDecision.due(p, today, yesterday).single().body.contains("12-day"))
    }

    @Test fun the_countdown_fires_only_at_fourteen_seven_and_one_days_out() {
        fun atDays(n: Int) = ProgressState(
            notif = NotifPrefs(daily = false, test = true),
            testDate = java.time.LocalDate.parse(today).plusDays(n.toLong()).toString(),
        )
        listOf(14, 7, 1).forEach { assertEquals("day $it", listOf(ReminderDecision.ID_TEST), due(atDays(it))) }
        listOf(0, 2, 6, 8, 13, 15, 30).forEach { assertTrue("day $it must be silent", due(atDays(it)).isEmpty()) }
    }

    @Test fun no_test_date_means_no_countdown() {
        assertTrue(due(ProgressState(notif = NotifPrefs(daily = false, test = true), testDate = null)).isEmpty())
    }

    @Test fun all_three_can_fire_on_the_same_evening() {
        val p = ProgressState(
            notif = NotifPrefs(daily = true, streak = true, test = true),
            goalTarget = 20, goalDone = 0, goalDate = today,
            streak = 5, lastDay = yesterday,
            testDate = java.time.LocalDate.parse(today).plusDays(7).toString(),
        )
        assertEquals(
            listOf(ReminderDecision.ID_DAILY, ReminderDecision.ID_STREAK, ReminderDecision.ID_TEST),
            due(p),
        )
    }
}
```

- [ ] **Step 2: Run to confirm it fails, then write `ReminderDecision`**

```kotlin
// notify/ReminderDecision.kt
package com.mvnsh.citizenship.notify

import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.domain.DateUtils

object ReminderDecision {

    const val ID_DAILY = 1001
    const val ID_STREAK = 1002
    const val ID_TEST = 1003

    /** Days before the test that the countdown speaks up, matching the settings copy. */
    private val COUNTDOWN_DAYS = setOf(14, 7, 1)

    data class Reminder(val id: Int, val title: String, val body: String)

    fun due(p: ProgressState, today: String, yesterday: String): List<Reminder> = buildList {
        val doneToday = if (p.goalDate == today) p.goalDone else 0

        if (p.notif.daily && doneToday < p.goalTarget) {
            add(Reminder(ID_DAILY, "Time for today's questions",
                "${p.goalTarget - doneToday} to go to hit today's goal."))
        }
        // Only warn while the streak is still alive and still worth keeping.
        if (p.notif.streak && p.streak >= 3 && p.lastDay == yesterday) {
            add(Reminder(ID_STREAK, "Your streak is at risk",
                "A few questions tonight keeps your ${p.streak}-day streak going."))
        }
        val testDate = p.testDate
        if (p.notif.test && testDate != null) {
            val days = (LocalDate.parse(testDate).toEpochDay() - LocalDate.parse(today).toEpochDay()).toInt()
            if (days in COUNTDOWN_DAYS) {
                add(Reminder(ID_TEST, "Your test is in $days ${if (days == 1) "day" else "days"}",
                    "A mock test now is the fastest way to see where you stand."))
            }
        }
    }
}
```

- [ ] **Step 3: Write the channel, worker and scheduler**

```kotlin
// notify/Channels.kt
object Channels {
    const val REMINDERS = "study_reminders"

    fun ensure(context: Context) {
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.channel_reminders),      // "Study reminders"
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
```

`NotificationChannel` is API 26+, which is our minSdk, so no version guard is needed. Call `Channels.ensure(this)` from `CitizenshipApp.onCreate`.

```kotlin
// notify/ReminderWorker.kt
class ReminderWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as CitizenshipApp
        // The worker can run before the repository has read from disk, so wait for the flag.
        app.progressRepository.loaded.first { it }
        val due = ReminderDecision.due(
            app.progressRepository.state.value, DateUtils.today(), DateUtils.shiftDay(-1),
        )

        if (due.isNotEmpty() && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val manager = NotificationManagerCompat.from(context)
            due.forEach { r ->
                manager.notify(r.id, NotificationCompat.Builder(context, Channels.REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(r.title)
                    .setContentText(r.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(r.body))
                    .setContentIntent(PendingIntent.getActivity(
                        context, r.id, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .setAutoCancel(true)
                    .build())
            }
        }
        return Result.success()
    }
}
```

```kotlin
// notify/ReminderScheduler.kt
object ReminderScheduler {
    private const val WORK_NAME = "daily-reminder"
    private const val HOUR = 20   // "One nudge at 8pm", per the settings copy

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNext(HOUR), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Milliseconds from now until the next occurrence of [hour] local time. */
    fun millisUntilNext(hour: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
```

WorkManager persists periodic work across reboots, so no `BOOT_COMPLETED` receiver is needed.

In `MainActivity`, collect `vm.progress.map { it.notif }.distinctUntilChanged()` and call `ReminderScheduler.schedule(this)` when any toggle is on, `cancel(this)` when all three are off.

- [ ] **Step 4: Request the runtime permission where the user opts in**

On API 33+, `POST_NOTIFICATIONS` must be granted. Ask at the moment a toggle is switched on — that is where the user has expressed intent — using `registerForActivityResult(ActivityResultContracts.RequestPermission())` in `SettingsFragment`. If the user denies, leave the toggle on (the preference is still theirs) but show a snackbar: `Turn on notifications in system settings to get reminders.` Do not ask on launch.

Add a permission check test:

```kotlin
@Test fun a_toggle_still_persists_when_the_permission_is_denied() = withProgress(ProgressState(onboarded = true)) {
    // The instrumentation grants POST_NOTIFICATIONS via the rule below, so this asserts the
    // preference path is independent of the permission path.
    navigateTo(R.id.settingsFragment)
    onView(withText("Streak at risk")).perform(click())
    assertTrue(app().progressRepository.state.value.notif.streak)
}
```

- [ ] **Step 5: Write the instrumented worker test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {

    @get:Rule val permission: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")

    @Test fun the_worker_succeeds_and_posts_the_due_reminders() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CitizenshipApp>()
        context.progressRepository.replace(ProgressState(
            onboarded = true, goalTarget = 20, goalDone = 0, goalDate = DateUtils.today(),
            streak = 5, lastDay = DateUtils.shiftDay(-1),
        ))
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        assertTrue(worker.doWork() is ListenableWorker.Result.Success)

        val posted = context.getSystemService(NotificationManager::class.java).activeNotifications
        assertEquals(setOf(ReminderDecision.ID_DAILY, ReminderDecision.ID_STREAK),
            posted.map { it.id }.toSet())
    }

    @Test fun nothing_is_posted_when_every_toggle_is_off() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CitizenshipApp>()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        context.progressRepository.replace(ProgressState(
            onboarded = true, notif = NotifPrefs(daily = false, streak = false, test = false)))
        TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork()
        assertEquals(0, context.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }

    @Test fun the_next_eight_pm_is_always_in_the_future() {
        val morning = ZonedDateTime.parse("2026-08-18T09:00:00-04:00[America/Toronto]")
        assertEquals(11 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNext(20, morning))
        val night = ZonedDateTime.parse("2026-08-18T22:00:00-04:00[America/Toronto]")
        assertEquals(22 * 60 * 60 * 1000L, ReminderScheduler.millisUntilNext(20, night))
    }
}
```

Add `androidTestImplementation("androidx.work:work-testing:2.10.0")` to make `TestListenableWorkerBuilder` available.

- [ ] **Step 6: Run and commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mvnsh.citizenship.notify.ReminderDecisionTest"
./gradlew :app:connectedDebugAndroidTest --tests "com.mvnsh.citizenship.notify.ReminderWorkerTest"
git add -A && git commit -m "feat: daily reminder notifications for goal, streak and test date

One WorkManager worker at 20:00 evaluates all three reminders, since
they share a trigger and a data source. The decision itself is a pure
function so every branch is JVM-tested; POST_NOTIFICATIONS is requested
at the moment the user turns a toggle on, never at launch."
```

---

## Task 16: Loading and error states, accessibility, final verification

**Files:**
- Modify: every fragment (loading and error gating), `res/layout/{view_loading,view_error}.xml`
- Modify: `ui/common/Snack.kt`, `res/values/strings.xml`
- Create: `app/src/androidTest/java/com/mvnsh/citizenship/ui/ShellStatesTest.kt`
- Create: `app/src/androidTest/java/com/mvnsh/citizenship/ui/AccessibilityTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.bank`, `loadFailed`, `retryLoad`.
- Produces: `view_loading.xml` and `view_error.xml` as `<include>`-able blocks; every screen that needs the bank gates on it.

- [ ] **Step 1: Build the two shell states**

The design has both, and they are the only states a user sees if the assets fail to parse.

**Loading skeleton** — 20dp × 16dp padding, 14dp gaps, `md_surface_container`-tinted placeholder blocks: a 26dp bar at 62% width (`radius_badge`), a 120dp `radius_xl` block, an 88dp `radius_xl` block, then two side-by-side 104dp `radius_md` blocks; below, centred at 13sp `md_text_muted`: `Loading your question bank…`

**Error state** (id `error_state`) — centred, 32dp padding, 16dp gaps: a 64dp `md_surface_container` circle with `ic_wifi_off` tinted `md_primary`; the heading `Questions could not load` in `Text.Headline.Dialog`; the body at 14.5sp/1.55 in `md_on_surface_variant`, capped at 34ch; then an `md_primary` pill (15dp × 30dp, 15.5sp/600) reading `Try again` with id `retry`, calling `retryLoad()`.

The heading and button label are the design's. The body is **not** — the design says "The bank is stored on your device after the first download — reconnect once and it stays available offline", which describes a network fetch this app does not have (deviation 9). Use instead:

> `Your saved progress is safe. The question bank ships inside the app, so this is unusual — try again, and reinstalling will restore it.`

Gate every bank-dependent screen: while `bank == null && !loadFailed`, show the skeleton; when `loadFailed`, show the error state; otherwise the content. Home, Practice, Topics and Progress all need this. Quiz and Mock cannot be reached without a bank, so they can assert it.

- [ ] **Step 2: Write the shell-state test**

```kotlin
@RunWith(AndroidJUnit4::class)
class ShellStatesTest : BaseUiTest() {

    @Test fun a_parse_failure_shows_the_error_state_and_retry_recovers() = withProgress(ProgressState(onboarded = true)) {
        rule.onActivity { activity ->
            ViewModelProvider(activity)[AppViewModel::class.java].forceLoadFailureForTest()
        }
        onView(withId(R.id.error_state)).check(matches(isDisplayed()))
        onView(withId(R.id.retry)).perform(click())
        onView(withText("Today's goal")).check(matches(isDisplayed()))
    }
}
```

Add `@VisibleForTesting fun forceLoadFailureForTest()` to `AppViewModel`, setting `_bank` to null and `_loadFailed` to true. An injectable fake `BankRepository` would be cleaner, but the app has no DI framework and adding one for a single test is not worth it — the annotation makes the intent explicit.

- [ ] **Step 3: Write the accessibility test and fix what it finds**

```kotlin
@RunWith(AndroidJUnit4::class)
class AccessibilityTest : BaseUiTest() {

    @Before fun enableChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    @Test fun the_four_top_level_screens_pass_the_accessibility_checks() = withProgress(
        ProgressState(onboarded = true, answered = 100, correct = 85, streak = 4,
            bookmarks = listOf(1, 2), seen = mapOf(1 to SeenStat(3, 2)),
            testDate = DateUtils.shiftDay(20), goalDate = DateUtils.today(), goalDone = 5)
    ) {
        listOf(R.id.homeFragment, R.id.practiceFragment, R.id.topicsFragment, R.id.progressFragment)
            .forEach { onView(withId(it)).perform(click()) }
    }

    @Test fun the_quiz_passes_with_an_answer_revealed() = withProgress(
        ProgressState(onboarded = true, session = SessionState(mode = "ALL", label = "All questions",
            ids = listOf(1, 2), startedAtEpochMs = System.currentTimeMillis()))
    ) {
        onView(withText("Individuals")).perform(click())
    }
}
```

`AccessibilityChecks` comes from `espresso-accessibility` — add `androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.6.1")`.

The checks will flag the design's tightest spots. Fix each rather than suppressing:
- **Touch targets under 48dp.** The quiz flag button, topic-row flag buttons and the search clear button are drawn at 19–22dp. Keep the visual size and expand the touch area with `TouchDelegate` (or 48dp padded containers with a smaller drawable).
- **Missing labels on icon-only buttons.** Every icon button needs a `contentDescription`: `Search questions`, `Settings`, `Go back`, `Leave session`, `Bookmark this question` / `Remove bookmark` (state-dependent), `Jump to question`, `Clear search`.
- **Decorative icons.** Icons paired with visible text (`ic_flame` next to "12 days", status dots, tile icons) must be `importantForAccessibility="no"` so TalkBack does not read them twice.
- **Contrast.** Verify `md_text_muted` on `md_surface_container` in both themes. `#6F5D59` on `#F5E9E5` is roughly 4.9:1 and passes for the 11.5sp size it is used at; the dark pair `#BCA6A1` on `#271D1B` is roughly 7.4:1. If any check fails, darken the light-theme muted token rather than enlarging text — the design's hierarchy depends on that size.
- **Progress bars and hand-drawn switches.** Give the goal and quiz bars `AccessibilityNodeInfo` range info (or a `contentDescription` like `Goal progress, 14 of 20 questions`), and the switch rows the checkable/checked state from Task 14.

Also confirm text scaling: run the four top-level screens at `Largest` font size and confirm no clipped text. The stat numerals and the 27sp screen titles are the risk; let them wrap rather than fixing heights.

- [ ] **Step 4: Verify the release build and shrinking**

```bash
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

Expected: release build succeeds. If `ProgressState.decode` returns defaults in a release build where it worked in debug, R8 stripped the serializers — check `proguard-rules.pro` from Task 1. Confirm by installing the release APK and checking that progress survives a restart.

Fix every lint error and any warning that indicates a real defect. Do not add blanket `lint.abortOnError = false`.

- [ ] **Step 5: Full verification run**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Every suite must be green before this task is claimed complete. Report the actual counts, not "tests pass".

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: loading and error states, accessibility fixes, release build

Expanded sub-48dp touch targets with TouchDelegate rather than resizing
the design's icons, labelled every icon-only button, marked decorative
icons as non-important, and gave the hand-drawn switches and progress
bars real accessibility state."
```

---

## Verification

End-to-end, on a device or emulator running API 26 and API 36 (the min and target):

1. **Fresh install → first session.** Clear app data. Confirm onboarding appears, pick "In about a month" and "20 a day", finish. Home shows `Ready when you are` and `Start studying`. Tap it, answer a question correctly, confirm the "Why" panel appears for question 1 and the goal bar advances.
2. **Weak-list round trip.** Answer the same question wrong twice (reach it via Practice → All questions). Confirm it appears under Home → Weak questions with `Missed 2×`, tap `Practise these now`, then `I know this now` and confirm it leaves the list while the attempt count is retained.
3. **Mock test, backgrounded.** Start a mock, note the timer, press Home for two minutes, return. The timer must have advanced by two minutes, not paused. Answer everything via the navigator sheet, submit, confirm the pass/fail banner matches 15-of-20, and that the attempt appears on both Progress and the mock intro.
4. **Process death.** Start a practice session, answer three questions, then kill the app from the system task switcher (or `adb shell am force-stop com.mvnsh.citizenship`). Relaunch: Home must offer `Resume session` at question 4, and the three answers must still be counted.
5. **Theme.** Settings → Dark. Every screen must be legible with no light-theme leakage: check the quiz's four option states, the mock timer in its low state (start a mock and wait, or temporarily lower `MOCK_SECONDS`), all three dialogs, the snackbar, and the bottom nav's selected pill. Then System, and toggle the device theme.
6. **Reminders.** Enable all three toggles, granting the permission. Then force the worker rather than waiting for 20:00: `adb shell cmd jobscheduler run -f com.mvnsh.citizenship <jobId>`, or temporarily change `PeriodicWorkRequestBuilder` to a 15-minute period. With `goalDone = 0` and a 5-day streak set via the debug demo-data row, confirm two notifications post and that tapping one opens the app.
7. **Offline.** Enable airplane mode and use every screen. Nothing may fail — the app declares no `INTERNET` permission, so this should be structurally guaranteed; the test confirms no asset is being fetched by accident.
8. **Search.** Search `Charter`, confirm hits show the correct answer line; search `the`, confirm `30+ results`; search `zzzz`, confirm the empty state and that `Browse topics` navigates.
9. **Accessibility sweep.** Turn on TalkBack and traverse Home and the quiz. Every control must announce a purpose, the bookmark button must announce its state, and no icon may be read twice. Then set font size to Largest and confirm nothing clips.
10. **Release build.** Install the release APK, answer questions, force-stop, relaunch, and confirm progress persisted — this is what catches R8 stripping the serializers.

Automated gates, all of which must be green:

```bash
./gradlew :app:testDebugUnitTest        # domain, serialization, bank, option styling, reminders
./gradlew :app:connectedDebugAndroidTest # every screen, navigation, worker, accessibility
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

