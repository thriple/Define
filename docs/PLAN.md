# DEFINE — Plan

A loud, always-moving Android app that turns short arm/chest workouts into levelling up a superhero
you can't stop coming back to. The training side is deliberately conservative (a 40-something body heals slowly); the
reward side is deliberately excessive (a 40-something brain still loves a surprise).

---

## 1. The training program

### Principles
- **Consistency over intensity.** The thing being trained is showing up. Every day has a mission,
  and every mission is short (8–12 min).
- **Recovery is built into the rotation**, not something you're punished for.
- **Progress only when the body says yes.** A step up happens only after a session that felt
  manageable *and* a soreness check-in that came back low.
- **No rewards for overdoing it.** Bonus reps are capped at +2 per set. The rewards can never
  tempt you into an injury.

### Three-day rotation (each muscle group gets ~72 h to recover)

| Day | Mission | Exercises |
|---|---|---|
| A | **PUSH** | Push-ups · Dumbbell overhead press · Overhead triceps extension |
| B | **PULL** | Dumbbell curls · One-arm dumbbell row · Prone Y-T-W raises (bodyweight, rear delts/upper back) |
| C | **RECHARGE** | 5-min mobility: doorway pec stretch, wall slides, wrist/elbow circles, dead hang or band pull-aparts if available |

Push opposes Pull (chest/front delts/triceps vs back/rear delts/biceps). Swimming already loads lats
and shoulders, so Pull days stay modest. RECHARGE counts for the streak and pays rewards like any
other day.

**Soreness gate:** before each session, a 2-second check-in (tap a body map: arms / chest /
shoulders / back, 1–5). If the target area is still ≥ 4, the app swaps today to RECHARGE and pushes
the mission to tomorrow. No penalty.

### Starting points → milestones → the endgame

3 sets per exercise, ~60–90 s rest. Weights assume dumbbells; the ladder past 30 lb needs heavier
weights (see Open questions).

| Exercise | Start | Milestones | Endgame ("Prime" boss) |
|---|---|---|---|
| Push-ups | 3 × 6 | 3×10 → 3×15 → 3×20 | **40 unbroken**, and **100 total in one session** |
| Curls | 3 × 3 @ 30 lb | 3×10 @ 30 → 3×12 @ 30 → 3×6 @ 35 → 3×10 @ 35 | **3 × 8 strict @ 40 lb** |
| Overhead press | 3 × 3 @ 30 lb | 3×8 @ 30 → 3×12 @ 30 | **3 × 8 @ 35 lb** |
| One-arm row | 3 × 6 @ 30 lb | 3×12 @ 30 → 3×8 @ 40 | **3 × 10 @ 40 lb** (each arm) |
| Triceps extension | 3 × 5 @ 30 lb (two hands) | 3×10 → 3×12 | **3 × 12 @ 35 lb** |
| Y-T-W raises | 2 × 6 | 3×8 → 3×12 | **3 × 12 with 2.5 lb plates** |

These endgames sit comfortably in "strong for 40–49" territory without chasing what a 28-year-old
did. Hitting them is the "campaign complete" moment; after that the app shifts to maintenance and
prestige.

### Progression rules (the engine)
- After a session, a one-tap effort rating: **Easy / Solid / Brutal / Couldn't finish**.
- **Step up** (+1 rep per set; at the rep ceiling, next weight and drop reps) when: all reps done,
  effort ≤ Solid, and the next check-in for that area ≤ 2.
- **Hold** when effort was Brutal or soreness was 3.
- **Step back one** when a set wasn't finished or soreness hit 5.
- **Deload every 5th week:** volume −40%, full rewards ("Recharge Week" with its own loot table).
- **Missed 4+ days:** resume one step back, with a "Welcome Back" supply pod, never a guilt screen.
- **Max tests** every 4 weeks for push-ups are staged as villain fights (boss health bar = target reps).

Rough timeline: ~2 sessions per group per week puts curls at 3×10 in about 5 weeks and the full
endgame at roughly 5–7 months. The engine adapts to real data, so this is a forecast, not a promise.

---

## 2. Reminders and time windows

