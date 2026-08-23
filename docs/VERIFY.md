# VERIFY

How to prove a change works. Run from the project root.

## Prerequisites

Android Studio (Ladybug or newer) with JDK 17 and Android SDK 35 installed. None
of these were present on the machine where the project was scaffolded — if
`gradlew` is missing, see the Blocker section in `docs/STATE.md` first.

## Sync and build

```bash
./gradlew --version
```

```bash
./gradlew :app:assembleDebug
```

A green `assembleDebug` is the bar for "Phase N compiles". It is not the bar for
"Phase N works".

## Static checks

```bash
./gradlew :app:lintDebug
```

## Unit tests

```bash
./gradlew :app:testDebugUnitTest
```

## Instrumented tests (device or emulator attached)

```bash
./gradlew :app:connectedDebugAndroidTest
```

Room migration tests live here — `MigrationTest` reads the committed schema JSON
out of `app/schemas/`, which is wired into androidTest assets in
`app/build.gradle.kts`.

## Install on the phone

Enable Developer Options → USB Debugging, plug in, then:

```bash
./gradlew :app:installDebug
```

The debug build installs as `com.kevinjones.fitmasala.debug`, so it sits alongside
a release build rather than replacing it.

## What is proven automatically (as of 2026-08-22)

`:app:testDebugUnitTest` - 32 tests, 0 failures, no device needed:

| Suite | Proves |
| --- | --- |
| `PlanEngineTest` (7) | Rate clamping, calorie floors, low-body-fat taper, protein priority, goal-weight maths |
| `AdaptiveTdeeTest` (6) | Maintenance back-calculation, sparse-logging rejection, noise rejection, clamping |
| `CutSimulationTest` (6) | The whole feedback loop over 12-20 simulated weeks |
| `MacroConsistencyTest` (6) | The Atwater cross-check that catches forgotten frying oil |
| `ResponseParsingTest` (7) | API envelope -> DTO -> Room entity, incl. refusal/truncation/unknown blocks |

`:app:lintDebug` - 0 errors, 43 warnings.
`:app:assembleDebug` - 20.6 MB debug APK.

### The simulation

`CutSimulationTest` runs a synthetic body with a known true TDEE for months.
It eats what the plan prescribes, its weight moves by real physiology, and a
NOISY scale reading feeds back in. The plan recomputes weekly from that logged
history alone. Nothing is mocked. Last run:

```
honest logging:   85.0kg @ 22.0%  ->  75.2kg @ 13.1%   (20 weeks)
15% under-logged: 77.0kg @ 15.0%  vs  76.4kg @ 14.4% honest (16 weeks)
true TDEE 2657    vs  learned 2783  (never told, inferred from logs)
lean mass lost over 18 weeks: 0.9kg
verdict: ON_TRACK
```

The second line is the one that matters: a camera reading 15% light lands
within 0.6kg of honest logging after four months, because the bias falls
entirely into the computed maintenance figure and cancels out of the deficit.

## What is NOT proven yet, and needs you

1. **Room DAOs.** `:app:connectedDebugAndroidTest` needs a device or emulator.
   `adb devices` currently lists none and no AVD exists. Until this runs, every
   SQL query in the DAOs is unexecuted.
2. **A real API call.** Nothing has contacted api.anthropic.com. The wire
   format is proven against a fixture, not against the live API. Needs a key
   pasted into Settings - which needs Phase 4/5 UI.
3. **Anything visual.** No screen has rendered on hardware. The theme has
   Compose previews but has never been seen on a real display.

## Per-phase proof

| Phase | Proof it actually works |
| --- | --- |
| 1 Scaffold | `assembleDebug` green; both `MainActivity` previews render; app launches and holds up in system light AND dark |
| 2 Data | `connectedDebugAndroidTest` — DAO insert/read round-trips for meals and sets |
| 3 AI | Real API call with a pasted key returns JSON that parses into the macro model |
| 4 UI | Nav graph reaches every destination; dashboard renders logged meals from Room |
| 5 Features | Log a chat recipe via "Cook & Eat" and see it change the dashboard totals |

## Watch out for

- **Never** verify a macro-parsing change against a mocked LLM response only. The
  model's real output drifts; test against a live call before trusting the parser.
- **Check the theme in both modes, every time.** The palette inverts hard
  (ink borders on paper becomes paper borders on near-black). A change that looks
  right in dark can be invisible in light. The two `@Preview`s on `MainActivity`
  are the cheap check; the phone with system dark-mode toggled is the real one.
- **A clipped hard shadow means a parent is clipping**, not that the modifier is
  broken. `Modifier.brutShadow` draws outside its own bounds by design.
- The rest timer must survive screen-off. Verify by starting a set, locking the
  phone for 90s, and unlocking — not by watching it in the foreground.
