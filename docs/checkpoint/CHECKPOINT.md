# MyFit Tracker — checkpoint (2 Oct 2026, 16:3x PKT)

Work paused at the user's request. Nothing below has been merged into `main` beyond what's listed as "on main".

## Latest delivered build
- build-43 (accounts/Firebase on). `main` has since moved on (WIP below), but CI runs on main after build 43 were not delivered to the user.

## On `main` (compiles, CI green at run 48)
- DB v4 entities + DAOs + exact v3→v4 migration (GlucoseReading, Medication, MedicationLog, BloodPressure, VitalReading, CycleDay, MindSession, MoodEntry …).
- Health Connect permissions for new types, Health hub, Day log overlay, hidden Developer options (tap version 7×).
- WIP features from an interrupted run: ui/cycle, ui/glucose (+Meds, PDF report), ui/mind (breathing, meditation, ambient sound, mood, stress), ui/vitals (camera HR, BP, devices), ui/routine (reminders, supplements, fasting), dashboard grid with half-width cards (Tiles.kt), WaterGlass3D.kt, 26 themes.
- CI builds `agent/**` branches without publishing releases.

## Feature branches on GitHub (each 1 commit ahead of main; interrupted, NOT yet CI-verified unless noted)
| Branch | State |
|---|---|
| agent/themes | "40 static themes in four families (dark, light, sport, Pakistani)" — completed commit, verify CI |
| agent/pip | "7×7 look grid with spring-smoothed bilinear cross-fade follow" — completed commit, verify CI + renders |
| agent/foodicons | pilot: per-dish 3D icon generator (FLUX.1-schnell via stable-diffusion.cpp in CI) — not finished |
| agent/health | checkpoint WIP (cycle/diabetes/vitals/mind completion) — partial |
| agent/nav | checkpoint WIP (dock Home·Train·Food·Arena·Settings, log icon, scrim, tiles) — partial |
| agent/habits | checkpoint WIP (reminders, supplements, fasting, badges & streaks) — partial |
| agent/ai | checkpoint WIP (on-device Gemma, caps, rewarded ads) — barely started |

Agent briefs used: docs/checkpoint/agent_common.md and the prompts (summarised below). Research: docs/checkpoint/research_health.md, research_tech.md.

## User's final decisions (this round)
- Dock: Home · Train (Exercises moved inside) · Food (everything food) · **Arena** (leaderboard, challenges, friends) · Settings. Log → top-bar calendar/history icon on every tab.
- Bottom gradient scrim behind dock (like Samsung Health). Smaller "Open camera" pill. Fix Today's Progress tile (clipped legend, orange blob on tiny progress).
- Half-width tiles automatically for small cards + **resize by holding** a card (Small/Large, Hide) + drag.
- 3D hydration glass, very animated, water follows gravity via accelerometer/gravity sensor (low heat).
- Food icons: a distinctive 3D icon for **every** food that looks like that dish; remove Wikimedia photos.
- Themes: 25+ across Premium dark, Clean light, Sport & gym, Pakistani-inspired.
- Pip: ≥3× look points, smooth finger following.
- Developer/AI/API settings hidden in Developer options only.
- Eligibility: cycle features for women (profile sex female); diabetes features for users who manage diabetes (ask once; diabetesType setting).
- Cycle: core + symptoms/phase tips + pill/period reminders + fertility logs + TTC + Pregnancy + Perimenopause modes; phone-only + app lock + discreet notifications; haiz/istihada mode later (optional, scholar-reviewed).
- Diabetes: full toolkit (glucose log + HC, tags, TIR, AGP, HbA1c + GMI, meds/insulin log only, low-sugar card 15-15/1122, doctor PDF, reminders, meal links) **+ Ramadan mode** (IDF-DAR). No dose calculators.
- Vitals: watch vitals via Health Connect + manual BP + camera heart rate (experimental).
- Mindfulness: breathing, meditation timer + sounds (+ Pip-voiced guided), mood & stress journal (HRV stress estimate), sleep wind-down.
- Brands: Health Connect hub + brand setup guides + per-metric source priority; Huawei via Health Sync; phone-sensor fallback.
- AI: on-device first (optional Gemma download on capable phones) + capped cloud; overflow = free daily cap + rewarded ads.
- Next phase after this: Reminders + Supplements AND Badges & Streaks.
- App name: still "MyFit"; user asked for suggestions (Pipfit, Taqat, JoshFit, Junoon, Kinetiq, Rozana) — not chosen yet.

## To resume
1. For each agent branch: rebase/merge onto main, finish remaining work per its brief, get CI green (`docs/checkpoint/scripts/wfb.sh agent/<name>`).
2. Merge into main in order: themes → pip → health → habits → nav → ai → foodicons; resolve shared-file conflicts (MyFitRoot.kt, Nav.kt, SettingsStore.kt, MeScreen.kt, build.gradle.kts, AndroidManifest.xml).
3. Independent crash/security review, fix, build on main, verify signing cert (SHA-256 prefix 93a908b11ff96f312fb65cbf), deliver APK link.
4. Give the user the AI cost/strategy write-up (on-device Gemma + capped cloud via a Cloudflare Worker proxy later).

## Progress log (resumed 2 Oct, 21:16 PKT — sequential, one branch at a time)
- ✅ themes merged (40 themes) — CI green run 52
- ✅ pip merged (7×7 look grid, smooth follow) — CI green run 53
- ✅ health merged + finished: glucose HbA1c log, check reminders, Ramadan mode; cycle TTC/pregnancy/perimenopause modes + PIN lock — CI green run 56
- ✅ habits merged + badges screen, streaks tile, celebration, hub entries — CI green run 58
- ✅ nav merged + finished: dock Home·Train(Workouts|Exercises)·Food(diary + fasting/supplements)·Arena(SocialScreen)·Settings; calendar history icon top-right on every tab (Overlay.History); bottom gradient scrim; Streaks & badges dashboard tile; ring blob/legend fix + compact camera pill + resize toolbar + eligibility gating verified; AI keys only in Developer options — CI green run 61
- ⏭ next: ai → foodicons
