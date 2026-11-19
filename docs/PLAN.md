# DEFINE — Plan

A loud, always-moving Android app that turns short arm/chest workouts into a casino you can't stop
visiting. The training side is deliberately conservative (a 40-something body heals slowly); the
reward side is deliberately excessive (a 40-something brain still loves a slot machine).

---

## 1. The training program

### Principles
- **Consistency over intensity.** The thing being trained is showing up. Every day has a mission,
  and every mission is short (8–12 min).
- **Recovery is built into the rotation**, not something you're punished for.
- **Progress only when the body says yes.** A step up happens only after a session that felt
  manageable *and* a soreness check-in that came back low.
- **No rewards for overdoing it.** Bonus reps are capped at +2 per set. The casino can never
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
- **Missed 4+ days:** resume one step back, with a "Welcome Back" crate, never a guilt screen.
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

## 3. The casino (gamification)

No real money, no ads, no purchases, ever. Everything is earned by training.

### Currencies
- **XP** → Rank (Rookie → Iron → Steel → Titan → Mythic → *Defined*), with prestige after the endgame.
- **Chips** → spend on slots, wagers and cosmetics.
- **Keys** → open crates. Earned by completing missions.
- **Shards** → from duplicate cards; craft any card you're missing.

### Rarity ladder (each tier has its own full-screen effect)
Common (white) · Rare (cyan) · Epic (violet) · Legendary (gold) · **Mythic** (prismatic, screen-wide).

### Mechanics, by where they fire
| Moment | Mechanic |
|---|---|
| Every rep tap | Pop + haptic; a **combo meter** with rising pitch (each tap a step up the scale) |
| Every completed set | **Set Roll**, a variable-ratio drop (chips most of the time, sometimes rare+, very rarely legendary) with a reel reveal |
| Mission complete | **Loot Crate**: god-rays, shake, burst. A pity counter guarantees an Epic within 10 crates and a Legendary within 40 |
| Done inside a window | **Scratch card**: finger-scratch to reveal. Morning-window scratch cards have better odds (Early-Bird) |
| First open in a window | **Daily Wheel** spin |
| Any time | **Iron Slots**: 3 reels, spend chips; near-miss animations included as requested |
| Every mission | Feeds the **Progressive Jackpot** (live ticking counter on Home). Every completion has a small hit chance, raised by streak length |
| Streak | **Multiplier ladder** (1.0× → 3.0× at 30 days). **Streak Shields** are earnable, and one auto-equips on a missed day |
| After a mission | **Double or Nothing**: wager chips that you'll train inside tomorrow's window |
| Random | **Happy Hour**: surprise 2× drop windows, announced by notification and favouring the lunch hour |
| Level up | **Plinko drop** for the level reward |
| Ongoing | **Collection**: ~60 holo-foil cards in themed sets ("Iron Beasts", "Mythic Lifters", "Deep Water"), with set-completion bonuses |
| 28-day cycle | **Season Track**: 40 tiers of rewards; a fresh season each month |
| Daily/weekly | **Bounties**, e.g. "Train before 7:20", "3 mornings this week", "Perfect form week" |
| Milestones | **Boss fights** for max tests and the endgame goals |
| Shop | Cosmetics: themes, rep-tap effects, particle trails, sound packs, crate skins |

### Guardrails baked into the economy
- RECHARGE days and deload weeks pay full rewards, so resting is never the "losing" move.
- Bonus reps beyond prescription are capped and pay nothing extra past +2.
- A broken streak loses the multiplier, never banked chips or cards.

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
- **Full-screen effects library:** confetti cannon, shockwave rings, coin fountains, god-rays,
  fireworks, lightning, screen shake, and a chromatic-aberration flash for Legendary/Mythic.
- **Sound:** synthesized in code (AudioTrack), so there are no asset licences. Arpeggios, coin
  clinks, reel ticks and jackpot sirens.
- **Haptics:** composed vibration patterns (Android 11+) with fallbacks.
- **Intensity setting:** Chill / **Hype** (default) / Absolutely Unhinged. A global mute is
  available for when you're near other people.

**Icon.** The diagonal dumbbell from the reference, in white on a magenta→orange gradient, as an
adaptive icon with a monochrome layer for themed icons.

---

## 5. Screens

1. **Arena (Home):** today's mission card (Push/Pull/Recharge), window countdown, streak flame +
   multiplier, ticking jackpot, daily wheel, bounty strip.
2. **Check-in:** body-map soreness tap → the engine adjusts today's mission.
3. **Workout Player:** one exercise at a time. A giant rep-tap target, set roll after each set, a
   rest timer with a mini-spin, effort rating at the end, then the crate.
4. **Vault:** crates, slots, scratch cards, wagers.
5. **Collection:** card binder with holo tilt (gyroscope).
6. **Journey:** a board-game path to the endgame goals, charts of reps/weight over time, and a
   "You vs Ghost of 28" comparison.
7. **Settings:** windows, equipment/weights, intensity, sound, haptics.
8. **Home-screen widget:** streak, today's mission, jackpot, and a "Home for lunch" button.

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
| **1. Core loop** | Project + CI + icon + theme; living background; Arena; Check-in; Workout Player with rep taps, set rolls and basic crate; program engine + progression; streaks; reminders. *Installable and usable from day one.* |
| **2. Casino** | Full economy: wheel, slots, scratch cards, jackpot, wagers, pity, happy hour, bounties, season track, card collection, synthesized SFX, haptic patterns. |
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