| Window | Times | Nudges |
|---|---|---|
| Morning | 7:00–7:50 | 7:00 "Mission is live" · 7:30 "20 min left, Early-Bird bonus fading" · 7:45 "Last call" |
| Lunch (fallback) | random | One nudge at 12:00 **only if the morning was missed**, plus a **"Home for lunch"** button on the widget and notification that opens a 20-min bonus window on demand |
| Evening (post-swim) | 19:00–21:30 | 20:15 "Swim done? Mission waiting" · 21:00 "Last call" · nothing after 21:30 |

- Notification actions: **Start now**, **Snooze 15**, **Lunch mode**.
- Windows are editable in Settings; weekends get their own schedule.
- Once a day's mission is done, every remaining nudge for that day is cancelled.
- Implementation: `AlarmManager` inexact windowed alarms (battery-friendly, no exact-alarm
  permission needed) re-armed daily and after reboot; `POST_NOTIFICATIONS` permission on Android 13+.

---

## 3. The reward system: your hero

No casino imagery and no real money, ads or purchases, ever. The *mechanics* that keep people coming
back (random rewards, guaranteed drops, near-misses, streaks, collecting, commitments) live inside one
theme: **you level up a superhero whose body tracks your real training.** The body comes in about 8
stages driven by your real progression. Each new stage is **unlocked as an event**: a full-screen
transformation sequence (flash, shockwave, the bigger hero revealed). It is deliberately *not* a slow
invisible morph: every visible change should land as a reward moment.
The hero and emblem are original: a masked comic-book hero whose emblem grows out of the dumbbell icon.

### What you earn
- **XP** → Rank (Rookie → Vigilante → Hero → Champion → Legend → *Defined*), with prestige after the endgame.
- **Power**: what you spend on gear upgrades and cosmetics.
- **Supply pods**: crash-landed pods you tap open. Each one holds gear.
- **Scrap**: from duplicate gear; turns into any piece you're missing.

### Rarity (each tier has its own full-screen effect)
Standard (white) · Enhanced (cyan) · Elite (violet) · Legendary (gold) · **Cosmic** (prismatic, screen-wide).

### Mechanics, by where they fire
| Moment | Mechanic | (Psychology behind it) |
|---|---|---|
| Every rep tap | Pop + haptic; a **combo meter** with rising pitch | Instant feedback |
| Every completed set | **Power surge**: energy orbs fly into the hero. Usually small, sometimes a bigger drop, very rarely Legendary | Random rewards on an unpredictable schedule |
| Mission complete | **Supply pod** crashes in; tap to crack it open | Opening a mystery box |
| Bad-luck protection | Pods glow hotter the longer you go without a good drop: Elite guaranteed within 10, Legendary within 40 | Guaranteed drop after a dry spell |
| Done inside a window | **Dawn Patrol**: a morning-window bonus reveal with better odds (**Early-Bird**) | Reveal bonus |
| First open in a window | **Signal flare**: tap it as it streaks across Home | Daily login reward |
| Any time | **Upgrade bench**: combine 3 pieces of gear into one, with a chance it jumps a rarity tier; the flicker toward the next tier that *almost* happens is the near-miss | Variable outcome + near-miss |
| Every mission | **Charge meter**: fills with every workout. Any day it can overload into **GO SUPER**, a full-screen frenzy that showers rewards. Longer streaks make it likelier | Growing prize pool |
| Streak | **Momentum**: the hero's aura multiplies rewards (1.0× → 3.0× at 30 days). Earnable **Force fields** absorb one missed day automatically | Streak multiplier + loss aversion |
| After a mission | **Oath**: swear to train in tomorrow's window. Keep it and the reward doubles; break it and the oath shatters (a Force field protects it) | Commitment device |
| Random | **Distress signal**: "The city needs you!" Surprise 2× windows, announced by notification and favouring the lunch hour | Surprise bonus |
| Rank up | A full **transformation sequence**, the app's biggest motion-graphics moment | Celebration |
| Ongoing | **Suit Locker**: capes, emblems, suit colours, visors, auras, in themed sets, with set-completion bonuses | Collecting |
| 28-day cycle | **Comic issue**: each month is an issue; training unlocks its panels, 40 steps of rewards along the way | Season pass |
| Daily/weekly | **Missions board**, e.g. "Train before 7:20", "3 mornings this week" | Short-term goals |
| Milestones | **Villain fights**: **The Snooze** (morning-window boss), **Doctor DOMS** (soreness), **Couch Lord**, and the final boss **Prime**, your 28-year-old self | Big moments |

