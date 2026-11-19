# Define

A loud, always-moving Android workout app that turns short arm and chest workouts into levelling up a
superhero. The plan is in [docs/PLAN.md](docs/PLAN.md); the hero art pipeline is in [art/README.md](art/README.md).

## Install on your phone
1. Open **https://github.com/thriple/Define/releases/latest** in Chrome on the phone.
2. Tap the `define-N.apk` file under *Assets*, then **Open**.
3. Allow "install from this source" if asked, then **Install**. If Play Protect warns, choose *Install anyway*.

Every push builds a new APK (GitHub Actions → Releases). New builds install over old ones and keep your
progress, because every build is signed with the same throwaway key (`debug.keystore`).

## Layout
- `app/src/main/java/com/thriple/define/engine/`: pure-Kotlin training program, progression, streaks and
  rewards (no Android code, unit-testable on a plain JVM).
- `.../ui/`: Compose screens and the motion/effects toolkit. `.../reminders/`: window nudges.
- `art/`: ComfyUI workflows, generator scripts and the generated hero stages.
