# Common rules for MyFit builder agents (read fully)

Project: Android app "MyFit Tracker" — Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.02, Compose 1.7), Room (DB version 4), MVVM-ish, minSdk 26, compileSdk 36. Package com.myfit.tracker. Repo: github.com/Sohaib020/myfit-tracker (public). Single user's private, offline-first fitness app with a glass UI ("Glass", "GlassCard", "GlassChip", "GlassButton", "AccentButton", "IconBubble", "Caption", "CardHeader", "OverlayTopBar", "GlassSheet" components in ui/components and ui/theme), theme via LocalFitTheme, settings via LocalSettings / container.settings (SettingsStore), nav via LocalNav (Overlay sealed interface in ui/nav/Nav.kt, overlays rendered in ui/MyFitRoot.kt), toasts via LocalToaster, writes via container.write { }.

## Building
- There is NO Android SDK locally. You cannot run gradle. Compile only via GitHub Actions CI.
- You are in your own git worktree. First `git checkout -b agent/<your-name>` from the current main (pull first: `git fetch origin && git reset --hard origin/main` if needed). Commit and `git push -u origin agent/<your-name>`; CI builds agent/** branches (no release published).
- Wait for the result with: `bash /tmp/claude-0/-home-claude-myfit-tracker/a3bd9ddc-9b2d-5774-bc12-e1c6a3a44cb1/scratchpad/wfb.sh agent/<your-name>` (prints conclusion + `e:` compile errors). Fix and push again until it says success. Kotlin often reports only some errors per run — read carefully and fix all you can per round.
- Commit messages end with:
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01TDToodQbPJg86keHcVnDSc

## File ownership (other agents work in parallel on other areas; keep merges clean)
- Only edit the files/dirs your brief says you own. If you truly need a change in a shared file (ui/MyFitRoot.kt, ui/nav/Nav.kt, data/prefs/SettingsStore.kt, data/db/AppDatabase.kt, data/db/*Entities*.kt, AndroidManifest.xml, app/build.gradle.kts), keep it minimal and additive (append, don't reorder/reformat), and list every such change in your final report.
- NEVER change Room entities or the DB version (a migration exists for v4 and must match exactly). If you need extra fields, store them in SettingsStore/DataStore or a small JSON file.
- Don't touch other agents' areas. Don't delete files you didn't create unless your brief says so.

## Product rules
- Accuracy: never invent the user's numbers; label estimates as estimates; health features get clear "not medical advice / see a doctor" wording where relevant; no insulin dose calculators; cycle predictions are "not contraception".
- Performance/heat matters a lot (the user's phone heated up before): no continuous animations when idle, no per-frame heavy work, stop sensors when not visible.
- UI polish matters: the user is picky; keep the existing glass style, consistent spacing (16dp gutters, 12–14dp between cards), text never truncated awkwardly, nothing overlapping.
- User is in Pakistan (Urdu/Roman-Urdu friendly), uses a Samsung S23 Ultra.

## Final report (your final message)
Branch name + final green commit sha, what you built (bullets), what's not done / caveats, every shared-file change, and any integration points the coordinator must wire (e.g. "push Overlay.X from the dashboard").