### Guardrails baked into the rewards
- RECHARGE days and deload weeks pay full rewards, so resting is never the "losing" move.
- Bonus reps beyond prescription are capped and pay nothing extra past +2.
- A broken streak dims Momentum; it never takes away banked Power or gear.

---

## 4. Art pipeline

Organic character art is not something code-drawn shapes can do well, so the work is split:

- **ComfyUI on your PC draws the hero** (via Pinokio, on your GPU). Image models are good at
  anatomy and style.
- **Claude does the code:** builds and runs the ComfyUI workflows, checks consistency, cleans up
  assets, and writes all the motion.
- **You are the art director.** Nothing goes into the app without your approval.
- **No step depends on Claude placing pixels by eye.** Cut-outs use a background-removal model,
  layer splits use a segmentation model, and stage alignment uses an image-registration algorithm.

### How Claude reaches ComfyUI
This session runs in a cloud container and can't see your PC directly. Either:
1. **Connect a folder on your PC to the session** (Claude desktop app → Settings → Claude Code →
   Remote Control). Claude can then run commands on your PC and drive ComfyUI's built-in local HTTP
   API (`/prompt`, `/history`, `/view`). No MCP setup needed; Pinokio only changes which port it's on.
2. **Run the art step from a Claude session on your PC** (desktop app, or `claude remote-control` in
   the Define folder). The ComfyUI MCP server can then be added locally if wanted, though the plain
   HTTP API is enough.

### Steps
1. **Character bible.** Chosen: stylized 3D render style, magenta/navy suit, orange cape, domino mask
   with cyan lenses, dumbbell emblem (round 1, `D_3d v1_cape`, seed 4001).
2. **Physique ladder.** 8 stages from day one to Prime. Method that worked (rounds 2–5): identical
   prompt with the physique as the *last* sentence, two length-matched extremes (scrawny / hugely
   muscular), and the stages in between as blends of the two text encodings (ConditioningAverage),
   re-spaced so each step looks equally big. Small stance/belt drift between stages is fine because
   stages are revealed through a transformation effect, never morphed.
3. **Poses.** Idle, flex and power-up per stage: about 15–18 images.
4. **Layers.** Automatic split into figure, cape and glow areas (eyes, emblem, aura).
5. **Motion (code).** Mesh warping for breathing and flexing (Android can bend a bitmap like a rubber
   sheet), wind ripple on the cape, depth-based tilt parallax, glow on eyes/emblem, particles,
   lighting sweeps, comic-panel transitions.
6. **Gear without multiplying the art.** Suit colours via automatic recolouring of the suit area;
   emblems as flat geometric graphics; auras and powers as code effects. Only capes, villains and
   comic panels need new generated images.

### Art test (gate before any app code depends on the art)
Character bible + physique ladder + one animated pose in a browser page you can open on your phone.
If it doesn't hit your bar, the reward system falls back to the crystal theme (geometric gems and
light effects, which code can render convincingly).

---

## 5. Look and motion

**Palette.** Saturated colours on a deep ink base so they glow: hot magenta `#FF2E93`, electric
cyan `#00E5FF`, acid lime `#C6FF00`, sunburst orange `#FF8A00`, ultraviolet `#7C4DFF`, ink
`#0B0A2A`. The background palette shifts with the window: sunrise oranges in the morning, pool-blue
and neon in the evening.

**Type.** A chunky display face (Bungee or Archivo Black, OFL licensed) for numbers and headlines,
plus a clean sans for body text.

**Comic-book layer.** Halftone dots, speed lines, starbursts and onomatopoeia ("POW", "SURGE") as
code effects; screen transitions as comic panels sliding, tearing and zooming.

**Nothing is ever still.**
- A living background: animated mesh gradient (AGSL shader on Android 13+, Canvas fallback below)
  with drifting particles.
