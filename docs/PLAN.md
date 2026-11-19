# DEFINE — Plan

A loud, always-moving Android app that turns short arm/chest workouts into a crystal-hunting game
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

| Exercise | Start | Milestones | Endgame ("Ghost of 28" boss) |
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
- **Missed 4+ days:** resume one step back, with a "Welcome Back" geode, never a guilt screen.
- **Max tests** every 4 weeks for push-ups are staged as boss fights (boss health bar = target reps).

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

## 3. The reward system: Crystals

No casino imagery: no slots, wheels, chips or jackpots, and no real money, ads or purchases, ever.
The *mechanics* that keep people coming back (random rewards, guaranteed drops, near-misses, streaks,
collecting, commitments) live inside one theme: **you grow and crack open crystals.** The theme
fits the name (a cut gem is all *defined* facets), and gems are made for bright, prismatic effects.

### What you earn
- **XP** → Rank (Rookie → Iron → Steel → Titan → Mythic → *Defined*), with prestige after the endgame.
- **Shards**: what you spend on Forge fusions and Workshop cosmetics.
- **Geodes**: rough stones you crack open by tapping. Each one holds a crystal.
- **Dust**: from duplicate crystals; turns into any crystal you're missing.

### Rarity = gemstone (each tier has its own full-screen effect)
Quartz (white) · Aquamarine (cyan) · Amethyst (violet) · Topaz (gold) · **Opal** (prismatic, screen-wide).

### Mechanics, by where they fire
| Moment | Mechanic | (Psychology behind it) |
|---|---|---|
| Every rep tap | Pop + haptic; a **combo meter** with rising pitch (each tap a step up the scale) | Instant feedback |
| Every completed set | **Spark**: a spark arcs off the rep target and lands as a shard. Usually Quartz, sometimes better, very rarely Topaz | Random rewards on an unpredictable schedule |
| Mission complete | **Geode**: tap to crack it; fractures spread, light leaks through the cracks, then it bursts open | Opening a mystery box |
| Bad luck protection | Geodes get visibly **denser and glowier** as you go: an Amethyst is guaranteed within 10, a Topaz within 40 | A guaranteed drop after a dry spell (often called a "pity timer") |
| Done inside a window | **Polish**: rub a dull stone with your finger to reveal its colour. Morning stones polish to better gems (**Early-Bird**) | Reveal by scratching |
| First open in a window | **Meteor**: a small meteor streaks across Home; tap to catch it | A daily login reward |
| Any time | **Forge**: fuse 3 crystals into one, with a chance it jumps a rarity tier. The flicker toward the next colour that *almost* happens is the near-miss | Variable outcome + near-miss |
| Every mission | **The Core**: a giant crystal at the centre of Home that grows with every workout. Any day it can **bloom**, a supernova that showers rewards. Longer streaks make a bloom more likely | Growing prize pool |
| Streak | **Resonance**: the Core's glow multiplies rewards (1.0× → 3.0× at 30 days). Earnable **Shields** absorb one missed day automatically | Streak multiplier + loss aversion |
| After a mission | **Pledge**: set a crystal into tomorrow's window. Train in the window and it grows a size; miss and it cracks (a Shield protects it) | Commitment device |
| Random | **Aurora**: surprise 2× windows when the sky lights up, announced by notification and favouring the lunch hour | Surprise bonus |
| Level up | **Cascade**: the old rank's crystal shatters and the shards rain down into rewards | Celebration |
| Ongoing | **Cabinet**: ~60 named crystals in themed sets ("Deep Water", "Ironstone", "Starfall"), procedurally shaped, refracting light as you tilt the phone. Set-completion bonuses | Collecting |
| 28-day cycle | **Expedition**: a 40-step path through biomes (caves → reef → glacier → volcano → orbit) with rewards along the way; a new expedition each month | Season pass |
| Daily/weekly | **Quests**, e.g. "Train before 7:20", "3 mornings this week", "Perfect form week" | Short-term goals |
| Milestones | **Boss fights** for max tests and the endgame goals | Big moments |
| Workshop | Cosmetics: themes, rep-tap effects, particle trails, sound packs, geode skins | Something to spend on |

### Guardrails baked into the rewards
- RECHARGE days and deload weeks pay full rewards, so resting is never the "losing" move.
- Bonus reps beyond prescription are capped and pay nothing extra past +2.
- A broken streak dims Resonance; it never takes away banked shards or crystals.

---

## 4. Look and motion

**Palette.** Saturated colours on a deep ink base so they glow: hot magenta `#FF2E93`, electric
cyan `#00E5FF`, acid lime `#C6FF00`, sunburst orange `#FF8A00`, ultraviolet `#7C4DFF`, ink
`#0B0A2A`. The background palette shifts with the window: sunrise oranges in the morning, pool-blue
and neon in the evening.

