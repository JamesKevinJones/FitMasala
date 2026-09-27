# STATE

_Last updated: 2026-09-27, rewritten from a survey of the code. The previous
version was dated 2026-08-19 and had fallen behind it._

## Where we are

**Phases 1-4 complete. Phase 5 and 6 screens exist and read real data; the
photo meal-logging path is designed but not built.**

- First green build 2026-08-21; installed and checked on a Redmi Note 12
  (Android 15) on 2026-08-22 - dashboard, navigation, Settings, light/dark.
- 43 JVM unit tests across six suites in `app/src/test`: `AdaptiveTdeeTest`,
  `PlanEngineTest`, `CutSimulationTest`, `StreakAndXpTest`,
  `MacroConsistencyTest`, `ResponseParsingTest`.
- Two instrumented suites in `app/src/androidTest` (`MealDaoTest`,
  `ProgressiveOverloadTest`); no recorded device run yet.
- Room is at schema version 1; `app/schemas/.../1.json` is committed.

## What exists

**Theme and components** - `core/ui/theme/` (Tones, Color, Type, Fonts with
bundled DM Sans, Shape, Spacing, Motion, Insets, Theme) and
`core/ui/components/` (the `Fm*` set: app bars, navigation bar + rail,
expandable FAB, list items, states, tactile button, surfaces, selection,
meters, gamification, portion picker, quick-add, log bar, trend chart,
animation). The neo-brutalist `Brut*` components are gone; see DECISIONS
2026-08-22.

**Data** - Room entities, DAOs (`MealDao`, `RecipeDao`, `WorkoutDao`,
`SessionDao`, `PlanDao`), `ExerciseSeed`, DataStore `SettingsStore`,
`ImagePreprocessor`. Repositories: `MealRepositoryImpl`, `PlanRepositoryImpl`
(joins weigh-ins and meal totals into `AdaptiveTdee`),
`ProgressRepositoryImpl` (streak + XP).

**AI** - `CulinaryLlmClient` with `generateRecipe` (wired to the Chef) and
`estimateFromPhoto` (built, not called by anything), structured output via
`RecipeSchemas`, Atwater advisories, `LlmResult` failure cases.

**Domain** - `domain/plan/` (body fat, weight trend, adaptive TDEE, macro
solver, plan engine) and `domain/progress/` (`StreakEngine`, `XpEngine`), all
pure Kotlin.

**Screens** - Dashboard (meals, plan target, streak, sessions), Chef tab +
AI Chef chat (generates recipes, "Cook & Eat" and "log again"), Train +
Active Session (routines, sets, rest timer via `WorkoutDao`/`SessionDao`),
Plan (weigh-ins, trend chart, projection via `PlanRepository`), Settings.

## Known issues

- **A valid day counts Dishes, not Meals** - both `MealDao` day counts use
  `COUNT(*)`, so one multi-dish photo meal would make a day valid and earn a
  streak day. Fix: #2. (Rule in `CONTEXT.md`; DECISIONS 2026-09-27.)
- **All three FAB actions are no-ops** - "Snap a meal", "Ask the chef" and
  "Start a workout" have empty handlers in `FitMasalaApp.kt`.
- **No manual meal entry screen** - `MealRepository.logManual` exists but
  nothing calls it. Built as part of #9.
- **Plan tab uses the camera icon.** Fix: #4.
- **Probably dead code** - `data/remote/AiService.kt` + `AiModels.kt` +
  `SystemPrompts.kt` + `data/repository/AiRepository.kt` form a second LLM path
  that only `NetworkModule` references; `PlaceholderScreen` is no longer used.
  Confirm and delete in a separate change.

## Next: photo meal logging

Designed in the 2026-09-27 grilling session (glossary in `CONTEXT.md`,
decisions in `docs/DECISIONS.md`) and broken into tickets, all labelled
`ready-for-agent`:

| Ticket | Blocked by |
| --- | --- |
| #2 Count Meals, not Dishes | - |
| #3 Structured Portions in the photo estimate | - |
| #4 Plan tab trend icon | - |
| #5 Snap a meal: camera → estimate → review → log | #2, #3 |
| #6 Edit Dishes on the review sheet | #5 |
| #7 Add a missed Dish by typing it | #6 |
| #8 Gallery logging, EXIF time, duplicate warning | #5 |
| #9 Estimate failure paths + manual entry | #5 |
| #10 Prune meal photos after 90 days | #5 |

Start with #2: it is a live bug and the smallest change.

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
