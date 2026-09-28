# STATE

_Last updated: 2026-09-28, after the "finish the project" pass. Everything
planned is now built; what remains is merging three PRs, one local build, and
the steps only Kevin can do on his phone and with his keys._

## Where we are

**All six phases are built.** Photo meal logging (#2-#10) and the provider
work (#20-#22) are on `main`. The last three PRs are open, independent of each
other, and can merge in any order:

| PR | What | Closes |
| --- | --- | --- |
| #29 | Plan tab setup form + weigh-in sheet, FAB actions, rail navigation, real app-bar dates, dead code removed | Phase 6, known issues |
| #30 | The Chef on the chosen provider (Gemini or Claude) | #24 |
| #31 | Opt-in Claude vs Gemini comparison runner | #23 (after Kevin's run) |

None of this has been through Gradle: every PR since #14 was written in a
cloud container with no Android SDK.

### What has been verified, and how

- **JVM harness** (a standalone Gradle project compiling the real pure-Kotlin
  files against stubs for Android and Room): every unit-test suite passes,
  including the new `PlanFormsTest` (15), `ComparisonToolsTest` (12) and the
  Gemini recipe cases in `GeminiBackendTest`.
- **Live APIs, fake keys:** the comparison runner reached both Anthropic and
  Google, got `Unauthorized`, stopped at the first photo, and wrote no key.
- **New SQL** was run in SQLite against tables built from the exported schema.
- **Not verified anywhere yet:** Compose screens, navigation, Hilt wiring,
  Room's compile-time query check, the v1 -> v2 migration on a device, and
  every instrumented test.

## What Kevin needs to do

1. **Build once from `main` after merging #29-#31:**
   ```
   .\gradlew.bat :app:testDebugUnitTest
   .\gradlew.bat :app:assembleDebug
   ```
2. **Commit `app/schemas/com.kevinjones.fitmasala.data.local.FitMasalaDatabase/2.json`.**
   Room writes it during that build (schema v2 added `estimateModel`, #20). It
   is deliberately not hand-written.
3. **Run the instrumented suites on the phone:**
   ```
   .\gradlew.bat :app:connectedDebugAndroidTest
   ```
   `MigrationTest` proves v1 -> v2 keeps existing meals.
4. **On the phone:** start a cut (typed body fat, then tape only), log a
   weigh-in with and without the tape, snap a meal, switch to Gemini with the
   Anthropic key removed and ask the Chef for a recipe, then Cook & Eat.
5. **Add the `CLAUDE_API_KEY` Actions secret** (Settings -> Secrets and
   variables -> Actions). The `security` check is red on every PR until then;
   nothing in the code fixes it.
6. **The comparison run (#23):** photos + `known.csv`, both keys, then
   `docs/VERIFY.md` -> *Claude vs Gemini on your own meals*. Paste the headline
   numbers under the 2026-09-27 Gemini entry in `docs/DECISIONS.md`; change the
   default provider only if the rule says so.

## What exists

**Theme and components** - `core/ui/theme/` and the `Fm*` set in
`core/ui/components/` (DECISIONS 2026-08-22).

**Data** - Room v2 (entities, DAOs, `Migrations.MIGRATION_1_2`),
`ExerciseSeed`, DataStore `SettingsStore` (theme, both keys, provider, model
overrides, rest timer), `ImagePreprocessor` + `PhotoSizing`, photo store and
90-day pruning. Day counts are distinct Meals, not Dishes (#2).

**AI** - `CulinaryLlmClient` (recipes, photo and typed-dish estimates, Atwater
advisories) over one `ProviderBackend` that routes every call to the provider
chosen in Settings, with no fallback: `AnthropicBackend` or `GeminiBackend`,
each on its own OkHttp client with its own key header. Every logged Estimate
records its model (#20).

**Domain** - `domain/plan/` (body fat, weight trend, adaptive TDEE, macro
solver, plan engine) and `domain/progress/` (streaks, XP), pure Kotlin.

**Screens** - Today (meals, plan target, streak, sessions), Chef + AI Chef,
Snap a meal (camera or gallery, review sheet, per-Dish editing, typed Dishes,
failure paths, log by hand), Train + Active Session, Plan (setup form, weigh-in
sheet with optional tape, trend chart, projection), Settings (theme, AI
provider, keys, rest timer).

## Known issues

- **Log by hand only exists inside Snap a meal** - there is no standalone
  manual-entry screen; `MealRepository.logManual` is called only from the
  failure path.
- **A cut cannot be edited once started** - "Start a cut" is only offered
  while no goal exists. Starting over means clearing app data.
- **The Train overline is static text** ("Push · Pull · Legs"), as is the
  Chef's; only Today and Plan show live values.

## Next

After the steps above, the project is feature-complete for its brief. Likely
follow-ups, none started: a standalone "log a meal" form, editing or
restarting a cut from the Plan tab, and barcode lookup against Open Food Facts
(DECISIONS 2026-09-27 marks it as a separate, later feature).

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
