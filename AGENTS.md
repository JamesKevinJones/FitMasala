# FitMasala

Native Android app for one user (Kevin). Combines an authentic-Indian culinary AI
(recipes + macro estimation) with a strength-training log, on one daily dashboard.
Everything persists locally; the only network traffic is the user's own LLM API
calls using a key they paste into Settings.

## Stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.0.21, JVM target 17 |
| UI | Jetpack Compose, Material 3 (full tonal system, light + dark), edge-to-edge |
| Architecture | Clean Architecture + MVVM (`data` / `domain` / `presentation`) |
| DI | Hilt 2.52 (KSP, not kapt) |
| Persistence | Room 2.6.1 (KSP) + DataStore Preferences for the API key |
| Networking | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization |
| Async | Coroutines + StateFlow |
| Build | AGP 8.13.2, Gradle 8.13, JDK 21, version catalog at `gradle/libs.versions.toml` |
| SDK | minSdk 26, target/compile 35 |

## Commands

Run from the project root (`Kevin codes\FitMasala`), not from `Kevin codes`.
PowerShell needs the `.bat`; the bare `./gradlew` form is bash-only.

```
.\gradlew.bat :app:testDebugUnitTest      # JVM tests - no device needed
.\gradlew.bat :app:assembleDebug          # first real check on Room + Hilt KSP
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest   # needs a device/emulator
.\gradlew.bat :app:installDebug
```

One test class, or one method:

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.kevinjones.fitmasala.plan.PlanEngineTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*PlanEngineTest.macrosAddUpToTheCalorieTarget"
```

One instrumented class:

```
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kevinjones.fitmasala.data.ProgressiveOverloadTest
```

### Environment gotchas that cost real time

- **Set `JAVA_HOME` before using the terminal.** `org.gradle.java.home` in
  `gradle.properties` pins JDK 21 for the build, but it is read only after the
  JVM starts - it cannot bootstrap the launcher script. Without `JAVA_HOME`,
  `gradlew.bat` fails with "JAVA_HOME is not set". Studio's own Gradle JDK
  setting covers IDE builds only.
- **Do not put the Studio-bundled JBR 25 back.** Gradle 8.x cannot parse it and
  fails with a bare `* What went wrong:` / `25.0.2` - a version-parse failure
  that looks nothing like a toolchain problem. Note `gradle --version` succeeds
  on it anyway, so never use `--version` to conclude the toolchain works.
- **`domain/plan` tests are the only ones that prove anything without hardware.**
  Reach for `:app:testDebugUnitTest` before anything that needs a device.

## Module layout

Single `:app` module. Package `com.kevinjones.fitmasala`:

```
core/ui/theme      Color, Type, Shape, Motion, Theme + the FmColors CompositionLocal
core/ui/components FmAppBars, FmNavigation (bar + rail), FmFab, FmListItems,
                   FmStates (empty/skeleton/error), FmButton (tactile), FmCard,
                   FmChip, FmTextField, FmMeters, FmGamification
core/util          Formatters, date helpers, Result wrappers
data/local         Room: entity/ dao/ converter/ relation/ + FitMasalaDatabase, ExerciseSeed
data/remote        LLM API: api/ dto/ prompt/ + CulinaryLlmClient, LlmResult, mappers
data/prefs         DataStore — API key, provider choice, daily targets
data/photo         On-device image preprocessing for photo calorie logging
data/repository    Repository implementations
domain/model       Plain Kotlin models the UI actually consumes
domain/plan        Cutting-plan engine: body fat, weight trend, adaptive TDEE,
                   macro solver, projections. Pure Kotlin, JVM-testable.
domain/repository  Repository interfaces
domain/usecase     Single-responsibility use cases
di                 Hilt modules (DatabaseModule, NetworkModule, RepositoryModule)
presentation/      navigation/ dashboard/ chef/ workout/{builder,session,history}/ settings/
```

## How the pieces connect

The three features are not independent - they meet at the daily calorie number,
and that path is worth understanding before changing anything in the middle of it.

**Recipe to logged meal.** The AI chef returns JSON, which is parsed into
`RecipeEntity` + `RecipeIngredientEntity` (`rawResponse` keeps the model's
original text so a better parser can re-read old recipes without re-asking the
model). "Cook & Eat" writes a `LoggedMealEntity` that **copies** name, region and
macros rather than joining back to the recipe - history must not change when a
recipe is edited or deleted, which is also why `sourceRecipeId` carries no
foreign key.

**Logged meal to plan target.** `MealDao.observeDayTotals` aggregates by
`dayEpoch` (a stored, indexed epoch-day, not derived at read time). Those totals
plus `BodyMetricEntity` weigh-ins feed `AdaptiveTdee`, which back-calculates real
maintenance calories from the observed weight trend. `PlanEngine` turns that into
today's target, and `MacroSolver` splits it protein-first. Nothing in
`domain/plan` touches Room or Android - the repository layer adapts entities into
its plain-value inputs.

**Why that direction matters.** Photo and LLM calorie estimates are imprecise, so
the target is never derived from them directly. A *consistent* estimation bias
lands entirely in the computed maintenance figure and cancels out of the deficit.
The failure mode the system cannot absorb is inconsistent logging, which is why
`AdaptiveTdee` discards days with fewer than two logged meals.

**Workouts join only at the dashboard.** `SessionDao` and `MealDao` share nothing
but `dayEpoch`. Progressive overload lives entirely in
`SessionDao.previousPerformance` / `observePersonalBest`.

Design rationale for all of the above is in `docs/DECISIONS.md`, one dated entry
per decision. Read it before reversing something that looks arbitrary.

## Rules

- **No `// TODO: implement` stubs.** Every file committed must compile and do its job.
- **KSP everywhere, never kapt.** Room and Hilt both go through KSP; adding kapt
  re-introduces a second annotation-processing pass and slows the build.
