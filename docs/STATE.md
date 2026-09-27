# STATE

_Last updated: 2026-09-27 (evening), after the photo meal logging tickets
#2-#10 were built. The earlier 2026-09-27 rewrite came from a survey of the
code; this one adds what landed since and what is still waiting in open PRs._

## Where we are

**Phases 1-4 complete. Photo meal logging (tickets #2-#10) is fully built,
but only #2 and #3 are on `main`; the rest waits in a stack of open PRs that
has not yet been compiled by Gradle or run on a device.**

- First green build 2026-08-21; installed and checked on a Redmi Note 12
  (Android 15) on 2026-08-22 - dashboard, navigation, Settings, light/dark.
- Room is still at schema version 1; `app/schemas/.../1.json` is committed.
  Nothing in #2-#10 changed the schema - only queries.
- Two instrumented suites in `app/src/androidTest` (`MealDaoTest`,
  `ProgressiveOverloadTest`); no recorded device run yet. `MealDaoTest` has
  grown distinct-Meal, photo-time and photo-pruning cases.

### What has been verified, and how

The PRs were written in a cloud container with no Android SDK, so none of them
has been through `:app:assembleDebug` yet. What was checked there instead:

- A standalone JVM Gradle project compiling the real pure-Kotlin files
  (models, ViewModel, `MealDao`, remote client, `domain/plan`) against stubs
  for Room annotations and Android types. At the top of the stack, 72 tests
  pass across seven suites, five of them new: `SnapMealModelTest` (28),
  `SnapFailuresTest` (12), `PhotoTimesTest` (6), `EstimateRequestsTest` (5),
  `PhotoRetentionTest` (4), plus `ResponseParsingTest` and
  `MacroConsistencyTest`.
  The other four suites (`AdaptiveTdeeTest`, `PlanEngineTest`,
  `CutSimulationTest`, `StreakAndXpTest`) were not part of that run; nothing
  in #2-#10 touches `domain/`.
- New SQL run in SQLite against a table built from the exported schema.
- **Not verified anywhere yet:** Compose screens, navigation, Hilt wiring,
  Room's compile-time query check, and every instrumented test.

## What exists

**Theme and components** - `core/ui/theme/` (Tones, Color, Type, Fonts with
bundled DM Sans, Shape, Spacing, Motion, Insets, Theme) and
`core/ui/components/` (the `Fm*` set: app bars, navigation bar + rail,
expandable FAB, list items, states, tactile button, surfaces, selection,
meters, gamification, portion picker, quick-add + the extracted `FmStepper`,
log bar, trend chart, animation). The neo-brutalist `Brut*` components are
gone; see DECISIONS 2026-08-22.

**Data** - Room entities, DAOs (`MealDao`, `RecipeDao`, `WorkoutDao`,
`SessionDao`, `PlanDao`), `ExerciseSeed`, DataStore `SettingsStore`,
`ImagePreprocessor`. Repositories: `MealRepositoryImpl`, `PlanRepositoryImpl`
(joins weigh-ins and meal totals into `AdaptiveTdee`),
`ProgressRepositoryImpl` (streak + XP). Day counts are distinct Meals, not
Dishes (#2, on `main`).

**AI** - `CulinaryLlmClient` with `generateRecipe` (wired to the Chef),
`estimateFromPhoto` and `estimateFromText` (both used by Snap a meal in the
open stack), structured output via `RecipeSchemas` - photo Portions are a
quantity plus an Indian unit (#3, on `main`) - Atwater advisories, and
`LlmResult` failure cases that each get their own UI (#9).

**Domain** - `domain/plan/` (body fat, weight trend, adaptive TDEE, macro
solver, plan engine) and `domain/progress/` (`StreakEngine`, `XpEngine`), all
pure Kotlin.

**Screens** - Dashboard (meals, plan target, streak, sessions), Chef tab +
AI Chef chat (generates recipes, "Cook & Eat" and "log again"), Train +
Active Session (routines, sets, rest timer), Plan (weigh-ins, trend chart,
projection), Settings. In the open stack: **Snap a meal**
(`presentation/snap/`): camera or gallery, estimate, review sheet, log; see
below.

### Snap a meal (open PRs, top of stack = #19)

`SnapMealModel.kt` holds the rules as plain Kotlin; `SnapMealViewModel` only
moves between states and does IO; `SnapMealScreen` draws them.

- **Capture** - system camera app via `TakePicture`, or the system photo
  picker. Photos live in app-private `filesDir/meal-photos` behind a
  FileProvider and are replaced by the downscaled copy actually sent.
- **When** - a gallery photo is logged at its EXIF time (camera offset wins
  over the phone's zone, never in the future); the meal type defaults from
  that time and is required. Time is editable on the sheet. Picking the same
  photo twice warns, never blocks.
- **Review** - always shown, advisories first. Each Dish can be stepped in
  its own unit (macros scale from the model's original), renamed, removed
  with undo; a missed Dish can be typed and is estimated with the same
  prompt rules. The total is the sum of kept Dishes. Stepped Dishes stay
  Estimates.
- **Failures** - each `LlmResult.Failure` maps to a message and actions:
  key problems open Settings, transient ones retry the same photo, refusals
  and unreadable photos ask for another. **Log by hand** is always offered:
  a one-dish form that logs a `MANUAL`, non-estimate row at the photo's time
  with the photo kept.
- **Pruning** - at app start, off the main thread, photos whose every Dish
  is older than 90 days are deleted and their paths cleared; Dishes stay.

## Open PRs and merge order

Each stacked PR targets the branch below it; after one merges, retarget the
next to `main` (GitHub may do this automatically when the base branch is
deleted on merge).

| PR | Closes | Base |
| --- | --- | --- |
| #1 skills, glossary, decisions, this file | - | `main` |
| #13 Plan tab scale icon | #4 | `main` |
| #14 Snap a meal | #5 | `main` |
| #15 Edit Dishes on the review sheet | #6 | #14 |
| #16 Add a missed Dish by typing it | #7 | #15 |
| #17 Gallery, EXIF time, duplicate warning | #8 | #16 |
| #18 Failure paths + log by hand | #9 | #17 |
| #19 Prune photos after 90 days | #10 | #18 |

Before merging the stack, build it once locally from the top (#19's branch
contains all of it):

```
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kevinjones.fitmasala.data.MealDaoTest
```

Then on the phone: snap a meal, pick a gallery photo from yesterday, remove
the API key and retry, and airplane mode -> Log by hand.

**The `security` check is red on every PR** because the workflow reads the
`CLAUDE_API_KEY` Actions secret, which does not exist. Add it under Settings
-> Secrets and variables -> Actions, then re-run; nothing in the code fixes it.

## Known issues

- **Two FAB actions are no-ops** - "Ask the chef" and "Start a workout" have
  empty handlers in `FitMasalaApp.kt`. ("Snap a meal" is wired in #14.)
- **Log by hand only exists inside Snap a meal** - there is no standalone
  manual-entry screen; `MealRepository.logManual` is called only from the
  failure path (#18).
- **Probably dead code** - `data/remote/AiService.kt` + `AiModels.kt` +
  `SystemPrompts.kt` + `data/repository/AiRepository.kt` form a second LLM path
  that only `NetworkModule` references; `PlaceholderScreen` is no longer used.
  Confirm and delete in a separate change.
- **Fixed on `main`:** a valid day counted Dishes, not Meals (#2).
- **Fixed in open PRs:** the Plan tab's camera icon (#13).

## Next

1. Build and device-test the stack (above), then merge #1, #13 and #14-#19
   in order.
2. Wire the two remaining FAB actions.
3. Phase 6: what remains of the plan manager (AGENTS.md lists its UI and
   vision call as pending).

## Device automation - do not repeat

Driving the phone with `adb shell input tap` tripped MIUI's App Lock, which
took over the foreground and swallowed the taps mid-sequence. Screenshots via
`adb exec-out screencap -p` are safe and useful; blind tap injection on this
device is not. Note also that Git Bash rewrites `/sdcard/...` into a Windows
path, so `adb shell screencap` + `adb pull` fails - use `exec-out`.

## Toolchain (resolved 2026-08-21)

Three separate problems, all fixed:

1. **No wrapper.** `gradlew`, `gradlew.bat` and `gradle-wrapper.jar` were
   recovered from the Gradle source distribution already unpacked under
   `~/.gradle/caches/8.9/transforms/` - no download needed.
2. **JDK.** Studio Quail bundles JBR 25, which Gradle 8.x cannot parse (every
   task died with a bare `* What went wrong:` / `25.0.2` - a JavaVersion parse
   failure, not a project error). JBR 21.0.11 was downloaded through Studio and
   is pinned in `gradle.properties` via `org.gradle.java.home`.
3. **Gradle version.** Studio's upgrade assistant bumped AGP to 8.13.2, which
   requires Gradle 8.13. `distributionUrl` updated; 8.13 was already cached.

**JAVA_HOME must still be set in the shell.** `org.gradle.java.home` is read
after the JVM starts, so it cannot bootstrap the launcher - `gradlew.bat` fails
with "JAVA_HOME is not set" without it. Set it once:

```
setx JAVA_HOME "C:\Users\kj638\.jdks\jbr-21.0.11"
```

### Versions as actually built

AGP 8.13.2, Gradle 8.13, Kotlin 2.0.21, KSP 2.0.21-1.0.28, JDK 21.
Studio raised AGP from the originally pinned 8.7.2; the rest is unchanged, and
the combination is now proven rather than assumed.

## Verifying a change

`docs/VERIFY.md`. `:app:testDebugUnitTest` first - it needs no device and
covers the plan engine, streaks and response parsing.
