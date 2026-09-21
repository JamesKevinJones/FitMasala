# Brag Plan: FitMasala

## What is this app?
A local-first native Android app, built for one person's own cut: an AI Indian
culinary assistant that reasons in katori/roti instead of grams, wired to an
adaptive body-recomposition engine that back-calculates real maintenance
calories from what actually happened — not what got logged — plus a
progressive-overload workout tracker. No backend, no account, no analytics;
the only network call is the user's own key hitting the LLM directly.

## The angle
This isn't a startup pitch — it's a personal engineering flex, and the video
should read that way. The premise: most calorie trackers either force Indian
food into grams and a generic macro guess, or trust whatever you log at face
value and drift when you under-report. FitMasala does neither — it reasons in
the units people actually cook in, and its planner is built to still land in
the right place even when logging is consistently a little off. The hook is
craft and precision, not hype.

## Hook (first 2-3 seconds)
Near-black screen. The FitMasala wordmark settles into frame in DM Sans
Display, full scale, saffron-on-near-black. One quiet line underneath:
"Built for one cut. Nobody else's."

## Key moments (the middle)
- The dashboard's macro rings filling toward today's targets while the streak
  flame visibly warms a tier (grey → amber → terracotta) as the day count
  ticks up — gamification that is visibly tied to real data, not a bolted-on
  counter.
- A logged meal resolving into Indian units — "2 roti · 1 katori dal · 1
  katori sabzi" — with a small "100% on-device · IFCT-calibrated" badge,
  proving the nutrition reasoning isn't a US-database guess running in the
  cloud.
- The weight-trend line drawing itself on the Plan screen while the
  maintenance-calorie number recalculates live (2,430 → 2,290 kcal) — the
  adaptive engine visibly updating itself from the trend, not from a formula
  typed in once at onboarding.

## Outro / punchline
Cut to black. Wordmark returns, small and settled. One line: "No backend. No
account. Just your phone." Hold. Silence.

## User flow worth showing
Entry → key action → result, pulled straight from the real screens:
1. Dashboard: today's macro rings + streak, the expandable FAB offering "Snap
   a meal" / "Ask the chef" / "Start a workout."
2. Chef: a snapped/asked meal resolves into real Indian portions, not grams.
3. Plan: the adaptive engine redraws the weight trend and the maintenance
   number moves with it.

## Tone
- Preset: polished
- Creative direction: a quiet, confident personal-engineering flex — treated
  like a premium product film, not a startup pitch. The craft (tonal color
  system, Indian-unit reasoning, an adaptive engine that doesn't flinch at
  noisy data) is the whole claim; it doesn't need to shout.
- Interpretation: slow, settled reveals with real holds on every line; no
  aggressive cuts or oversized type; restraint is the visual argument for how
  carefully this was built.

## Format: landscape — 1920x1080
## Duration: ~22s

## Visual identity (from the project)
- Background: `#100F0D` (Tones.kt `N6`, the dark-theme background/surface)
- Accent (primary, dark theme): `#FFB95C` (Tones.kt `P80`, saffron)
- Accent (primary, light theme, for any light-surface card): `#875300` (`P40`)
- Macro triad: protein `#F4A43C`, carbs `#E8C86A`, fat `#D96F4E` (Color.kt
  dark-mode macro set)
- Streak flame tiers: cold `#8A8279` → warm `#F4A43C` → hot `#E06A52`
  (Color.kt streak tiers — the flame should visibly step through these, not
  just recolor once)
- Text (on dark bg): `#E7E2DB` (Tones.kt `N90`)
- Display font: DM Sans (variable, opsz 40, weights 600-800) — available on
  Google Fonts as "DM Sans"
- Body font: DM Sans (opsz 14, weights 400-700) — same family, lower optical
  size