- **Use Material 3's own components, themed.** NavigationBar, LargeTopAppBar,
  FloatingActionButton, Badge, HorizontalDivider and friends already carry the
  bar heights, 48dp targets, state layers and TalkBack semantics. Wrap them in
  `core/ui/components` to fix the styling; never reimplement them.
- **Spacing comes from `Fm`** (8dp grid, 4dp secondary). No raw `.dp` for
  layout spacing - that is how spacing drifts screen by screen.
- **Read the window size class, not the device.** Compact gets the bottom bar,
  wider gets the rail; margins 16/24dp; content caps at 640dp.
- **Consume insets exactly once.** Scaffold applies them; call
  `consumeWindowInsets(innerPadding)` so children do not apply them again.
- **Never hardcode a colour.** `MaterialTheme.fm.*` for progress/macro/streak
  tokens, `MaterialTheme.colorScheme.*` for everything else.
- **Depth comes from a lighter surface plus an 8% hairline, never a shadow.**
  On near-black a drop shadow is invisible; compensating with a hard offset
  shadow is what made the previous theme look like stacked stickers.
- **Sentence case.** No uppercase labels outside a rare micro-label. Uppercase
  destroys word-shape recognition and made the old screen unreadable at a
  glance.
- **Warm = food, green = progress.** Do not use saffron for an achievement or
  green for a macro. That split is the app's whole information design.
- **Colours are tone positions, not hex picks.** Roles come from the five ramps
  in `Tones.kt`; light uses tone 40/90, dark uses 80/30. Never add a raw hex to
  a ColorScheme - add a tone to a ramp.
- **Both themes are designed peers.** Check every change in light AND dark;
  light hairlines are darker and more opaque, not the dark values inverted.
- **Only primary actions get the tactile lip** (`FmButton`). If everything is
  tactile there is no hierarchy and the effect stops registering.
- **This is not a delivery app.** No food photography, rating rows, promo
  banners, circular category rails, or search-as-hero. Quick-commerce
  INTERACTIONS (stepper, sticky running total) are welcome; its visual
  furniture is not.
- **Portions are Indian units first** (`FmPortionPicker`). Katori, roti,
  piece, plate - grams last. Nobody weighs a katori of dal, and demanding a
  gram conversion at log time is what stops people logging.
- **Meters must show overshoot** rather than capping at 100%. Going over is the
  most useful fact a calorie tracker holds.
- **Motion values come from `FmMotion`.** Press 90ms, surfaces 220ms, exits
  60-80% of enters. Celebration is reserved for milestones only.
- **Gamification stays tied to the maths.** A streak day requires the same two
  logged meals `AdaptiveTdee` needs. Never award XP for a bigger deficit.
- **The API key never leaves the device** except in the `x-api-key` header of the
  user's own LLM request. Never log it. Backups are disabled app-wide in the
  manifest for the same reason.
- **Room schemas are exported** to `app/schemas/` and committed, so migrations
  can be diffed. Bump `version` and write a `Migration` — never
  `fallbackToDestructiveMigration()`, since the meal and workout history is the
  whole point of the app.
- **Macro JSON comes from `output_config.format` with a schema in
  `RecipeSchemas`** — never from prompt instructions, and NEVER from assistant
  prefill, which is a 400 on Opus 5. Schemas and the DTOs in `dto/CulinaryDtos.kt`
  must change together.
- **A `Success` with advisories is not a clean result.** The Atwater check can
  flag macros that are schema-valid and nutritionally wrong; the UI must show
  advisories rather than logging the meal silently.
- **The plan trusts the scale, not the camera.** Photo estimates are ±20–40%.
  Targets come from `AdaptiveTdee`, which back-calculates maintenance from the
  weight trend. Never wire a photo estimate straight into a target.
- **Never render an estimate and a weighed value identically.** `MealSource` and
  `isAiEstimate` exist so the UI can tell them apart; `HazardCallout` is the
  marker for an estimate.
- **`domain/plan` stays free of Android imports** so it keeps running as fast JVM
  unit tests. It is the only part of this app whose correctness can be proven
  without a device.

## Build phases

1. ✅ Scaffold — Gradle, manifest, theme, Hilt application, empty package tree
1b. ✅ Theme — StarMatch neo-brutalism ported to Compose
2. ✅ Data layer — Room entities, DAOs, database, DataStore prefs, DatabaseModule
3. ✅ AI networking — Retrofit service, DTOs, the culinary system prompt
4. ✅ UI foundation — theme, component set, app shell, dashboard feed — nav graph, dashboard, full theme
5. ⬜ Feature screens — AI Chef chat, workout logging, camera capture
6. 🟡 Plan manager — engine + persistence done; UI and vision call pending

Current position is always in `docs/STATE.md`.