- The hero idles, breathes, and reacts to every set.
- Buttons breathe, squash and stretch on press, and fire a haptic tick.
- Every number rolls like an odometer.
- **Full-screen effects library:** confetti cannon, shockwave rings, energy fountains, god-rays,
  fireworks, lightning, screen shake, and a colour-split flash for Legendary/Cosmic.
- **Sound:** synthesized in code (AudioTrack), so there are no asset licences. Power-up arpeggios,
  impacts, whooshes, and a big swell for GO SUPER.
- **Haptics:** composed vibration patterns (Android 11+) with fallbacks.
- **Intensity setting:** Chill / **Hype** (default) / Absolutely Unhinged. A global mute is
  available for when you're near other people.

**Icon.** The diagonal dumbbell from the reference, in white on a magenta→orange gradient, as an
adaptive icon with a monochrome layer for themed icons.

---

## 6. Screens

1. **HQ (Home):** the hero front and centre, today's mission card (Push/Pull/Recharge), window
   countdown, charge meter, Momentum, signal flare, missions board.
2. **Check-in:** body-map soreness tap → the engine adjusts today's mission.
3. **Workout Player:** one exercise at a time. A giant rep-tap target, a power surge after each set,
   a rest timer, effort rating at the end, then the supply pod.
4. **Armory:** open pods, Dawn Patrol reveals, upgrade bench, Oaths.
5. **Suit Locker:** gear collection; dress the hero.
6. **Journey:** the comic issue, villain fights, charts of reps/weight over time, and "You vs Prime".
7. **Settings:** windows, equipment/weights, intensity, sound, haptics.
8. **Home-screen widget:** the hero, today's mission, streak, and a "Home for lunch" button.

---

## 7. Technical architecture

- **Kotlin + Jetpack Compose**, single Activity, a small hand-rolled back stack (no navigation
  library), minSdk 26 / targetSdk 36.
- **Persistence:** append-only JSON workout log + state snapshot (SharedPreferences/org.json), no
  database dependency.
- **`engine/` is pure Kotlin** (ProgramEngine, StreakEngine, RewardEngine with seeded RNG/drop
  tables/pity, Economy), unit-tested locally before every push.
- **Effects:** a reusable Compose particle system on `Canvas`, `Canvas.drawBitmapMesh` for hero
  warping, plus AGSL `RuntimeShader` effects gated to API 33+.
- **Reminders:** AlarmManager + BootReceiver + notification channel with actions.
- **Widget:** Jetpack Glance.
- **Build/delivery:** GitHub Actions builds the APK on every push and publishes it to the repo's
  **Releases**. You install from `github.com/thriple/Define/releases/latest` on the phone. The
  signing key is committed as a throwaway debug key so updates install over old versions without
  wiping data. (This sandbox can't reach Google's Maven servers, so CI does the compiling.)

---

## 8. Build passes

| Pass | Delivers |
|---|---|
| **0. Art test** | Character bible, physique ladder, one animated pose in a browser page. Go/no-go on the hero. |
| **1. Core loop** | Project + CI + icon + theme; living background; HQ; Check-in; Workout Player with rep taps, power surges and a basic pod; program engine + progression; streaks; reminders. Placeholder hero if the art isn't ready. *Installable and usable from day one.* |
| **2. Rewards** | Full reward system: pods with bad-luck protection, Dawn Patrol, signal flare, upgrade bench, charge meter + GO SUPER, Oaths, Force fields, Distress signals, missions board, comic issue, Suit Locker, synthesized SFX, haptic patterns. |
| **3. Motion polish** | Animated hero, shader backgrounds, comic transitions, full effects library, odometers everywhere, intensity setting, widget, Journey screen, villain fights. |
| **4. Tuning** | Balance drop rates and progression against your real logs; fix whatever feels flat or annoying. |

Pass 0 and pass 1 can run side by side: the app code doesn't wait on the art.

---

## 9. Open questions

1. **Equipment:** one 30 lb dumbbell or a pair? Anything heavier or adjustable, or resistance bands
   / a pull-up bar? (This decides the weight ladder past 30 lb.)
