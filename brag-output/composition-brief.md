# Hyperframes Composition Brief: FitMasala

## Objective
Create a short launch-style brag video for FitMasala — a local-first native
Android app combining an Indian-unit culinary AI, an adaptive cut/body-
recomposition planner, and a progressive-overload workout tracker, built for
one person's own cut.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: ~22 seconds (15-25s range)

## Source Material
- Project root: `C:\Users\kj638\Kevin codes\FitMasala`
- Primary files read: `README.md`, `AGENTS.md`, `docs/STATE.md`,
  `app/src/main/java/com/kevinjones/fitmasala/core/ui/theme/Tones.kt`,
  `Color.kt`, `Theme.kt`, `Fonts.kt`, and the real screen source
  (`presentation/dashboard/DashboardScreen.kt`,
  `presentation/chef/ChefScreen.kt`, `presentation/plan/PlanScreen.kt`) for
  actual on-screen copy.
- This is a native Android app with no web frontend or existing
  screenshots/screen recordings on disk — there is nothing to literally
  crop or capture. The composition must recreate the real screens in
  HTML/CSS using the project's actual design tokens (exact hex values,
  actual DM Sans font, actual copy) rather than inventing a generic app
  mockup. Accuracy to the real tonal system matters more than decoration.
- Product name: FitMasala
- Tagline / strongest claim: "Built for one cut. Nobody else's." (hook) /
  "No backend. No account. Just your phone." (outro). Strongest technical
  claim to visualize: the app reasons in Indian portion units (katori, roti)
  instead of grams, runs an on-device nutrition engine calibrated against
  Indian Food Composition Tables (IFCT/NIN Hyderabad), and its adaptive
  planner back-calculates real maintenance calories from the observed weight
  trend so a consistent logging bias cancels out rather than stalling the
  plan.
- Key UI or visual moment to recreate: the Dashboard's three macro rings +
  streak flame that steps through color tiers as it's earned; the Chef
  screen's Indian-unit portion resolution; the Plan screen's self-drawing
  weight-trend line with the maintenance number recalculating live.
- Copy that must appear verbatim:
  - "FitMasala"
  - "Built for one cut. Nobody else's."
  - "Eaten today"
  - "Snap a meal", "Ask the chef", "Start a workout"
  - "2 roti", "1 katori dal", "1 katori sabzi"
  - "100% on-device · IFCT-calibrated"
  - "Adapts to what happened. Not what you logged."
  - "No backend. No account. Just your phone."

## Creative Direction
- Tone preset: polished
- Creative direction: a quiet, confident personal-engineering flex — a
  premium product film, not a startup pitch. This is a real, working,
  local-first app one person built and is proud of; the craft (a designed
  tonal color system, Indian-unit reasoning instead of forced grams, an
  adaptive engine that self-corrects from noisy data) is the entire claim.
  It doesn't need to shout to make its case.
- Interpretation: slow, settled reveals; every line gets a real hold before
  it exits; no aggressive cuts, no oversized/all-caps type, restraint
  throughout. Confidence through space, not volume.
- Angle: most calorie trackers either force Indian food into grams and a
  generic macro guess, or trust logged intake at face value and drift when
  logging is inconsistent. FitMasala does neither — full detail in
  `brag-plan.md` → "The angle."
- Hook: near-black screen, FitMasala wordmark settles into frame, then
  "Built for one cut. Nobody else's." fades up beneath it.
- Outro / punchline: cut to black, wordmark returns small and settled, "No
  backend. No account. Just your phone." holds, music fades under the hold.
