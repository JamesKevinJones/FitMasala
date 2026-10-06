# STATE

_Last updated: 2026-10-01._

## Where we are

**FitMasala is going fully on-device: no API key, no Claude, no Gemini, no
network** (DECISIONS 2026-10-01). The cloud features merged on 27-28 September
(#2-#36) keep running on `main` until their replacements land. The plan below
removes them in an order where every merged state still works.

**`main` has now been through Gradle.** On 2026-10-01 the first local build
compiled the app, KSP (Room's query checks and the Hilt graph), the debug APK
and the instrumented tests, and 186 unit tests passed. The one failure was the
Claude vs Gemini comparison tool: it used `java.awt` and `javax.imageio`, which
are not on Android's compile classpath, so no unit test could compile. It is
deleted, and Room's `2.json` schema from that build is committed.

**Not verified yet:** anything on the phone. That means the Compose screens,
navigation, the v1 -> v2 migration and the instrumented suites.

## The plan

Each step is one issue and one PR.

1. ✅ Record the decision, delete the comparison tool, commit schema v2.
2. ⬜ Port the fixes found on 2026-10-01:
   - Repeated meals log zero carbs and fat.
   - Add a meal detail view with delete (`deleteMeal` has no caller).
   - Back navigation on sub-screens other than Settings.
   - Remove the active session's nested Scaffold and second FAB.
3. ⬜ Brag video: the badge "100% on-device · IFCT-calibrated" was never true.
   It becomes "No backend · no account"; re-render with a new poster.
4. ⬜ Dish catalogue:
   - Sourced ingredient rows (USDA FoodData Central or the pack label).
   - Catalogue dishes as ingredient grams per Portion (katori = 150 ml).
   - Catalogue search in the review sheet and in "Log a meal".
   - The Chef becomes a recipe library.

   Kevin then corrects the quantities to how it is cooked at home.
5. ⬜ Remove the cloud:
   - The Anthropic and Gemini backends, the provider setting and its keys.
   - AI Chef generation.
   - Barcode lookup and its scanner.
   - The `INTERNET` and `ACCESS_NETWORK_STATE` permissions.

   The AGENTS.md rules change with it.
6. ⬜ Recognition:
   - A bundled image embedder (MediaPipe Image Embedder, MobileNet-V3).
   - Confirmed photos: an embedding plus a 224 px thumbnail, in a new Room table
     with a real migration.
   - A pre-filled review sheet plus chips.
   - Thresholds tuned on the phone.

The catalogue (4) lands before the cloud goes (5), so photo and typed logging
always have something behind them.

## What Kevin needs to do

1. **Fix the shared Gradle cache before building locally.** Every entry under
   `~/.gradle/caches/8.13/transforms` lost its `metadata.bin` on 2026-09-06,
   so builds fail with "Could not read workspace metadata". Close Android
   Studio and move that directory aside; Gradle rebuilds it.
2. **Run the instrumented suites on the phone:**
   `.\gradlew.bat :app:connectedDebugAndroidTest`. `MigrationTest` proves
   v1 -> v2 keeps existing meals. Steps 4 and 6 also need the phone.
3. **The `security` CI check needs a `CLAUDE_API_KEY` repo secret** (Settings
   -> Secrets and variables -> Actions). That is the CI reviewer only; the app
   itself holds no key.

## What exists

**Theme and components** - `core/ui/theme/` and the `Fm*` set in
`core/ui/components/` (DECISIONS 2026-08-22).

**Data** - Room v2 (entities, DAOs, `Migrations.MIGRATION_1_2`),
`ExerciseSeed`, DataStore `SettingsStore` (theme, both keys, provider, model
overrides, rest timer), `ImagePreprocessor` + `PhotoSizing`, photo store and
90-day pruning. Day counts are distinct Meals, not Dishes (#2).

**AI, until step 5** - `CulinaryLlmClient` (recipes, photo and typed-dish
estimates, Atwater advisories) over one `ProviderBackend` that routes every
call to the provider chosen in Settings, with no fallback: `AnthropicBackend`
or `GeminiBackend`, each on its own OkHttp client with its own key header.
Every logged Estimate records its model (#20).

**Domain** - `domain/plan/` (body fat, weight trend, adaptive TDEE, macro
solver, plan engine) and `domain/progress/` (streaks, XP), pure Kotlin.

**Screens** - Today (meals, plan target, streak, sessions), Chef + AI Chef,
Snap a meal (camera or gallery, review sheet, per-Dish editing, typed Dishes,
failure paths, log by hand), Log a meal (FAB: one typed dish, MANUAL not an
Estimate, movable time), Scan a barcode (FAB: Google code scanner or typed
digits, label from Open Food Facts, servings else grams, logged as BARCODE),
Train + Active Session, Plan (setup form, weigh-in sheet with optional tape,
trend chart, projection, edit or restart the cut), Settings (theme, AI
provider, keys, rest timer).

## Known issues

- **The Train overline is static text** ("Push · Pull · Legs"), as is the
  Chef's; only Today and Plan show live values.

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
