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
- ✅ ai merged + finished: OnDeviceLlm (LiteRT-LM `latest.release`, GPU→CPU fallback, idle release after 2 min), Settings "Offline brain" card (download/pause/resume/delete, Wi-Fi only, device check), FoodVision + PipBrain route on-device → capped cloud (15 photos / 40 answers per day, live names ≤60), AiCapSheetHost with rewarded ads (+5/+10, max 6/day; Google test ids unless ADMOB_APP_ID / ADMOB_REWARDED_ID secrets), Developer options AI card — CI green run 62 (APK 96→121 MB)
- 🔄 foodicons: hand-written visual descriptions for all 455 foods committed to agent/foodicons (Gemini key is app-restricted; Groq call also failed in CI). Workflow fixed (12 GB swap, Q4 T5, VAE tiling, annotations). Generation in CI is slow on CPU runners (a 512 px run starved a runner after ~3 h with nothing saved). Now 384 px, 150-min budget per shard, artifacts always uploaded; leftovers go in a second run via tools/foodicon/only.txt (= missing.txt from the release). App-side switch (assets/foodicon, FoodThumb Fit tile, diary row icons, photos/credits/foodimg removed) is saved on branch agent/foodicons-app — merge after icons land.
- ⛔ 3 Oct 07:00 PKT: icon run 8 made ~142 raw icons (≈20 min each on CPU; artifacts raw-0..18 on run 37073366023, ids achar…apple alphabetically ~first third) but the pack job and all further GitHub Actions jobs are BLOCKED: "recent account payments have failed or your spending limit needs to be increased". Repo is private → free Actions minutes used up by the 19-shard icon runs. Latest APK = build-62 (everything except 3D icons). To resume: fix billing / raise spending limit (or make repo public), rerun pack job 111098425076, then continue icons (prefer a paid GPU/API e.g. fal.ai FLUX-schnell ≈$1.50 for all 455).
- ▶ 3 Oct 11:45 PKT: repo made PUBLIC by user (Actions free). Icon run 37103950225 started: reuses run-8 icons, 320-min budget, pack commits results to branch `foodicon-assets` (foodicon/*.webp, sheets/, missing.txt). Next: git fetch foodicon-assets → review sheets → copy into agent/foodicons-app assets → merge to main → safety review → one build.
- ⏭ then: review contact sheets → add icons → build → final review → deliver

- ✅ 3 Oct ~18:10 PKT: all 455 icons generated (run 37103950225, saved on branch foodicon-assets); 453 added to app/src/main/assets/foodicon (firni, cheesecake dropped as wrong → generic tile); Wikimedia photos/credits removed; diary rows show icons — CI green run 71. Sheets 1,3,6,9,12,15 reviewed; others not individually checked.
- ⏭ next: user device testing feedback; move AI keys behind a Cloudflare Worker before launch; haiz mode (scholar-reviewed) later; app name.

## Round 3 request (3 Oct 19:19 PKT) — decisions
- Sign-in REQUIRED at start (Google + email). Diabetes onboarding: type + insulin/tablets + CGM + target range (default 70–180). Female → ask cycle Health Connect permissions automatically.
- Pip: real-time 3D model (smooth head turns, blink, nod, wave), no pose cross-fades.
- Tour: spotlight on real screen with Pip pointing.
- Home order: Progress(+check-in merged; splits when small) → Snap a meal + Chat with Pip (one row; small Pip tile keeps Chat button) → Vitals (camera HR + log BP buttons on card) → Food+Hydration (glass always, undo water, splits into 2 when small; rename "Food log" → "Add food") → Mindfulness (breathing + mood buttons) → Menstrual cycle (females; Today's log + Set PIN). All cards visibly openable.
- Me: show online account type + profile photo (also in Me pill).
- Train: remove exercise from template; add exercise to live workout; + on each library exercise.
- iOS-style grow-to-fullscreen open animation. Calendar logs grouped by category tiles. Less clutter overall.
- UI bugs: day strip clipped, empty-state text clipped, Train header overlap, Open camera label missing, history under dock.
- NEXT PHASE: smaller on-device AI (<2.5 GB), more Pakistani foods + icons.
- Tasks run one at a time; one CI build per group.
- ✅ R3: UI fixes + grouped daily log (run 72); Home cards rework (run 73)
- ✅ R3: onboarding sign-in/diabetes/cycle perms (run 74); Me account+photo, Train template quick edit + add-to-workout (run 75)
- ✅ R3: iOS-style grow-open (run 76); Pip spotlight tour + replay (run 78). Next: real-time 3D Pip
- ✅ R3: real-time 3D Pip (Filament 1.56, assets/pip/pip3d.glb from docs/checkpoint/pip3d/export_glb.py), dev toggle 'Live 3D Pip', auto-fallback — run 81. Untested on device: lighting/size may need tuning from user screenshots.

## Round 4 request (4 Oct 00:37 PKT) — decisions
- Pip: bring back the rendered (old) Pip; remove the 3D one. Add MORE head-look poses (denser grid → smoother, less fade) and MORE emotes/animations.
- UI fixes: hydration small tile "+250 ml" clipped; card-open animation must be consistent everywhere (iOS-style), Home→Train tab switch lags; sheets drawn under the dock (Exercises "Add …" sheet); Today's progress card empty space; exercise remove option in Train; daily-log calendar should be a swipe slider with haptic ticks.
- Camera blood pressure: answer only (no validated camera/sensor-free BP method — don't ship).
- Arena revamp: weekly & monthly challenges, virtual journeys with original characters + map track, 1-on-1 duels & team battles, mini-games; graphs, tracks, style (Samsung Together-like).
- Offline AI: total app ≈1.8–1.9 GB; model 1.3–1.5 GB max, smarter, auto-download after install (Wi-Fi), chat + photos.
- New section "Shariah & Health" (shown only to Muslims; asked in onboarding): prayer times + Qibla, fasting hub (Ramadan, Sunnah fasts, qada), Sunnah habits & dhikr (tasbeeh, adhkar), halal food tags + Hajj/Umrah tracker.
- Pakistani dishes + icons moved to the phase after this.
- ✅ R4: old Pip restored (3D removed), UI fixes (run 83). Next: smaller offline AI
- ✅ R4: offline brain → gemma-4-E2B-it-gpu.litertlm (2.0 GB), auto-download once on Wi-Fi (run 84). Next: Shariah & Health
- ✅ R4: Shariah & Health section (ui/deen/*), onboarding step, Home card, halal tags (run 85). Next: Arena revamp
- ✅ R4: Arena revamp (ui/arena/*) — run 86. Next: more Pip look poses + new emotes (render in CI)
- ✅ R4: Pip 13×13 look grid (169 poses, ±32° yaw) + 12 new emotes (thumbsup, salute, facepalm, cheer, grumpy, peekaboo, highfive, bow, meditate, dizzy, sneeze, hearteyes) rendered in CI (tools/piprender, .github/workflows/piprender.yml → pip-assets branch). Meditation screen uses meditate loop. — build-87
- ▶ R5 (4 Oct 02:15 PKT): +107 Pakistani dishes in foods_pk.json (562 total, new 'Regional' category: GB/KPK/Balochi/Sindhi/Kashmiri). Icons rendering in background: push to branch foodicon-run → .github/workflows/foodicon.yml (20 shards, ids in tools/foodicon/only.txt) → results on branch foodicon-assets-r5 (foodicon/*.webp, sheets/, missing.txt). Then: review sheets → copy to app/src/main/assets/foodicon → build.
- ✅ R5 Phase 1: Today's progress big values (shrink-to-fit, % per ring), visible Delete template (+Undo), Arena card removed from Home (Merged/hidden), tour: per-tab anchors inside dock + live-tracking spotlight
- ✅ R5: 105 new food icons added (tandoori_paratha, shakarpara dropped as wrong → regen later with new prompts). 558 icons total.
- ✅ R5 Phase 2: Arena v2 — 3D cast (Pip rig re-skinned: zara/taj/kami/shaheen/motu; tools/piprender/cast.py + castrender.yml → cast-assets branch → assets/arena), stars ledger + 30 levels/7 tiers (ArenaProgress), personalised frozen goals with 4 checkpoints (7★ + 2 early), ScenicTrack (Track.kt) for challenges/journeys, podium leaderboard, duel-win stars, garden/ghost stars, Rewards tab (partner unlocks, level road, 18 awards), level-up celebration
- ✅ R5 Phase 3: in-app updater (update/AppUpdater.kt + UpdateUi.kt: GitHub releases/latest, Wi-Fi auto-download, one-tap install via FileProvider, Settings → App updates), stable MyFitTracker.apk asset + commit message as release notes, invites (social/Invite.kt: link + zxing QR + myfit://invite deep link, Scan QR via code-scanner, auto-add on open), GitHub Pages site (site/, pages.yml — USER must enable Pages: Settings → Pages → Source: GitHub Actions), friends' level+partner on leaderboard (firestore.rules updated — USER must publish rules)
- ✅ R5 Phase 4: Mind → 'Anxiety & Depression' tab (ui/mind/Calm.kt + CalmStore.kt): SOS calm flow (rate → cyclic sighing orb → 5-4-3-2-1 grounding → body → re-rate), tools (sigh/box breathing, grounding, PMR, BA planner, CBT thought record, worry time, 3 good things), weekly GAD-7 + PHQ-9 with history chart and item-9 safety card, recovery stats, PK helplines (Umang 0311-7786264, Rozan 0304-111-1741, 1122, 1098). Home Mind card gets 'Calm' action.
- ✅ R6-1: Today's progress rebuilt (no clip on legend — rounded clip was shaving glyphs; 38% ring split; value + bar + target), Roborazzi screenshot test (app/src/test/.../ProgressCardShotTest.kt, ui-shots.yml → ui-shots branch; fails on spill/overlap), Quick add water row + 4-col grid fill width, CastImage swaps instantly (produceState kept stale bitmap), onboarding DOB wheels + animated age + training-days step (STEPS=14)
- ✅ R6-2: Arena — 15 wildlife characters rendered (castrender WHO list) + assets, 21-char cast with unlocks to Lv55, 60 levels/12 tiers (step 10+2(n-1)), 14 new badges, ChallengeDetail.kt (host, big track, stats, daily chart, checkpoints+rewards, FriendsRace), Social.raceUpdate/raceStandings + firestore.rules arena/{cid}/members (USER must publish rules)
- ✅ R6-3: Buddy.kt (download charpacks/<id>.zip → filesDir/buddy, lookN from pack.json), Pip.kt loads via Buddy.open/source + key(buddy), names on Home/chat, BuddyChooser in Rewards + Settings. Packs rendering: charpacks.yml (15 shards, packrender.py units per clip) → release 'charpacks' + branch charpack-preview (report.txt, sheet.png). Run 37173152975 done: all 20 packs 44/44 clips, 49 looks, 6 talk, 13–19 MB each, release assets live.
- ✅ R6-4a: anatomy mannequin from MakeHuman CC0 (tools/anatomy/mh.py, fetch_mh.sh; soft per-vertex muscle masks, fibre shader), assets/anatomy front/back + 17 overlays each (268 KB), MuscleMap + ExerciseMedia (3D loop if assets/ex3d/<key>.webp else photo crossfade, play/pause) in ExerciseDetail
- ✅ R6-4b: 3D loops — tools/anatomy/pose.py + moves.py (153 exercises) + loops.py, ex3d.yml (4 shards) → ex3d-assets branch. First run exploded meshes (pose.reset never reset pb.scale → drift compounded over hundreds of aims) and missing limb keys lerped to the other pose (deadlifts/lunges didn't move). Fixed both (+ bicycle crunch for Air_Bike, upright row end pose), re-rendered with 8 shards in ~25 min: 153 loops, 6.6 MB in assets/ex3d. tools/anatomy/diag.py + ex3d-diag.yml for CI debugging.
- ✅ R6-5: Programs — tools/programs/gen.py → assets/programs.json (54 programs, 193 days, phases hyp/strn/fat/base, catalog keys validated). ui/programs: Programs.kt (ProgramLib, Target, ProgramEngine follow/stop/startSession in SharedPreferences 'programs', filters), ProgramsScreen (Train segment 'Programs': search, Filters sheet For/Level/Goal/Equipment/Days, cover cards, active program card), ProgramDetail (Overlay.ProgramDetail: cover, stats, phases, week stepper, day cards, start/switch/stop). Home WorkoutCard shows program up-next. build-106 ✅
- ✅ R6-5b: AI program covers (FLUX pipeline like foodicon) → assets/programs/<id>.webp — tools/programcover (desc/style/post.py) + programcover.yml (9 shards, reuses foodicon gen.py via ICON_DIR, w/h). 54 covers, 1 MB. Some women covers show crop tops despite 'modest' prompt — regenerate those if the user wants. build-109 ✅ (151 MB APK)
- ✅ R7-1: Buddies bundled — tools/piprender/buddylite.py + buddylite.yml (12 shards, ~25 min) → branch buddylite-assets → app/src/main/assets/buddy/<id>/ (8 clips idle/wave/celebrate/thinking/love/sleepy/letsgo/train at 448 px short loops, talk/0-5, portrait.webp; ~1.8 MB each, 36 MB total). Buddy.kt: no downloads, path() maps any Pip clip to the nearest kept move, hasLook only for Pip, persona() for AI. Arena portraits/_cheer/_wave/_sad removed (CastArt uses buddy art; _run stays). Kami lash curves removed (double brows). Chat greeting + Gemini/on-device prompts use the buddy's name.
- ✅ R7-2: Challenge dashboard — ui/arena/PaceChart.kt (paceModel, ProgressRing w/ pace tick, PaceChart cumulative vs even pace + projection + avatar marker, DailyBars best day gold, PaceBadge). ChallengeDetail rebuilt (opaque bg + tap-blocking, ring headline, KPIs daily pace needed/projected finish, chart, daily breakdown, friends race rank/gap/behind, rewards). ChallengeCard uses ring + compact chart. ChallengeShotTest → ui-shots/challenge_dashboard.png. Journeys still use ScenicTrack.
- R8 (user round 8): ✅ offline AI (backend pairs, maxNumTokens 4096, no cloud when offline, real errors, Test button) build-113 · ✅ Programs first, hint, 3D removed, chrome hides under sheets, chips single-line, aurora tier card build-114 · ✅ Friends first Arena tab + FriendsHero (Arena, SocialScreen, Home SOCIAL card un-merged) build-115 · ✅ locked characters colour + CharacterPreview dialog build-116 · ✅ Today screen (Overlay.Today, HealthSync.hourly, calories ring) build-117 · ✅ ChallengeMap (assets/challenge/<scene>.webp from challengemap.yml FLUX, serpentine route, pins, photo marker) build-119 · ☐ program covers: Openverse (stockcovers.yml) too sparse → waiting for user's PEXELS_API_KEY secret. NB: never `git reset --hard` after a trigger commit — use commit-tree to push trigger refs.
- R9: ✅ offline vision fix (TEXT spec for gpu build, DEFAULT = 2.6 GB vision build, legacy() fallback, auto-delete old on install, runtime probe for missing vision encoder) build-120 · ✅ Mood rename · ✅ Friends page = Overlay.Social (tabs Leaderboard/Friends/Challenges/Activity/Account, ActivityFeed via Social.feed/postEvent + firestore feed rule — USER must re-publish rules), Home top-bar people icon, Arena FriendsGlimpse, Arena Friends tab removed build-121 · ✅ covers: Pixabay via PIXABAY_API_KEY secret, stockcovers.yml → stockcovers-cands → assets/programs (+credits.json) build-122
- R10 decisions: journeys = all 4 themes (wonders, city routes, nature & animals, legendary routes/events), 8–12 journeys · reminders = water/move/workout days/wind-down+Monday weigh-in, max 3/day, quiet 22:00–08:00, skip if done · stopwatch = both (Gym Mode timer+calories AND standalone Start activity with MET + body-effect summary) · beta = Firebase App Distribution · audit: report first, ask before removing anything.
- R10-1 ✅ auto-update: update/SelfInstaller.kt (PackageInstaller session, USER_ACTION_NOT_REQUIRED API31+, installs on MainActivity.onStop, MY_PACKAGE_REPLACED notification), UpdateIsland (Dynamic-Island pill that tucks into the Me pill, progress ring on MePill), Settings toggle — build-123
- R10-2 ✅ quick fixes build-124: onboarding catch-up (OnboardingVersion CURRENT=2, ADDED map of step numbers; OnboardingCatchUp asks only new steps for existing users; StepBody extracted — bump CURRENT + add steps whenever onboarding changes) · Cal/kcal unit (EnergyUnits, EnergyUnit.label, Settings → Units → Energy, default Cal) · camera HR: red-chroma channel, best-of estimate, early finish ≥17 s, keep screen on · Arena header/tabs scroll with list · ArrangeDash spring row animation · overlay opaque base (no bottom bleed) · captions trimmed · "· preview" removed from locked characters.
- R10-3 ✅ build-126: reminders/Nudges.kt (smart nudges, own "Gentle nudges" channel, silent+short vibe, budget reserve for workout/wind-down, 90-min gap, fire-time checks; armed from ReminderScheduler.rescheduleNow; app start now reschedules) + Smart nudges card in Reminders · domain/Burn.kt (MET table, kcal, WHO minutes, effects) · ui/activity/StopwatchScreen.kt (ActivityClock persisted, Overlay.Stopwatch, Train "Activity" button, summary saves ActivityEntry) · Gym Mode header live timer+burn, Finish screen burn card.
- R10-4 (in progress): journeys code done build-129 (ui/arena/Journeys.kt: 12 journeys × 4 themes, JourneyCard covers, JourneySheet with stop facts, RouteMap generalised from ChallengeMap, MapArt LRU by path). Art: journeyart.yml run 37236975654 → branch journeyart-assets (journey/<id>_a|_b.webp + sheet.jpg) → pick best → app/src/main/assets/journey/<id>.webp. Then trigger appicon-run (tools/appicon, 6 concepts ×2) → send sheet to user. Audit done (agent report) + Launch Readiness doc https://claude.ai/code/artifact/4f842166-5be6-4dd9-a211-4f5701653142 (audit fixes await user approval).
- Next (old): R10-4 journeys revamp + 6 icon concepts → R10-5 audit report, launch-risk research, beta plan, beta-tester review.
