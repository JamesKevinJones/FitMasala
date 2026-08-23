# STATE

_Last updated: 2026-08-19_

## Where we are

**Phases 1-4 complete and RUNNING ON HARDWARE. Phase 5 partial.**

Installed and verified on a Redmi Note 12 (23049PCD8I), Android 15, on
2026-08-22. Dashboard, navigation, Settings and the light/dark toggle all
confirmed working by screenshot. 43 unit tests pass, lint is clean.

First green build: 2026-08-21. `:app:assembleDebug` produces a 20.6 MB debug
APK; `:app:testDebugUnitTest` runs 43 tests across six suites with zero failures
(incl. CutSimulationTest, which drives the whole plan loop for 20 simulated
weeks, and ResponseParsingTest, which proves the API-to-Room chain against a
fixture). `:app:lintDebug` is clean: 0 errors, 43 warnings. Room exported
`app/schemas/com.kevinjones.fitmasala.data.local.FitMasalaDatabase/1.json` -
commit it.

Android Studio Quail is now installed on the user's machine but the project has
not been opened in it yet, so no version number and no line of Kotlin here has
been through a compiler.

## What exists

**Build** — Gradle files, version catalog, ProGuard rules, `app/schemas/` wired
into androidTest assets.

**Theme (redesigned 2026-08-22)** — the brutalist theme was scrapped after it
was seen on a phone; see docs/DECISIONS.md. Now `core/ui/theme/` (Color, Type, Shape, Theme + `BrutPalette`
CompositionLocal) and `core/ui/brutalism/` (`brutShadow`/`brut`/`brutOutline`/
`hazardStripes` modifiers, and `BrutPanel`, `BrutButton`, `BrutChip`,
`BrutTextField`, `BrutRule`, `MonoLabel`, `HazardCallout`, `BrutStatRow`).
Ported from StarMatch's `globals.css`.

**Data layer**
- `data/local/entity/` — `Enums`, `Macros` (embedded, no column prefix),
  `LoggedMealEntity`, `RecipeEntity` + `RecipeIngredientEntity`,
  `WorkoutEntities` (Exercise, Routine, RoutineExercise, Session, Set)
- `data/local/relation/` — `Relations` (RecipeWithIngredients,
  RoutineWithExercises, SessionWithSets, SetWithExercise) and `Projections`
  (DailyMacroTotals, DailyCalories, ExercisePersonalBest, SessionSummary)
- `data/local/converter/Converters.kt` — enums by name with safe fallbacks,
  string lists as JSON
- `data/local/dao/` — `MealDao`, `RecipeDao`, `WorkoutDao`, `SessionDao`
- `data/local/FitMasalaDatabase.kt` (v1, `exportSchema = true`) and
  `ExerciseSeed.kt` (33 starter exercises incl. yoga/mobility)
- `data/prefs/SettingsStore.kt` — DataStore: API key, provider, model, macro
  targets, rest timer
- `di/DatabaseModule.kt` — database, DAOs, raw-SQL seeding, `PRAGMA foreign_keys`
- `core/util/DateKeys.kt` — the one place millis becomes `dayEpoch`

**Tests (androidTest, unrun)** — `MealDaoTest` (round-trip, day totals, empty-day
zeroes, calorie trend) and `ProgressiveOverloadTest` (previous performance
excludes current session and warm-ups, 1RM ranking, zero-row aggregate, active
session recovery, cascade behaviour).

**Plan manager (`domain/plan/`, pure Kotlin, no Android imports)**
- `PlanModels` - BodyComposition, MacroTarget, PlanProjection, PlanWarning,
  AdaptiveState, CutAggression
- `BodyFatEstimator` - Navy tape method, Mifflin-St Jeor, Katch-McArdle
- `WeightTrend` - EMA smoothing + least-squares weekly rate + stall detection
- `AdaptiveTdee` - back-calculates real maintenance from trend + intake
- `MacroSolver` - protein (from lean mass), fat floor, carbs remainder
- `PlanEngine` - target, goal weight, timeline, progress review

