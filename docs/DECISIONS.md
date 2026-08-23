# DECISIONS

Why the project is built the way it is. Append; don't rewrite history.

---

## 2026-08-19 — Hilt over Koin

**Decision:** Hilt for DI.

**Why:** The prompt asked for whichever is cleanest for a single-user app, which
argues for Koin (no code generation, no build-time cost). But Room already forces
KSP into the build, so Hilt's annotation processing is riding a pass that exists
either way — the marginal build cost is small. In exchange, Hilt gives
compile-time graph validation and `hiltViewModel()` integrates with
navigation-compose without any manual scope plumbing. Koin's failures are runtime
`NoBeanDefFoundException`s, which is a worse trade in an app that will be extended
phase by phase.

---

## 2026-08-19 — KSP, never kapt

**Decision:** Room and Hilt both use KSP.

**Why:** kapt runs a full Java stub-generation pass. Both libraries have stable
KSP support at the pinned versions. Mixing the two would mean paying for both.

---

## 2026-08-19 — No dynamic color

**Decision:** Fixed colour schemes, `dynamicColor` never wired up.

**Why:** The palette is the product's identity. Material You would repaint the
flat accents from the user's wallpaper, and a brutalist palette that shifts per
device is not a design language — it's a coincidence. Also: the macro chart
colours are fixed (protein = volt, carbs = acid, fat = coral) so a glance at the
pie is readable without re-reading the legend; dynamic colour would destroy that
constancy.

---

## 2026-08-19 — minSdk 26

**Decision:** minSdk 26 (Android 8.0).

**Why:** `java.time` is available natively from 26, so no core library desugaring
is needed for the date handling that meal-logging and workout history both lean on.
Adaptive launcher icons also work from 26, so no PNG mipmaps are required. This is
a personal-use app on a modern phone; the coverage loss below 26 is irrelevant.

---

## 2026-08-19 — API key in DataStore, backups disabled

**Decision:** The LLM API key lives in DataStore Preferences, and cloud backup +
device transfer are excluded for the whole app in the manifest.

**Why:** The app has no backend by design — the key sits on the device. Leaving
Android Auto Backup on would push it (and the entire meal/workout history) to the
user's Google account. `androidx.security:security-crypto` was the obvious
alternative for encrypting it at rest, but it is deprecated and its replacement
isn't stable; excluding the app from backup addresses the actual exfiltration path
without depending on a dead library.

---

## 2026-08-19 — No `fallbackToDestructiveMigration()`

**Decision:** Room schemas exported to `app/schemas/` and committed; every version
bump gets a real `Migration`.

**Why:** The logged meals and the progressive-overload history are the entire value
of the database — a destructive migration during development silently deletes
months of training data. Committed schema JSON makes each migration reviewable as
a diff.

---

## 2026-08-19 — Theme ported from StarMatch (neo-brutalism)

**Decision:** The original warm-spice palette was dropped. FitMasala now uses
StarMatch's neo-brutalist design language, ported from
`starmatch/src/app/globals.css`: 3dp hard ink borders, un-blurred offset shadows
at 3/6/10dp, a single 2dp corner radius, and flat high-chroma accents (acid
`#DDF247`, volt `#4D5BFF`, coral `#FF5C4D`, mint, orchid) on paper `#F4F1EA` /
ink `#0B0B0B`, inverting to `#121110` / `#1C1A18` with bright paper rules in dark.

**Why the accents shift between light and dark:** acid at full chroma is
illegible on paper, and volt/coral are too dark on `#121110`. Both problems were
already solved in StarMatch's own `--chart-*` tokens, so the light/dark accent
split here is copied from there rather than re-invented.

**Why a custom shadow modifier:** `Modifier.shadow()` in Compose always renders a
blurred, elevation-tinted drop shadow. That is exactly the soft look brutalism
rejects, and there is no parameter to turn the blur off. `Modifier.brutShadow()`
re-draws the shape solid at an offset via `drawBehind` + `translate` instead.
Consequence: the shadow occupies no layout space, so `Modifier.brut()` reserves
an end/bottom gutter equal to the offset. Anything that clips (a `Card`, an
explicit `Modifier.clip`) will cut the shadow off.