- Strongest visual element: the tonal ramp itself (near-black surface, warm
  saffron accents, a streak flame that literally warms as it's earned) — this
  is a designed color *system*, not a picked palette, and the video should
  show it behaving like one (rings filling, flame stepping tiers, chart
  redrawing) rather than a static screenshot.

## Share copy (draft)
Built an Android app that reasons in katori and roti instead of grams, and
doesn't flinch when I under-log a meal. Local-first, no backend, no account —
just my own cut. FitMasala.

## Audio direction
- Role: warm, restrained bed with minimal, tasteful accents — nothing
  corporate-upbeat, nothing that competes with the reveals.
- Music: `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` ("Steady and
  clean," ~110 BPM) — the bundled track the skill's own tone table recommends
  for `polished`.
- Music treatment: starts at 0 under the hook at low volume (~0.30-0.35),
  stays under throughout, no big swell — a steady bed the visuals sit on top
  of, gentle fade on the final hold.
- Music cue guidance: preset read from
  `happy-beats-business-moves-vol-12-by-ende-dot-app.music-cues.md`
  (duration 117.36s, ~109.96 BPM). Target strong cues for the two internal
  transitions: **8.74s** (0.99, strong_beat) for Dashboard→Chef, and
  **18.56s** (0.99, strong_beat) for Plan→Outro. Chef→Plan transition aligns
  to a regular beat-grid point at **13.64s** rather than a strong cue, to
  avoid over-punctuating a polished edit with three hard accents in a row.
- Audio-reactive treatment: subtle — the streak flame's glow and the macro
  rings' presence may breathe slightly with RMS; no waveform/equalizer
  visuals, no strobing.
- SFX posture: minimal but present, 2-3 cues total per the tone table's
  `polished` guidance (soft `interface/drop_*`-family pop for the macro rings
  settling, one `interface/bong_001`-family soft accent on the streak tier
  stepping up, nothing on the outro but the held silence). Hyperframes picks
  exact files from `sfx-analysis.md` at composition time.
- Audio-coupled moments: FAB labels revealing in rapid succession then
  holding as a group; the Indian-unit portion list items arriving one by one;
  the trend line drawing itself; the maintenance number ticking down.
- Restraint rule: never more than one SFX per beat, nothing on text entrances
  that are already carrying the scene's meaning (the wordmark, the outro
  line) — let those land in silence.

## Storyboard

### Scene 1 — Hook — 3.5s
Near-black background (`#100F0D`). "FitMasala" wordmark (DM Sans Display,
700-800 weight, saffron `#FFB95C`) settles into center frame from a slight
scale/fade, full scale. At 1.8s, one line fades up beneath it in `#E7E2DB`:
"Built for one cut. Nobody else's."
Sequential/interaction: none — single settle, single line-fade.
Audio intent: quiet confidence, no fanfare on the name itself.
Audio-coupled idea: none — the wordmark and line land in near-silence under
the music bed, no SFX.
Music: bed starts at 0, low (~0.30).
Transition mood: soft crossfade → Scene 2

### Scene 2 — Dashboard reveal — 5.24s (3.5s → 8.74s)
Recreate the real Dashboard: an "Eaten today" section header, three macro
rings (protein `#F4A43C`, carbs `#E8C86A`, fat `#D96F4E`) filling from 0
toward today's targets over ~1.5s, numbers counting up alongside. To the
side, a streak counter ticks 1→12 while the flame icon visibly steps through
its tiers (`#8A8279` cold → `#F4A43C` warm → `#E06A52` hot) in sync with the
count, landing hot. In the last ~1.5s, the expandable FAB opens: three labels
— "Snap a meal," "Ask the chef," "Start a workout" — reveal in rapid
succession (~0.25s apart) then hold together as a settled group through the
scene's end.
Sequential/interaction: yes — macro rings fill together, streak flame steps
tier by tier with the count, then the 3 FAB labels reveal fast and hold as a
group (per the sequential-text hold rule — reveal fast, then keep the full
set legible).
Audio intent: the data coming alive — busy but composed, not chaotic.
Audio-coupled idea: a soft pop/drop as the rings finish filling; a single
warm accent as the flame steps to its hottest tier.
Music: bed continues, steady.
Transition mood: soft crossfade, timed to the 8.74s strong beat → Scene 3

### Scene 3 — Chef highlight — 4.90s (8.74s → 13.64s)
A "Snap meal" moment resolves: a small card/badge reading "100% on-device ·
IFCT-calibrated" fades in first (establishing this runs locally, tuned to
Indian food composition data, not a generic US database), then three portion
lines reveal one by one and hold as a set: "2 roti," "1 katori dal," "1
katori sabzi" — Indian units throughout, no grams anywhere on screen.
Sequential/interaction: yes — badge first, then the 3 portion lines reveal in
quick succession and hold together for the remainder of the scene.
Audio intent: precise, deliberate — each portion line landing with a small
confirming tick.
Audio-coupled idea: soft interface tick on each portion line's arrival
(sparse — accent the first and last, not all three equally, per the timing
rules).
Music: bed continues, steady.
Transition mood: soft crossfade, timed near the 13.64s beat-grid point →
Scene 4

### Scene 4 — Plan highlight — 4.92s (13.64s → 18.56s)
The Plan screen's weight-trend line draws itself left to right over ~2s
against the near-black surface. As it draws, a "Maintenance" figure ticks
down from 2,430 kcal to 2,290 kcal beside it. Once the line and number
settle (~2.5s remaining), a single line holds beneath: "Adapts to what
happened. Not what you logged."
Sequential/interaction: yes — the trend line draws progressively; the
maintenance number counts down in step with the draw, not before or after
it.
Audio intent: a quiet "this is the smart part" beat — let the drawing line
carry it, minimal sound.
Audio-coupled idea: a very soft, continuous tick or whoosh matched to the
line's draw speed, nothing per-frame.
Music: bed continues, steady, no swell yet.
Transition mood: soft crossfade, timed to the 18.56s strong beat → Scene 5

### Scene 5 — Outro — 3.54s (18.56s → 22.10s)
Cut to full black. The FitMasala wordmark returns, smaller and settled, no
motion beyond a gentle fade-in. Beneath it: "No backend. No account. Just
your phone." Hold on the full frame through the end; music bed fades out
under the hold rather than cutting.
Sequential/interaction: none — single fade-in, then a held frame.
Audio intent: settle, don't sting. The confidence is in the silence, not a
sting.
Audio-coupled idea: none — no SFX on the outro; let it land in the fading
bed.
Music: fades out across the hold.
Transition mood: n/a (final scene)

**Music mood for this video:** polished / steady-and-clean, ~110 BPM, low in
the mix throughout.
**Audio summary:** One restrained instrumental bed under the entire video,
fading in with the hook and out with the outro hold, with two to three soft
SFX accents (ring-fill pop, streak-tier step, portion-line ticks) placed to
reinforce data arriving on screen — never louder than the visuals, never
present on the wordmark or the closing line.
