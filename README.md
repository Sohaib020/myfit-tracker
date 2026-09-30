# MyFit Tracker

Private, offline-first Android fitness logbook for one user. Kotlin · Jetpack Compose · Room · Material 3.

## Getting the APK
Every push to `main` builds a signed APK in GitHub Actions and publishes it as a **Release** (`build-N`).
Open the repo's *Releases* page on your phone and tap the `.apk` to install. Builds are signed with the same
key every time, so a new build installs over the old one and keeps your data.

## Principles (enforced in code)
- Raw records are the source of truth; totals, averages, volumes and PRs are recalculated, never stored as the only copy.
- Missing data is never zero. Every average reports how many days actually had data.
- Every entry is stamped with epoch time + time zone + the local calendar day at the moment it was logged.
- Canonical units (kg, cm, ml, m, s) at full precision; rounding happens only on screen.
- Targets and goals are versioned: changing one never rewrites past performance.
- Soft delete / archive for anything history depends on.
- Estimates are always labelled as estimates.

## Build phases
| Phase | Scope | Status |
|---|---|---|
| 1 | Database (full schema), profile/onboarding, navigation, liquid-glass design system, themes & wallpapers, dashboard, quick add (weight, water, sleep, steps, measurements, check-in, notes), daily log with edit/delete | ✅ |
| 2 | Gym Mode (fast set logging, pre-fill, previous/best, rest timer, supersets, stopwatch), templates, repeat workout, history with corrections, 876-exercise photo library + custom exercises | ✅ |
| 2.5 | Health Connect (Samsung Health / Galaxy Watch) steps, sessions, sleep, HR; phone step sensor; Pip chat (offline data answers + Gemini), Pip v3 mint plush (2.5D, tap reactions, voice), liquid-glass refraction engine, 21 themes (16 animated), blur/refraction controls | ✅ |
| 3 | Progression graphs, PR detection & PR history | next |
| 4 | Weight & measurement analytics, progress photos | |
| 5 | Nutrition, food database (incl. Pakistani foods), saved meals, barcode | |
| 6 | Supplements, fasting timer, Health Connect | |
| 7 | Reminders & notifications | |
| 8 | Analysis & charts | |
| 9 | Reports: PDF / CSV / JSON | |
| 10 | Backup & restore | |
| 11 | Voice logging, camera calorie estimates | |
| 12 | Widgets, app lock, plate calculator, polish & testing | |

## Signing (one-time setup)
The signing key is never committed. Add two repository secrets (Settings → Secrets and variables → Actions):
`MYFIT_KEYSTORE_B64` (base64 of the keystore) and `MYFIT_KEYSTORE_PASSWORD`. With them, every build is signed
with the same key and installs over the previous one without losing data.

Fonts: Montserrat and Anton, SIL Open Font License (see `FONT_LICENSE_OFL.txt`).

Exercise photos and instructions: [free-exercise-db](https://github.com/yuhonas/free-exercise-db), public domain (Unlicense).