**Why a `BrutPalette` CompositionLocal:** Material 3 has no slot for "the line
colour" or "the panel colour", which are the two tokens carrying the entire
design. Putting them in a CompositionLocal means exactly one place in the app
decides what a border is — hardcoding `Color(0xFF0B0B0B)` at each call site is
how a theme drifts.

**Why a component set, not raw Material:** screens must be built from
`BrutPanel` / `BrutButton` / `BrutChip` / `BrutTextField`, never from `Card`,
`Button` or `OutlinedTextField`. Material's own components ship container fills,
ripples, focus rings and floating labels that all fight a hard border. The M3
`ColorScheme` is still mapped to the brutalist tokens so anything stock that does
appear (Snackbar, date pickers, the IME) lands in the same palette.

---

## 2026-08-19 — Web fonts not bundled yet

**Decision:** Archivo / Space Grotesk / IBM Plex Mono are documented in `Type.kt`
but the families currently resolve to `FontFamily.SansSerif` and
`FontFamily.Monospace`.

**Why:** Downloadable Google Fonts on Android need a `font_certs` string-array —
a long signing blob that Android Studio generates when you add a font resource.
It cannot be hand-written correctly. Shipping a half-wired provider would fail at
runtime with a silent fallback, which is worse than an honest system font. Every
style routes through the three private `FontFamily` values at the top of
`Type.kt`, so the swap is a three-line change once Studio has generated the certs.

---

## 2026-08-19 — `dayEpoch` denormalised onto meals and sessions

**Decision:** Every logged meal and workout session stores an indexed `dayEpoch`
(epoch day) alongside its `eatenAt` / `startedAt` epoch-millis.

**Why:** The dashboard's hottest query is "today's totals". Deriving the local
day from millis inside SQLite means timezone arithmetic on every row of every
read, and SQLite has no real timezone support to do it correctly. Computing it
once at write time makes the query an indexed integer comparison — and it means a
meal stays on the day it was actually eaten even if the phone later crosses a
timezone, which deriving-on-read would silently change.

---

## 2026-08-19 — Logged meals are denormalised from recipes

**Decision:** `logged_meals` copies name, region and macros in, rather than
joining to `recipes` through `sourceRecipeId`. That column carries no foreign key.

**Why:** History must be immutable. If a recipe is edited or deleted, what you
ate three weeks ago cannot change, and a `CASCADE` from a deleted recipe erasing
meal history would be catastrophic and silent. `sourceRecipeId` stays as a soft
back-reference for "show me the recipe" and is allowed to dangle.

---

## 2026-08-19 — Sets key to an exercise id, and exercises are never hard-deleted

**Decision:** `exercise_sets.exerciseId` is a foreign key with `RESTRICT`, the
exercise catalogue is uniquely indexed on `name`, seeded with `INSERT OR IGNORE`,
and retired exercises are archived rather than deleted.

**Why:** Progressive overload only means anything if "Bench Press" is the same
row across months. Three failure modes are closed here: matching on a typed-in
string (a stray space starts a new history), `OnConflictStrategy.REPLACE` on
seeding (REPLACE deletes and re-inserts with a *new* id, orphaning every set ever
logged), and deleting an exercise (which would take the proof you got stronger
with it).

---

## 2026-08-19 — Aggregate queries COALESCE in SQL

**Decision:** Every `SUM`/`MAX` projection wraps in `COALESCE`, and
`observePersonalBest` passes the exercise id through as a bound parameter rather
than selecting the column.

**Why:** An aggregate over zero rows returns one row of NULLs, which Room cannot
write into non-null Kotlin fields — it throws. That is the difference between a
never-lifted exercise showing "0 kg" and the app crashing the first time you open
a new exercise. Both cases are covered in `ProgressiveOverloadTest` and
`MealDaoTest`.

---

## 2026-08-19 — Seeding uses raw SQL in `onCreate`

**Decision:** The starter exercise catalogue is inserted with `db.execSQL` inside
the Room `onCreate` callback, not through the DAO.