- Avoid:
  - Generic SaaS/fitness-app language ("streamline your workflow," "crush
    your goals")
  - Delivery-app visual furniture — no food photography, star ratings, promo
    banners, circular category rails, or search-as-hero (this is an explicit
    house rule in the project's own `AGENTS.md`: "This is not a delivery
    app")
  - Abstract filler visuals unrelated to the actual screens
  - Grams anywhere on screen — the whole point is Indian units
  - A generic dark-mode app mockup that doesn't use the project's actual
    tone ramp / macro triad / streak-tier colors

## Visual Identity
- Background: `#100F0D` (dark-theme surface, Tones.kt `N6`)
- Text on dark background: `#E7E2DB` (Tones.kt `N90`)
- Primary accent (dark theme): `#FFB95C` (Tones.kt `P80`, saffron)
- Macro triad (fixed, not decorative — protein/carbs/fat read by color
  app-wide): protein `#F4A43C`, carbs `#E8C86A`, fat `#D96F4E`
- Streak flame tiers (must visibly step, not just recolor once): cold
  `#8A8279` → warm `#F4A43C` → hot `#E06A52`
- Display font: "DM Sans" (Google Fonts), weight 700-800, used at a larger
  optical size for headline/wordmark moments
- Body font: "DM Sans" (Google Fonts), weight 400-600, for section headers,
  labels, portion lines
- Visual references from the project: the tonal-ramp system in
  `core/ui/theme/Tones.kt` (5 palettes × tone positions, not a hand-picked
  palette), the macro/streak color split documented in `AGENTS.md` ("Warm =
  food, green = progress" — note green is reserved for progress/streak
  elsewhere in the real app, but the streak flame itself is the
  warm-tier ramp above; don't introduce a green not present in this
  storyboard's actual elements), the near-black + warm-accent surface
  treatment used throughout the real dark theme.

## Storyboard
Use the storyboard in `brag-plan.md` as the creative contract — full detail,
per-scene audio-coupled ideas, and beat-lock targets are there. Summary:

1. **Hook** — 3.5s — FitMasala wordmark settles in on near-black; "Built for
   one cut. Nobody else's." fades up beneath it. No SFX, bed starts low.
2. **Dashboard reveal** — 5.24s (3.5s→8.74s) — "Eaten today" header; 3 macro
   rings fill with counting numbers; streak counter ticks 1→12 while the
   flame steps cold→warm→hot in sync; FAB opens revealing "Snap a meal" /
   "Ask the chef" / "Start a workout" in rapid succession, then holds as a
   group. Transition beat-locked to **8.74s** (strong cue, intensity 0.99).
3. **Chef highlight** — 4.90s (8.74s→13.64s) — "100% on-device ·
   IFCT-calibrated" badge fades in, then "2 roti" / "1 katori dal" / "1
   katori sabzi" reveal in quick succession and hold as a set. Transition
   near **13.64s** (regular beat-grid point, not a strong cue — keep this
   one softer than the other two).
4. **Plan highlight** — 4.92s (13.64s→18.56s) — weight-trend line draws
   itself over ~2s; "Maintenance" figure counts down 2,430→2,290 kcal in
   step with the draw; "Adapts to what happened. Not what you logged." holds
   for the remainder. Transition beat-locked to **18.56s** (strong cue,
   intensity 0.99).
5. **Outro** — 3.54s (18.56s→22.10s) — cut to black; wordmark returns small;
   "No backend. No account. Just your phone." holds through the end; music
   fades out under the hold. No SFX.

## Audio
- Audio role: warm, restrained instrumental bed with minimal SFX accents —
  nothing corporate-upbeat, nothing competing with the reveals.
- Audio arc: bed fades in under the hook at low volume (~0.30-0.35), holds
  steady under scenes 2-4, fades out under the outro's final hold. 2-3 soft
  SFX total, placed only where data is visibly arriving (ring fill, streak
  tier step, portion-line ticks) — none on the wordmark or the closing line.
- Music: `assets/music/happy-beats-business-moves-vol-12-by-ende-dot-app.mp3`
  (already copied into this composition's asset folder) — "Steady and
  clean," ~110 BPM, the bundled track the skill's own tone table recommends
  for `polished`.
- Music treatment: start at 0, volume ~0.30-0.35 throughout (never above
  0.5), fade out across the final ~1.5-2s of the outro hold rather than
  cutting.
- Music cue guidance: bundled preset at
  `assets/music/cues/happy-beats-business-moves-vol-12-by-ende-dot-app.music-cues.json`
  (and the paired `.md` summary alongside it) — duration 117.36s, tempo
  ~109.96 BPM. Strong cues to target: **8.74s** (0.99) and **18.56s**
  (0.99). A regular beat-grid point near **13.64s** for the third,
  intentionally-softer transition. Use these as timing hints, not hard
  locks — shift up to ±0.15s for major reveals, ±0.10s for smaller
  entrances, and ignore them if they hurt readability or pacing.
- Audio-reactive treatment: subtle — the streak flame's glow and/or the
  macro rings' presence may breathe slightly with music RMS/bass. No
  waveform/equalizer visuals, no strobing, no text scaling tied to audio.
- Audio-coupled moments:
  - Scene 2 FAB labels — reveal fast (~0.25s apart), then hold as a settled
    group rather than snapping every label to a beat (they're text the
    viewer must read).
  - Scene 2 streak flame tier step — align the "hot" tier landing near a
    beat-grid point if it doesn't fight the ring-fill timing.
  - Scene 3 portion lines — quick sequential reveal, accent first and last
    line's arrival with sound, not all three equally.
  - Scene 4 trend line draw + maintenance count-down — continuous, not
    per-frame; one soft sound matched to the draw's motion, not its length.
- SFX selection guidance: soft `interface/drop_*`-family pop for the macro
  rings settling; one `interface/bong_001`-family soft accent for the streak
  flame reaching its hottest tier; soft `interface/click_*` or
  `ui/rollover_*`-family ticks (low frequency-risk) for the Chef portion
  lines. Nothing sharp, nothing stacked — 2-3 total cues across the whole
  video, per the `polished` tone-table guidance in `audio.md`.
- SFX analysis guidance: read `<brag-skill-dir>/assets/sfx/sfx-analysis.md`
  before final selection; prefer low/medium high-frequency-risk files since
  these are repeated, polished moments, not isolated chaotic accents.
- Exact SFX choice: Hyperframes chooses exact filenames, timestamps,
  density, and volume once the animation timing is implemented — copy
  chosen SFX files into `assets/sfx/...` under this composition directory.
- Audio files: music is already at
  `brag-output/composition/assets/music/happy-beats-business-moves-vol-12-by-ende-dot-app.mp3`;
  add any selected SFX under `brag-output/composition/assets/sfx/`.

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core`
(composition contract + `data-*` timing), `hyperframes-animation` (motion),
`hyperframes-creative` (design spec, beats, audio-reactive),
`hyperframes-keyframes` (seek-safe keyframes), and `hyperframes-cli`
(lint/check/render). `/brag` is its own workflow: do not enter the
`hyperframes` entry-point intent interview and do not route into its generic
promo/launch-video workflow. Prefer native Hyperframes conventions over
anything in `/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from the source project
  (all five scenes do — this is a UI-recreation-heavy brief since there are
  no existing screenshots to source from).
- Keep all text readable in the final render — respect the reading-time
  floors already baked into the storyboard's scene durations.
- Keep the video within 15-25 seconds (targeting ~22.1s).
- Include the planned music/SFX layer — audio was not disabled.
- Treat `/brag` audio notes as guidance, not a fixed cue sheet. Choose exact
  SFX after the visual animation exists.
- Treat music cue metadata as optional timing hints, not hard requirements.
- Major reveals may move toward nearby strong cues within ~0.15s; smaller
  entrances may align to nearby beat points within ~0.10s. Only 1-3 strong
  cue locks in this 15-25s video (2 are planned: 8.74s, 18.56s).
- Use SFX to support motion and interaction: soft drop/pop sounds for the
  ring fill and FAB reveal, a single soft bell/accent for the streak's
  hottest tier, soft ticks for the portion-line reveal, restraint everywhere
  else.
- Honor the planned music treatment: fade-in under the hook, steady bed
  under scenes 2-4, fade-out under the outro hold.
- Consider the Hyperframes audio-reactive workflow for a subtle glow/presence
  response on the streak flame and/or macro rings — avoid waveform/
  equalizer visuals or strobing.
- Use local assets (the music file already copied in; any SFX Hyperframes
  selects) rather than remote URLs.
- Run `hyperframes check` before render — it is brag's single gate.