**Type.** A chunky display face (Bungee or Archivo Black, OFL licensed) for numbers and headlines,
plus a clean sans for body text.

**Nothing is ever still.**
- A living background: animated mesh gradient (AGSL shader on Android 13+, Canvas fallback below)
  with drifting particles.
- Buttons breathe, squash and stretch on press, and fire a haptic tick.
- Every number rolls like an odometer.
- Transitions: the mission card morphs into the workout player; radial wipes; zoom-blur between
  major screens.
- **Full-screen effects library:** confetti cannon, shockwave rings, shard fountains, god-rays,
  fireworks, lightning, screen shake, and a colour-split flash for Topaz/Opal.
- **Sound:** synthesized in code (AudioTrack), so there are no asset licences. Arpeggios, crystal
  chimes, cracking stone, and a big swell when the Core blooms.
- **Haptics:** composed vibration patterns (Android 11+) with fallbacks.
- **Intensity setting:** Chill / **Hype** (default) / Absolutely Unhinged. A global mute is
  available for when you're near other people.

**Icon.** The diagonal dumbbell from the reference, in white on a magenta→orange gradient, as an
adaptive icon with a monochrome layer for themed icons.

---

## 5. Screens

1. **Arena (Home):** today's mission card (Push/Pull/Recharge), window countdown, the Core
   crystal (grows with your streak), Resonance multiplier, meteor, quest strip.
2. **Check-in:** body-map soreness tap → the engine adjusts today's mission.
3. **Workout Player:** one exercise at a time. A giant rep-tap target, a spark after each set, a
   rest timer, effort rating at the end, then the geode.
4. **Forge:** crack geodes, polish stones, fuse crystals, set Pledges.
5. **Cabinet:** the crystal collection, refracting light as you tilt the phone (gyroscope).
6. **Journey:** a board-game path to the endgame goals, charts of reps/weight over time, and a
   "You vs Ghost of 28" comparison.
7. **Settings:** windows, equipment/weights, intensity, sound, haptics. A Workshop tab holds cosmetics.
8. **Home-screen widget:** the Core, today's mission, streak, and a "Home for lunch" button.

---

## 6. Technical architecture

- **Kotlin + Jetpack Compose**, single Activity, a small hand-rolled back stack (no navigation
  library), minSdk 26 / targetSdk 36.
- **Persistence:** append-only JSON workout log + state snapshot (SharedPreferences/org.json), no
  database dependency.
- **`engine/` is pure Kotlin** (ProgramEngine, StreakEngine, RewardEngine with seeded RNG/drop
  tables/pity, Economy), unit-tested locally before every push.
- **Effects:** a reusable Compose particle system on `Canvas`, plus AGSL `RuntimeShader` effects
  gated to API 33+.
- **Reminders:** AlarmManager + BootReceiver + notification channel with actions.
- **Widget:** Jetpack Glance.
- **Build/delivery:** GitHub Actions builds the APK on every push and publishes it to the repo's
  **Releases**. You install from `github.com/thriple/Define/releases/latest` on the phone. The
  signing key is committed as a throwaway debug key so updates install over old versions without
  wiping data. (This sandbox can't reach Google's Maven servers, so CI does the compiling.)

---

## 7. Build passes

| Pass | Delivers |
|---|---|
| **1. Core loop** | Project + CI + icon + theme; living background; Arena; Check-in; Workout Player with rep taps, sparks and a basic geode; program engine + progression; streaks; reminders. *Installable and usable from day one.* |
| **2. Crystals** | The full reward system: geodes with bad-luck protection, Polish, Meteor, Forge, the Core + blooms, Pledges, Shields, Aurora, Quests, Expedition, Cabinet, Workshop, synthesized SFX, haptic patterns. |
| **3. Motion polish** | Shader backgrounds, shared-element morphs, full effects library, odometers everywhere, intensity setting, widget, Journey screen. |
| **4. Tuning** | Balance drop rates and progression against your real logs; fix whatever feels flat or annoying. |

---

## 8. Open questions

1. **Equipment:** one 30 lb dumbbell or a pair? Anything heavier or adjustable, or resistance bands
   / a pull-up bar? (This decides the weight ladder past 30 lb.)
2. **Joints:** any shoulder, elbow or wrist complaints? (Affects overhead press vs. alternatives.)
3. **Weekends:** same windows, or different ones?
4. **Sound:** on by default, or haptics-only until you turn it up?
5. **Phone:** roughly what model/Android version? (Decides how much of the shader work you'll see.)