**Why:** Going through the DAO means asking Dagger for a DAO belonging to the
database currently being constructed. Doing that off-thread to break the cycle
introduces a race with the first read. Raw inserts run inside Room's own creation
transaction: synchronous, atomic, exactly once. The cost is a hand-written column
list that must track `ExerciseEntity`.

---

## 2026-08-19 — The plan trusts the scale, not the camera

**Decision:** Photo-estimated calories never drive the target directly.
`AdaptiveTdee` back-calculates real maintenance from logged intake plus the
observed weight trend, and the target is derived from that.

**Why:** Image-based calorie estimation runs roughly ±20–40%, and worse on Indian
food specifically — a mixed gravy hides its oil and ghee in a tadka is invisible.
Building a plan on that number directly would stall the cut and give no signal as
to why.

The insight that makes photo logging viable anyway: **a consistent bias cancels
out.** If every estimate reads 20% light, that error lands entirely in the
computed maintenance figure and the *deficit* stays correct. What the system
cannot survive is inconsistency — photographing lunch but not the evening chai.
Hence `MIN_MEALS_FOR_A_VALID_DAY`: a day with one logged meal is not a logged day,
and counting it would inflate the apparent deficit and ratchet the target down for
no reason. `AdaptiveTdeeTest.consistentUnderLoggingIsAbsorbedIntoTheMaintenanceFigure`
is the test that pins this.

---

## 2026-08-19 — Weight is smoothed before it is used for anything

**Decision:** EMA at alpha 0.1 (~10-day half-life), and the rate comes from a
least-squares fit over the smoothed series, not first-vs-last.

**Why:** Daily weight swings 1–2 kg on sodium, carbs, water and gut content —
larger than a whole week's actual fat loss. Reacting to raw readings is how a
working plan gets abandoned on day four. First-vs-last was rejected because one
bloated morning at either end would define the entire slope; the fit uses every
point. `isStalled` is defined against measurement noise (0.15 kg/week), not
against zero.

---

## 2026-08-19 — Macros solve protein → fat → carbs, in that order

**Decision:** Protein set first from LEAN mass (2.2–2.8 g/kg LBM, scaling with
deficit depth), fat floored at 0.6 g/kg bodyweight, carbs take the remainder.

**Why:** Protein is the variable that decides whether the weight lost is fat or
muscle, and at 12% there is no spare muscle to pay with. Filling carbs first and
letting protein take what's left is the standard way a cut becomes muscle loss.
Anchoring to lean mass rather than bodyweight matters too: bodyweight-anchored
targets over-feed protein to someone carrying a lot of fat and under-feed a lean
person — backwards, since the requirement rises as body fat falls.

If the calorie target cannot hold protein plus the fat floor, the plan raises
`CARBS_SQUEEZED_TO_ZERO` rather than silently shaving protein.

---

## 2026-08-19 — Projections use the clamped rate, and goal weight includes lean loss

**Decision:** `estimatedWeeks` is derived from the deficit that survives the 25%
cap and the calorie floor — not from the rate that was requested. Goal weight
solves for the endpoint assuming 10% of lost tissue is lean.

**Why:** Two ways an app lies about a date. Projecting from the requested rate
promises a timeline the engine has already made impossible by clamping. Assuming
perfect lean-mass preservation understates goal weight by a kilo or two and the
timeline with it — wrong in the direction that disappoints. Both are covered by
`PlanEngineTest`.

---

## 2026-08-19 — Photos are preprocessed on-device before upload

**Decision:** Two-pass `BitmapFactory` decode with `inSampleSize`, EXIF rotation,
downscale to 1568px long edge, JPEG q85, all on `Dispatchers.Default`.

**Why:** Three separate problems. Decoding a 50 MP photo in one pass costs ~200 MB
and OOMs mid-range phones — the most common crash in photo-logging apps; the
bounds-only first pass avoids ever materialising it. EXIF rotation is not
cosmetic: phone cameras write orientation to metadata rather than rotating pixels,
so skipping it sends every portrait thali sideways and measurably degrades
recognition. And 1568px is where the major vision APIs stop gaining accuracy, so
anything larger costs upload time and tokens for nothing.