**Plan persistence** - `BodyMetricEntity`, `PlanGoalEntity`, `PlanDao`, registered
on the database and provided by Hilt. `LoggedMealEntity` gained `source`
(`MealSource`) and `photoPath`.

**Photo pipeline** - `data/photo/ImagePreprocessor`: two-pass decode, EXIF
rotation, 1568px downscale, JPEG q85, on `Dispatchers.Default`. CameraX and
androidx-exifinterface added to the catalog; CAMERA permission in the manifest.

**Tests** - `PlanEngineTest` and `AdaptiveTdeeTest` are JVM unit tests
(`:app:testDebugUnitTest`), so they need no device. These are the only part of
the project whose correctness can be checked without hardware - run them first.

**AI networking (`data/remote/`)**
- `dto/` — `AnthropicRequest` / `AnthropicResponse` (adaptive thinking,
  `output_config.effort` + `format`, refusal `stop_details`, server-side
  fallbacks) and `CulinaryDtos` mirroring the schemas
- `prompt/CulinaryPrompts` — the recipe and vision system prompts
- `prompt/RecipeSchemas` — JSON schemas for structured output
- `api/AnthropicApi` + `AnthropicAuthInterceptor` (key read per-request from
  DataStore; `MissingApiKeyException` distinguishes "no key" from "bad key")
- `CulinaryLlmClient` — both calls, refusal/truncation handling, Atwater
  validation surfaced as advisories
- `LlmResult` — enumerated failures so each gets its own UI
- `CulinaryMappers` — DTO to entity, plus photo base64
- `di/NetworkModule` — Json (with unknown-block fallback), OkHttp, Retrofit
- `MacroConsistencyTest` (JVM) covers the Atwater check

## Phase 5 - what is still missing

Settings, Chef and Plan are built and on device. **Train is still a
`PlaceholderScreen`, and NO screen is wired to Room yet** - Chef and Plan both
render hardcoded sample data. Still to build:

- **Repositories + ViewModels**: the single biggest gap. Chef's frequent-meal
  list, the dashboard's rows and Plan's weight series are all hardcoded.
- **Chef**: chat UI against `CulinaryLlmClient.generateRecipe`; the screen
  shell and quick-add exist, the AI call is not wired
- **Train**: routine builder, live session tracker, rest timer
- **Plan**: onboarding for body stats, weight trend chart, the projection to 12%
- **Photo capture**: CameraX screen feeding `ImagePreprocessor` ->
  `estimateFromPhoto`
- **ViewModels + repositories** joining Room to the screens. The dashboard still
  renders hardcoded sample rows.

## Device automation - do not repeat

Driving the phone with `adb shell input tap` tripped MIUI's App Lock, which
took over the foreground and swallowed the taps mid-sequence. Screenshots via
`adb exec-out screencap -p` are safe and useful; blind tap injection on this
device is not. Note also that Git Bash rewrites `/sdcard/...` into a Windows
path, so `adb shell screencap` + `adb pull` fails - use `exec-out`.

## Not yet built for the plan manager

- Wiring `ImagePreprocessor` -> `CulinaryLlmClient.estimateFromPhoto` behind a
  repository (the call itself exists; nothing invokes it yet)
- CameraX capture screen and the plan/onboarding UI (Phase 4/5)
- A `PlanRepository` joining PlanDao + MealDao into `AdaptiveTdee` inputs

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

## Exact next step

1. Open `C:\Users\kj638\Kevin codes\FitMasala` in Android Studio, sync.
2. `:app:testDebugUnitTest` — the plan-engine tests need no device and are the
   fastest real signal that anything works.
3. `:app:assembleDebug` — the first real check on Room and Hilt annotation
   processing.
4. Confirm `app/schemas/1.json` was generated, and commit it.
5. `:app:connectedDebugAndroidTest` with a device attached — the two test classes
   are the proof the data layer works.

Then **Phase 3: the LLM Retrofit service, request/response DTOs, the strict
culinary system prompt, and the JSON macro parser.** Note that
`RecipeEntity.rawResponse` exists so responses can be re-parsed later without
re-asking the model.