2. **Joints:** any shoulder, elbow or wrist complaints? (Affects overhead press vs. alternatives.)
3. **Weekends:** same windows, or different ones?
4. **Sound:** on by default, or haptics-only until you turn it up?
5. **Phone:** roughly what model/Android version? (Decides how much of the shader work you'll see.)
6. **ComfyUI:** which GPU, and which models are installed (any image-edit model such as Flux Kontext
   or Qwen-Image-Edit, and ControlNet)? What port does Pinokio show for ComfyUI?

---

## 10. Status (2026-11-20)

**Shipped (builds 1–4, Releases `build-N`):**
- Engine (pure Kotlin, `engine/`, unit-tested on the JVM in a scratch Gradle project): rotation, ladders,
  soreness-gated step-ups, deloads, welcome-back, streak + shields, momentum, pods with pity, ranks
  (thresholds 0/1500/5000/12000/24000/42000), 8 hero stages unlocked as transformation events.
- UI: HQ, body check, workout player, effort, supply pod, transformation, settings, reminders.
- Hero body rig (`ui/HeroRig.kt`, `ui/Hero.kt`): landmarks per stage from the silhouettes
  (`art/tools/rig_*.py`), displacement-field AGSL shader (Android 13+) with drawBitmapMesh fallback:
  chest-driven breathing, sway over planted feet, cape flutter, glow; post-workout pump.
- Coaching (`engine/Coaching.kt`, `engine/Pictogram.kt`, `ui/PictogramView.kt`): plain-language primer,
  per-exercise intro cards with animated stick-figure diagrams (IK), "How to do it".
- Workout input: one tap per round (hands are busy with weights); PACE ME = 3-2-1 countdown + per-phase
  cue words and tones at each exercise's tempo, synced figure, reps-left countdown.

**User feedback so far:** whole-body breathing looked wrong (fixed with the rig); workout terms too
vague (plain language + diagrams); tapping per rep impractical with weights (one tap / PACE ME).

**Open questions:** pair vs single 30 lb dumbbell, heavier weights owned (only 30 lb caps the hero at
stage 6 of 8), joint issues, weekend windows, phone model/Android version.

**Next candidates:** hero on the workout screen flexing per round; Suit Locker (gear recolours the hero
via rig masks); Oath / Distress signal / Dawn Patrol / missions board / comic issue; history calendar;
intensity setting; widget.

**Side project:** SeedVR2 Upscaler (PowerShell WinForms app, not in this repo) driving
`S:\pinokio\api\comfy.git\app\custom_nodes\seedvr2_videoupscaler\inference_cli.py` with ComfyUI's
`app\env\Scripts\python.exe`, models in `app\models\SEEDVR2`, ffmpeg from `S:\pinokio\bin\ffmpeg-env`.
Quality was poor at batch 5 / 3B Q8; added input noise, tile size and preview mode for A/B tests.

### Music pass (2026-11-21)
- `engine/music/`: real-time synth engine in pure Kotlin. Instruments: 909-style kick, snare, clap, hats/crash,
  synth taiko pair, pumping bass, acid line, supersaw pads/stabs, pulse plucks, risers, impacts. Electronic
  writing only; the user asked for no synth imitations of real-instrument music.
- Scenes: HOME (energy grows with progress and streak), CALM, WORK, REST, POD, VICTORY, TRANSFORM. The key
  changes each day.
- `engine/Pace.kt`: every exercise's rhythm is in beats at 128–140 BPM, so one rep = 2 bars. The figure,
  cue words, haptics and music all read from it. PACE ME counts in to a bar line.
- PACE ME movement cue: a fast arpeggio up the chord while lifting and a slower one down while lowering.
  A continuous gliding guide tone was tried and rejected because it sounded like a siren.
- `ui/Music.kt`: AudioTrack thread. The beat clock comes from the playback head, so visuals match what you
  hear. No audio focus requests; stays quiet if other music is already playing.
- Settings: separate SOUND and MUSIC toggles. With music off, cues (count-in, movement arps, metronome) remain.
- Demos are rendered on the JVM by `MusicDemo.kt` in the scratch enginetest project.