`Dispatchers.Default`, not `IO`: this is compute-bound work, and parking it on the
blocking-IO pool makes it compete with the network call that follows it.

---

## 2026-08-19 — Structured outputs, not prefill, and not "please reply in JSON"

**Decision:** Every call sets `output_config.format` with a `json_schema`
(`RecipeSchemas`), and the DTOs mirror those schemas one-for-one.

**Why:** Three options existed and two are wrong. Asking for JSON in the prompt
gets JSON most of the time, and the failure mode is a parse error on the response
you most wanted. Prefilling an assistant turn with `{` — the classic trick —
**returns a 400 on Claude Opus 5 and the whole 4.6+ family**; assistant prefill
has been removed. `output_config.format` is the current mechanism and it
guarantees the response parses.

Consequence worth knowing: the `Unparseable` failure branch in `CulinaryLlmClient`
should now be unreachable. If it ever fires, the schema and the DTO have drifted
apart, which is why it surfaces the raw text rather than swallowing it.

**Every schema object sets `additionalProperties: false` with a complete
`required` list.** Optional-by-omission would let the model quietly drop the
field it was least confident about — which is precisely the interesting one. A
value it cannot determine comes back as an explicit null instead.

---

## 2026-08-19 — The schema guarantees shape; the Atwater check guards contents

**Decision:** Successful responses are still validated — protein and carbs at
4 kcal/g and fat at 9 should reconcile with the stated calorie figure within 15%
— and failures surface as `advisories` on a `Success`, not as errors.

**Why:** A schema cannot catch a response that is perfectly well-formed and
nutritionally nonsense, and that is a real failure mode: forgotten frying oil
makes a poori's grams and its calorie figure disagree by ~30%
(`MacroConsistencyTest` pins that case). Advisory rather than rejection because
the honest response to "these numbers don't add up" is to show the user, not to
throw away a recipe they asked for.

---

## 2026-08-19 — `claude-opus-5` as the default model

**Decision:** `AnthropicApi.DEFAULT_MODEL = "claude-opus-5"`, adaptive thinking,
`effort: "high"`, `fallbacks: "default"` with the
`server-side-fallback-2026-07-01` beta header.

**Why:** Macro estimation for Indian food is a reasoning problem, not a lookup —
absorbed frying oil, dry-vs-cooked basis and katori sizing all have to be
reasoned through, and it is the one place in the app where being wrong is
invisible to the user. Adaptive thinking is the only on-mode available
(`budget_tokens` is a 400 on this model); depth is controlled through `effort`.
Fallbacks are opt-in — without them a policy decline just stops the request.

The model is overridable in Settings; this is only what ships.

---

## 2026-08-19 — Provider enum is single-valued

**Decision:** `LlmProvider` has only `ANTHROPIC`. The OpenAI branch was removed.

**Why:** The original brief said "Anthropic/OpenAI", but a provider selectable in
Settings with nothing behind it is a stub that fails at runtime — which the
project's first rule forbids. The enum stays as the seam a second provider would
slot into, and the client is written against it, so adding one is additive rather
than a refactor.

---

## 2026-08-22 — Neo-brutalism replaced with warm dark editorial + earned gamification

**Decision:** The StarMatch brutalist theme was removed. `core/ui/brutalism/` is
deleted; `core/ui/components/` replaces it. New direction: a single warm-neutral
ramp, saffron as the one true accent, a subdued green reserved for achievement,
16dp default radius, sentence case throughout, dark-only.

**Why it failed on a phone.** StarMatch's brutalism works on a wide light
canvas. Ported to a small dark screen it broke in four specific ways:

1. **3dp full-strength white borders.** In dark mode a border suggests
   elevation; at full contrast it just draws a box around everything. Dark mode
   wants 8%-opacity hairlines.
2. **Hard offset shadows.** A drop shadow is nearly invisible on near-black, so
   the design compensated by making it hard, black and offset — which reads as a
   stack of stickers. Dark mode gets depth from a *lighter surface*, not shadow.
3. **Uppercase mono for every label.** Uppercase destroys word-shape
   recognition, so "WHAT'S IN MY DABBA" has to be read letter by letter. As a
   micro-label that's a texture; as the entire interface it is exhausting.
4. **Acid `#DDF247` as primary.** High-chroma yellow-green on near-black is the
   harshest pairing available, and it was doing the work of a food accent.

**The palette is split by meaning, not decoration.** Warm = food and nutrition;
green = progress and achievement. Two colour languages the user learns in a day
and then never needs a legend for. The macro triad (saffron / wheat / terracotta)
stays one warm family, separated by lightness and hue enough to survive red-green
colour deficiency.

**Dark only, deliberately.** A gym at 6am and a kitchen at 9pm. A derived light
mode would be a second, worse design maintained for nobody. If one is ever
wanted it gets designed, not generated.

---

## 2026-08-22 — The tactile button, and why it is rationed

**Decision:** `FmButton` puts a darker tone of its own fill 4dp beneath the face;
pressing drops the face onto it over 90ms. `FmButtonTonal` and `FmButtonGhost`
are flat.

**Why it is not the brutalist shadow returning.** The lip is a tonal shade of the
button's fill, on the same radius, with no border — one physical object with
thickness. The old version was a black rectangle offset behind a bordered box:
two flat shapes.

**Why only primary actions get it.** Per the delight-impact curve, a per-action
moment gets subtlety and no flourish. A screen where every control is tactile has
no hierarchy, and the effect stops registering by the third press. 90ms because
this fires hundreds of times a day.

---

## 2026-08-22 — Gamification is wired to the maths, not bolted on

**Decision:** A day counts toward the streak only with **at least two logged
meals** — `StreakEngine.MIN_MEALS_FOR_A_LOGGED_DAY`, the same threshold
`AdaptiveTdee` uses to decide a day is usable data.

**Why:** This is the whole justification for shipping a streak. A streak that
ticked on one photographed lunch would train exactly the inconsistent logging
that makes an adaptive TDEE estimate worthless — a number going up while the
product silently gets worse at its job. Aligning the bars means the game rewards
precisely the behaviour the engine needs.

Two related rules: the streak survives an unfinished today (yesterday counts, or
every user sees a broken streak before breakfast), and **no XP is awarded for a
bigger deficit or for under-eating.** XP tracks consistency, never severity —
rewarding severity in a cutting app is how a fitness tracker becomes harmful.

Milestones stay sparse (3, 7, 14, 30, 60, 100, 180, 365) because a rare moment
only carries a large delight budget while it stays rare.

---

## 2026-08-22 — Meters must show overshoot

**Decision:** `MacroRing` and `FmMeter` draw the excess above 100% in the error
colour on top of the completed ring, rather than capping the fill.

**Why:** A ring that silently caps at full hides the single most useful fact a
calorie tracker holds — that you went over. The track is always visible for the
same reason: an empty ring and a missing ring must not look alike, or "nothing
logged yet" reads as "zero progress".

---

## 2026-08-22 — Android's mobile design language, adopted structurally

**Decision:** The app now uses Material 3's own components (Scaffold,
NavigationBar, NavigationRail, LargeTopAppBar, FloatingActionButton, Badge,
HorizontalDivider) themed through the FitMasala ColorScheme, plus FitMasala
composites on top. Grounded in developer.android.com/design/ui/mobile.

**This reverses an earlier rule.** The brutalist theme banned `Card`, `Button`
and `OutlinedTextField` because their built-in chrome fought a 3dp hard border.
With the surface system now aligned to M3, that ban is backwards: M3's
components already carry the 80dp bar height, 48dp touch targets, state layers,
the pill indicator and TalkBack's selected-state announcement. Rebuilding those
by hand is how an app ends up looking Android-shaped and behaving wrong.

**What was actually missing was chrome.** The old screen was a naked scrolling
`Column` — no app bar, no navigation, no FAB. That single absence is what read
as "vibe coded" more than any colour choice: real Android apps have structure,
and a page of stacked cards is a web page in a WebView.

Specifics adopted:

- **8dp primary grid, 4dp secondary**, per the grids-and-units guidance. Tokens
  in `Fm` are named by ROLE (`gutter`, `gap`, `section`) not size, so the value
  can change without a find-and-replace.
- **Window size classes.** Compact gets a bottom navigation bar; medium and
  expanded get a navigation rail. Read from the window, so unfolding swaps them
  live. Margins 16dp compact / 24dp wider; content stops widening at 640dp.
- **Canonical layouts.** The dashboard is a Feed (`LazyColumn` — the meal list is
  unbounded, and a plain Column composes rows that are off screen). List-detail
  and supporting-pane follow for recipes and quick actions.
- **Large collapsing top app bar** via `exitUntilCollapsedScrollBehavior`, with a
  transparent container that only gains a surface once content scrolls under it.
- **Insets consumed exactly once.** Scaffold applies system-bar padding, then
  `consumeWindowInsets` stops children reapplying it. Double-applied insets look
  merely roomy on the developer's phone and clip on a device with a bigger cutout.
- **`AutoMirrored` back arrow**, which flips in right-to-left locales.
- **`defaultMinSize` on list rows, never a fixed height** — a hard height clips
  text once the user scales their font up, and nobody on default settings sees it.

**Four navigation destinations, and Settings is not one of them.** M3 allows
three to five; past five, labels truncate and targets fall under 48dp. A screen
visited twice a year does not earn a permanent quarter of the bottom bar — it
lives in the app bar.

---

## 2026-08-22 — Edge-to-edge done properly (three real bugs fixed)

**Context:** targetSdk 35 means Android 15 enforces edge-to-edge; the opt-out is
gone. Audited against developer.android.com/design/ui/mobile/guides/foundations/system-bars.

**Bug 1 — content was clipped above the navigation bar, not scrolling under it.**
The shell applied `Modifier.padding(innerPadding)` to the content Box. That is
the opposite of edge-to-edge: it stops the list dead above the bar and leaves a
strip of background exactly where the design intends content passing under
glass. Bar insets belong in a scrolling container's `contentPadding`, so the
first and last items clear the bars while the list scrolls beneath them.

**Bug 2 — double bottom padding.** `FmBottomSpacer` added a hardcoded
`navBarHeight + block` (116dp) on top of Scaffold's own bottom inset, so the
dashboard carried nearly 200dp of dead space. The hardcoded 80dp was wrong
anyway: gesture navigation and 3-button navigation are different heights, and
neither is knowable at compile time. Deleted; the real inset is read at runtime.

**Bug 3 — the transparent navigation bar was not transparent.** Android paints a
translucent contrast scrim behind 3-button navigation unless
`window.isNavigationBarContrastEnforced = false` is set. Correct here because the
app's own NavigationBar fills that region; a screen where content scrolls
directly under the system bar should keep the scrim instead.

**Added, per the guide:**

- **Gradient bar protection** (`FmSystemBars`). Status icons are drawn by the
  system over whatever is beneath them, and over a bright card they become
  unreadable. A gradient from background to transparent keeps edge-to-edge while
  guaranteeing contrast; a flat scrim would draw a hard line across the screen.
- **Display cutout in the horizontal padding.** In landscape a camera cutout eats
  into the side of the window. Invisible in portrait, which is where it ships.
- **`imePadding()`** so content follows the keyboard's animation rather than
  snapping after it settles.
- **Predictive back**: `android:enableOnBackInvokedCallback="true"` plus
  `BackHandler`s that unwind transient UI (the FAB menu) before navigation, and
  navigation before leaving the app. Without the manifest flag the system's
  predictive animations never run, however well the app is written.

**Note for future sessions:** `android skills add --skill edge-to-edge` is a real
command referenced in Google's system-bars guide. It needs the Android
`cmdline-tools`, which are not installed on this machine — the recommendations
above were implemented by hand instead.

---

## 2026-08-22 — Light mode restored; full M3 tonal system adopted

**Decision reversed.** The 2026-08-22 "dark only, deliberately" entry is
superseded. The app now ships designed light AND dark schemes and follows the
system preference.

**Why the reversal.** The product argument for dark-only was real — a gym at 6am,
a kitchen at 9pm. Material's accessibility argument outranks it: forcing a theme
"can go against a user's accessibility and personalization needs", and
light-on-dark halates badly for people with astigmatism, which is common. A
preference the user has already expressed at the OS level should not be overridden
by an app's aesthetic preference.

**Tonal palettes, not hand-picked role colours.** `Tones.kt` defines five ramps
(saffron, green, terracotta, warm neutral, neutral variant) from tone 0 to 100.
Roles are tone POSITIONS: light primary is P40 with a P90 container, dark primary
is P80 with a P30 container. Because both themes draw from the same ramp they read
as one brand, rather than two palettes that happen to share a name — which is what
hand-picking each role separately produces.

**Every role is filled, including ones this app never names.** Material's own
components reach for `inverseSurface` (snackbars), `surfaceBright`, `surfaceDim`
and all five container levels. Leaving them at their defaults is how a carefully
themed app suddenly shows a stock purple snackbar.

**Light is designed, not derived.** Its hairlines are darker *and* more opaque
than the dark theme's: 8% white reads clearly on near-black, while 8% black on
near-white is invisible. Flipping alpha is the standard way a light theme ends up
looking unfinished. Macro chart colours have separate light values around tone
45–50 for the same reason.

**Launch window follows the theme** via `values/` and `values-night/` variants,
so a cold start under the dark scheme does not flash white. `SystemBarStyle.auto`
flips system-bar icon polarity with the theme; pinning it to `.dark` would have
left white icons invisible on the light scheme.

**Dynamic colour remains declined** — the single departure from Material's
recommendation. Saffron, green and the macro triad carry information here, not
decoration: "warm means food, green means progress" is the whole legend, and the
macro ring is glanceable only because protein is always the same colour.
Wallpaper-derived colour would repaint a system the user has already learned.

---

## 2026-08-22 — Theme choice belongs to the user, in Settings

**Decision:** `ThemeMode` (SYSTEM / LIGHT / DARK) persisted in DataStore,
surfaced as a radio group in Settings, read in `MainActivity` before any screen
composes.

**Radio group, not a switch.** A switch has two states; the theme has three.
Collapsing "follow the system" into an implicit third state is how apps end up
unable to return to it once the user has touched the toggle.

**Hoisted to the Activity.** The theme has to be known before any screen exists,
so a ViewModel scoped to the settings route would mean the app could not know its
own theme until the user navigated there. `WhileSubscribed(5_000)` keeps the flow
alive across rotation instead of flashing the default for a frame.

---

## 2026-08-22 — Two bugs only on-device testing found

Both were invisible in code review and in Compose previews.

**Selecting a navigation destination did not dismiss Settings.** `showSettings`
was never cleared, so tapping "Today" appeared to do nothing and the navigation
bar looked dead. Fixed in both the bar and the rail.

**The FAB was physically covering a switch in Settings.** A FAB is a screen's
primary action, and Settings has none — it should not have been rendered there at
all.

The lesson worth keeping: a Compose preview renders one screen in isolation. Bugs
in how screens *relate* — navigation state, floating chrome overlapping content —
only appear on a device.

---

## 2026-08-22 — Quick-commerce interactions, not quick-commerce looks

**Context:** A FoodGrid delivery UI kit and Blinkit were given as inspiration,
with the explicit constraint that the app must stay unique and must NOT look like
a delivery app.

**Taken — the interaction model:**

- `FmQuickAdd` — an ADD button that becomes a stepper. Adding six things must
  cost six taps, not six navigations. It fits food logging better than it fits a
  basket: portions are naturally counted (two rotis, one katori), so the stepper
  maps onto the real unit rather than an abstract quantity.
- `FmStickyLogBar` — the "N items, view cart" bar, showing kcal and PROTEIN
  instead of a price. A basket bar shows a number the shopper already knows; this
  shows the one they cannot compute in their head, and the one that decides
  whether they log the fourth roti.
- `FmInsightStrip` — the equivalent of "delivery in 10 minutes". The claim here is
  not speed but continuity: what the app knows that the user would otherwise work
  out ("790 left, with dinner still to log").

**Refused — the visual furniture.** No food photography, no rating rows, no
promotional banner, no storefront search-as-hero. `FmCategoryRail` was built and
then deleted: a horizontal rail of circular cuisine icons is the single strongest
delivery-app tell, and region filtering already has `FmChip`, which reads as an
app control rather than a shopfront.

**Also refused: recolouring to the kit's `#F4803B`.** Matching a food-delivery
template's exact brand hex would make the app look MORE derivative, not more
original. The saffron ramp already sits in the same warm family and carries the
macro semantics.

**Built instead — the thing that makes the app itself.** `FmPortionPicker`: a
portion control in Indian units. Every other tracker asks for grams; nobody
weighs a katori of dal. People think "one katori", "two rotis", and forcing a
gram conversion at the point of logging is precisely the friction that stops
people logging. Grams stay available, last in the list rather than first. The
live macro readout is the load-bearing part - it turns portion choice from data
entry into a decision, because seeing the third roti cost 120 kcal before
committing is the whole value.

**Standing risk:** this is the third design language in three turns (brutalist,
M3 warm dark, delivery-kit inspiration). Rewrites are cheap in code and expensive
in coherence. Prefer additive changes to the component set over another
wholesale re-theme.

---

## 2026-08-22 — DM Sans bundled as a variable font

**Decision:** DM Sans ships in the APK as a single 240KB variable font
(`res/font/dm_sans.ttf`), with weights reached through `FontVariation.Settings`.
Licence at `assets/licenses/dm-sans-OFL.txt`.

**Why bundled, not downloadable.** A downloadable font needs Play Services, fails
SILENTLY to a system fallback when unavailable, and makes the app's typography
depend on a network. 240KB is a fair price for it always being right.

**Why variable.** Google Fonts no longer ships static instances of DM Sans — the
family is one file with optical-size and weight axes. Five weights therefore cost
one file, and the display styles can run a larger `opsz` (40 vs 14), which
tightens spacing and thins strokes at headline sizes. That optical adjustment is
most of why big type stops looking clumsy.

`FontVariation` needs API 26 — this app's minSdk. `FontVariation.Setting` (the
raw-axis form) is still `@ExperimentalTextApi`.

---

## 2026-08-22 — Motion that carries information, and the chart bug it exposed

**Added:** `animatedCount` (headline numbers count up), `enterFromBelow`
(staggered list entry), `fadeInOnce`.

Each earns its place by carrying information a static version loses: a counting
number shows the value ARRIVING rather than simply being; a staggered list shows
ORDER. `animatedCount` scales its duration to the size of the change, so 0→1,590
reads as an arrival while 1,590→1,610 is nearly instant — a fixed duration makes
small updates sluggish and large ones abrupt. The stagger caps at eight items,
past which a list animating in sequence is a loading screen pretending to be a
list.

`enterFromBelow` animates through `graphicsLayer`, not `offset`. Translation and
alpha composite on the render thread and skip layout entirely; animating offset
would re-measure the whole list every frame.

**`FmTrendChart` — the creative centrepiece, and it is function-first.** Raw
weigh-ins as a scatter, the smoothed trend as a line, the goal as a dashed rule.
This is the app's central argument as a picture: the dots jump, the line does
not, and only the line drives the plan. It uses the SAME `WeightTrend` smoothing
the engine uses — a chart that smoothed differently from the maths would be a lie
about how the app works. Drawn on Canvas rather than via a charting library: two
paths and a scatter do not justify a dependency, a theme to fight, and a default
look shared with every other app using it.

**The bug on-device testing caught:** forcing the goal into the y-range squashed
three kilos of real movement into a ten-kilo axis, so the line read as FLAT while
the card above it said "0.6 kg per week". The chart was contradicting its own
headline. Now the scale follows the data, and a goal outside the visible range
gets an honest "(below)" in the legend instead of distorting everything.
