package com.myfit.tracker.ui.settings

import com.myfit.tracker.ui.theme.Duo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.BuildConfig
import com.myfit.tracker.data.db.ActivityLevel
import com.myfit.tracker.data.db.Experience
import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.DistanceUnit
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.VolumeUnit
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSegmented
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.Stepper
import com.myfit.tracker.ui.entries.FormHeader
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalBackdrop
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.theme.drawBackdrop
import com.myfit.tracker.ui.theme.realBlurSupported
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val backdrop = LocalBackdrop.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) container.write {
            val name = BackgroundImages.import(context, uri)
            if (name != null) container.settings.setCustomBackground(name) else toaster.show("Couldn't read that image")
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = com.myfit.tracker.ui.components.TopBarSpace)) {
                Text("Settings", style = FitType.display, color = th.text)
                Caption("Look & feel, Pip, AI, voice, Gym Mode and units.")
            }
        }

        // ---------- appearance
        item {
            GlassCard {
                CardHeader(Duo.Palette, "Theme", th.fat)
                Spacer(Modifier.height(14.dp))
                Caption("${Themes.all.size} themes · all still images, zero battery cost. Tap one to apply.")
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Themes.all.size, key = { Themes.all[it].id }) { idx ->
                        val t = Themes.all[idx]
                        val sel = t.id == settings.themeId
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(width = 92.dp, height = 150.dp).clip(RoundedCornerShape(20.dp))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) t.accent else Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                    .clickableNoRipple { container.write { container.settings.setTheme(t.id) } }
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    // live miniature of the real backdrop art
                                    val full = backdrop.rootSize
                                    if (full.width > 0) {
                                        val s = size.width / full.width
                                        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                                            drawBackdrop(t, null, t.stillT, full.width, full.height)
                                        }
                                    }
                                    drawRoundRect(t.glassFallback.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.62f),
                                        size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f))
                                    drawRoundRect(t.accent, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.8f),
                                        size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(t.name, style = FitType.caption, color = if (sel) th.text else th.textDim)
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                CardHeader(Duo.Wallpaper, "Background", th.water)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassChip("Theme art", settings.customBackground == null, { container.write { container.settings.setCustomBackground(null) } })
                    GlassChip("My photo", settings.customBackground != null, {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, icon = Duo.Image)
                }
                if (settings.customBackground != null) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Choose a different photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, height = 44.dp)
                }
                Spacer(Modifier.height(14.dp))
                Spacer(Modifier.height(12.dp))
                Text("Motion", style = FitType.section, color = th.text)
                Caption("Pip and glass effects. Themes are always still images. Battery saver keeps everything still.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Smooth", "Balanced", "Battery saver").forEachIndexed { i, label ->
                        GlassChip(label, settings.motion == i, { container.write { container.settings.setMotion(i) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassSlider("Glass tint", "How milky the cards are", settings.glassStrength, 0.4f..1.6f) { v -> container.write { container.settings.setGlassStrength(v) } }
                GlassSlider("Blur amount", "0 = crystal clear, right = heavy frost", settings.blurAmount, 0f..2f) { v -> container.write { container.settings.setBlurAmount(v) } }
                GlassSlider("Dock blur", "How frosted the bottom bar is", settings.dockBlur, 0f..2.5f) { v -> container.write { container.settings.setDockBlur(v) } }
                if (!realBlurSupported) Caption("This phone runs Android 11 or older, so glass uses a frosted fallback instead of live blur.")
            }
        }

        // ---------- pip / AI
        item { PipSettingsCard(container, dev = false) }
        item { OfflineBrainCard() }
        item {
            GlassCard {
                ToggleRow("Shariah & Health", "Prayer times, Qibla, fasting hub, dhikr, halal check", settings.muslim == "yes") { v -> container.write { container.settings.setMuslim(if (v) "yes" else "no") } }
            }
        }
        item {
            GlassButton("Replay Pip's tour", { container.write { container.settings.setTourDone(false) } }, Modifier.fillMaxWidth(), icon = Duo.AutoAwesome, height = 46.dp)
        }
        item { VoiceSettingsCard(container, dev = false) }

        // ---------- gym mode
        item {
            GlassCard {
                CardHeader(Duo.FitnessCenter, "Gym Mode", th.accentBright)
                Spacer(Modifier.height(10.dp))
                ToggleRow("Auto-start rest timer", "Starts after every completed set.", settings.restAutoStart) { container.write { container.settings.setRestAuto(it) } }
                Text("Default rest", style = FitType.body, color = th.text)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120, 180).forEach { r -> GlassChip(if (r < 60) "${r}s" else "${r / 60}:${"%02d".format(r % 60)}", settings.restDefaultSec == r, { container.write { container.settings.setRestSec(r) } }) }
                }
                Caption("Templates can set their own rest per exercise.")
                ToggleRow("Rest-over sound", null, settings.restSound) { container.write { container.settings.setRestSound(it) } }
                ToggleRow("Rest-over vibration", null, settings.restVibrate) { container.write { container.settings.setRestVibrate(it) } }
                ToggleRow("Keep screen on in Gym Mode", null, settings.keepScreenOn) { container.write { container.settings.setKeepScreenOn(it) } }
                Text("Weight +/− step (kg)", style = FitType.body, color = th.text)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0, 1.25, 2.5, 5.0).forEach { st -> GlassChip(Fmt.trim(st, 2), settings.weightStepKg == st, { container.write { container.settings.setWeightStep(st) } }) }
                }
                Caption("In pounds the step is always 5 lb. You can always tap the number to type an exact weight.")
            }
        }

        // ---------- units
        item {
            GlassCard {
                CardHeader(Duo.Straighten, "Units", th.warning)
                Spacer(Modifier.height(12.dp))
                val u = settings.units
                UnitRow("Weight") { GlassSegmented(WeightUnit.entries, u.weight, { it.label }, { container.write { container.settings.setUnits(u.copy(weight = it)) } }, Modifier.width(170.dp)) }
                UnitRow("Body measurements") { GlassSegmented(LengthUnit.entries, u.length, { it.label }, { container.write { container.settings.setUnits(u.copy(length = it)) } }, Modifier.width(170.dp)) }
                UnitRow("Water") { GlassSegmented(VolumeUnit.entries, u.volume, { it.label }, { container.write { container.settings.setUnits(u.copy(volume = it)) } }, Modifier.width(200.dp)) }
                UnitRow("Distance") { GlassSegmented(DistanceUnit.entries, u.distance, { it.label }, { container.write { container.settings.setUnits(u.copy(distance = it)) } }, Modifier.width(170.dp)) }
                Caption("Data is stored in metric at full precision; units only change how it's shown.")
            }
        }

        // ---------- dashboard & behaviour
        item {
            GlassCard {
                CardHeader(Duo.Tune, "Dashboard & behaviour", th.steps)
                Spacer(Modifier.height(10.dp))
                val navS = com.myfit.tracker.ui.nav.LocalNav.current
                Caption("${settings.dashCards.size} of ${DashCard.entries.size} cards shown. Reorder them or switch them off.")
                Spacer(Modifier.height(8.dp))
                GlassButton("Arrange dashboard cards", { navS.push(com.myfit.tracker.ui.nav.Overlay.ArrangeDash) }, icon = Duo.Tune, height = 44.dp)
                Spacer(Modifier.height(6.dp))
                ToggleRow("Haptic feedback", null, settings.haptics) { container.write { container.settings.setHaptics(it) } }
            }
        }

        item { com.myfit.tracker.update.AppUpdatesSection() }
        item {
            GlassCard {
                CardHeader(Duo.Lock, "Privacy", th.textDim)
                Spacer(Modifier.height(10.dp))
                Caption("All data lives only on this phone. No account, no ads, no analytics. Backup & export arrive in a later build. Exercise photos & instructions: free-exercise-db (public domain).")
                Spacer(Modifier.height(6.dp))
                var taps by remember { mutableIntStateOf(0) }
                val toasterV = LocalToaster.current
                Caption("Version ${BuildConfig.VERSION_NAME}", Modifier.clickableNoRipple {
                    if (settings.devMode) { toasterV.show("Developer options are already on"); return@clickableNoRipple }
                    taps++
                    if (taps >= 7) { container.write { container.settings.setDevMode(true) }; toasterV.show("Developer options unlocked") }
                    else if (taps >= 4) toasterV.show("${7 - taps} more taps to unlock developer options")
                }.padding(vertical = 4.dp))
            }
        }
        if (settings.devMode) item {
            val navD = com.myfit.tracker.ui.nav.LocalNav.current
            GlassCard(onClick = { navD.push(com.myfit.tracker.ui.nav.Overlay.DevSettings) }) {
                CardHeader(Duo.Tune, "Developer options", th.textDim) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                Spacer(Modifier.height(6.dp))
                Caption("AI services & keys, voice keys, glass refraction.")
            }
        }
    }
}

/** Hidden developer settings: AI providers & keys, voice service keys, advanced glass. */
@Composable
fun DevSettingsScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    Column(Modifier.fillMaxSize()) {
        com.myfit.tracker.ui.components.OverlayTopBar("Developer options", { nav.pop() }, "For advanced users")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PipSettingsCard(container, dev = true) }
            item { AiProvidersCard(container) }
            item { AiDevCard() }
            item { VoiceSettingsCard(container, dev = true) }
            item {
                GlassCard {
                    CardHeader(Duo.Palette, "Advanced glass", th.fat)
                    Spacer(Modifier.height(8.dp))
                    if (com.myfit.tracker.ui.theme.LiquidGlass.supported)
                        GlassSlider("Refraction", "How strongly the glass edges bend what's behind", settings.refraction, 0f..2f) { v -> container.write { container.settings.setRefraction(v) } }
                }
            }
            item {
                GlassButton("Turn off developer options", { container.write { container.settings.setDevMode(false) }; nav.pop() }, Modifier.fillMaxWidth(), height = 46.dp)
            }
        }
    }
}


/** Me: your profile, body numbers and daily targets (opened from the top-left of every tab). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeScreen(container: AppContainer, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        com.myfit.tracker.ui.components.OverlayTopBar("Me", { nav.pop() }, "Profile, body & daily targets")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        // ---------- profile
        item {
            val p = profile
            GlassCard(onClick = { open(Sheet.EditProfile) }) {
                CardHeader(Duo.Person, p?.name ?: "Profile", th.accentBright) {
                    Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.Edit, null, tint = th.textDim) }
                }
                if (p != null) {
                    val age = p.age + ChronoUnit.YEARS.between(LocalDate.parse(p.ageRecordedOn), Clock.today()).toInt()
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        KV("Age", "$age")
                        KV("Sex", if (p.sex == Sex.MALE) "Male" else "Female")
                        KV("Height", Fmt.length(p.heightCm, settings.units.length))
                        KV("Target", p.targetWeightKg?.let { Fmt.weight(it, settings.units.weight) } ?: "—")
                    }
                    Spacer(Modifier.height(8.dp))
                    Caption("Profile created ${Clock.localDateOf(p.createdAt)} · editing it never changes past entries.")
                }
            }
        }

        item { com.myfit.tracker.ui.social.AccountCard(container) }

        // ---------- targets
        item {
            GlassCard(onClick = { open(Sheet.EditTargets) }) {
                CardHeader(Duo.TrackChanges, "Daily targets", th.success) {
                    Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.Edit, null, tint = th.textDim) }
                }
                Spacer(Modifier.height(12.dp))
                val today = Clock.today()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KV("Water", Targets.on(targets, TargetType.WATER_ML, today)?.let { Fmt.volume(it, settings.units.volume) } ?: "—")
                    KV("Steps", Targets.on(targets, TargetType.STEPS, today)?.let { Fmt.int(it) } ?: "—")
                    KV("Calories", Targets.on(targets, TargetType.CALORIES, today)?.let { "${Fmt.int(it)} kcal" } ?: "—")
                    KV("Protein", Targets.on(targets, TargetType.PROTEIN_G, today)?.let { "${Fmt.int(it)} g" } ?: "—")
                    KV("Sleep", Targets.on(targets, TargetType.SLEEP_MIN, today)?.let { Fmt.duration(it.toLong()) } ?: "—")
                    KV("Workouts/wk", Targets.on(targets, TargetType.WEEKLY_WORKOUTS, today)?.let { Fmt.int(it) } ?: "—")
                }
                Spacer(Modifier.height(8.dp))
                Caption("Target changes apply from today. Past days keep the target that applied then.")
            }
        }

            item { HealthStatusCard(container) }
            item {
                GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Body) }) {
                    CardHeader(Duo.MonitorWeight, "Body & progress photos", th.fat) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                    Spacer(Modifier.height(6.dp))
                    Caption("Weight and body-fat trends, weekly averages, measurements and private before/after photos.")
                }
            }
            item {
                GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Social) }) {
                    CardHeader(Duo.Flag, "Friends & leaderboard", th.accent) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                    Spacer(Modifier.height(6.dp))
                    val u = container.social.user.collectAsState().value
                    Caption(if (u != null) "Signed in as ${u.email ?: u.displayName ?: "you"} · challenges and weekly boards." else "Sign in with Google or email to compete with friends.")
                }
            }
            item {
                GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Records) }) {
                    CardHeader(Duo.EmojiEvents, "Personal records", th.warning) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                    Spacer(Modifier.height(6.dp))
                    Caption("Every PR you've set, detected from your logged sets.")
                }
            }
            item {
                GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Food(null)) }) {
                    CardHeader(Duo.ForkKnife, "Food diary", th.protein) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                    Spacer(Modifier.height(6.dp))
                    Caption("Every meal you've logged, day by day, with calories and macros.")
                }
            }
            item {
                GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Archive) }) {
                    CardHeader(Duo.Inventory2, "Archive", th.textDim) { Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim) } }
                    Spacer(Modifier.height(6.dp))
                    Caption("Archived exercises and templates.")
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    val th = LocalFitTheme.current
    Column {
        Text(k, style = FitType.caption, color = th.textDim)
        Text(v, style = FitType.section, color = th.text)
    }
}

@Composable
private fun UnitRow(label: String, content: @Composable () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.body, color = th.text)
            if (sub != null) Caption(sub)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = th.accent, checkedThumbColor = th.onAccent, uncheckedTrackColor = th.textFaint.copy(alpha = 0.3f), uncheckedBorderColor = Color.Transparent),
        )
    }
}

// ------------------------------------------------------------------ edit sheets

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileForm(c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    val p = c.profileRepo.profile.collectAsState(initial = null).value
    if (p == null) { Caption("Loading…"); return }
    val currentAge = p.age + ChronoUnit.YEARS.between(LocalDate.parse(p.ageRecordedOn), Clock.today()).toInt()
    var name by remember { mutableStateOf(p.name) }
    var age by remember { mutableIntStateOf(currentAge) }
    var height by remember { mutableStateOf(Fmt.trim(Units.cmTo(p.heightCm, units.length), 1)) }
    var hasTarget by remember { mutableStateOf(p.targetWeightKg != null) }
    var target by remember { mutableStateOf(p.targetWeightKg?.let { Fmt.trim(Units.kgTo(it, units.weight), 1) } ?: "") }
    var activity by remember { mutableStateOf(p.activityLevel) }
    var experience by remember { mutableStateOf(p.experience) }
    var days by remember { mutableIntStateOf(p.workoutDaysMask) }
    var wTime by remember { mutableIntStateOf(p.workoutTimeMin ?: (18 * 60)) }
    var wake by remember { mutableIntStateOf(p.wakeTimeMin) }
    var sleep by remember { mutableIntStateOf(p.sleepTimeMin) }

    FormHeader("Edit profile", Duo.Person, th.accentBright, false, null)
    NotesField(name, { name = it.take(40) }, "Name")
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Age", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
        Stepper(age, { age = it }, 13..100)
    }
    Spacer(Modifier.height(12.dp))
    Text("Height", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    NumberInput(height, { height = it }, units.length.label, big = false)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip("Target weight", hasTarget, { hasTarget = true }); GlassChip("No target", !hasTarget, { hasTarget = false })
    }
    if (hasTarget) { Spacer(Modifier.height(8.dp)); NumberInput(target, { target = it }, units.weight.label, big = false) }
    Spacer(Modifier.height(14.dp))
    Text("Activity level", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ActivityLevel.SEDENTARY to "Sedentary", ActivityLevel.LIGHT to "Light", ActivityLevel.MODERATE to "Moderate", ActivityLevel.ACTIVE to "Active", ActivityLevel.VERY_ACTIVE to "Very active")
            .forEach { (k, v) -> GlassChip(v, activity == k, { activity = k }) }
    }
    Spacer(Modifier.height(14.dp))
    Text("Experience", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Experience.BEGINNER to "Beginner", Experience.INTERMEDIATE to "Intermediate", Experience.ADVANCED to "Advanced")
            .forEach { (k, v) -> GlassChip(v, experience == k, { experience = k }) }
    }
    Spacer(Modifier.height(14.dp))
    Text("Workout days", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d -> GlassChip(d, days and (1 shl i) != 0, { days = days xor (1 shl i) }) }
    }
    Spacer(Modifier.height(10.dp))
    listOf(Triple("Workout time", wTime) { v: Int -> wTime = v }, Triple("Wake-up", wake) { v: Int -> wake = v }, Triple("Sleep", sleep) { v: Int -> sleep = v }).forEach { (l, v, set) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l, style = FitType.body, color = th.text, modifier = Modifier.weight(1f)); MinuteOfDayChip(v, set)
        }
    }
    Spacer(Modifier.height(20.dp))
    val h = height.toDoubleOrNull()?.let { Units.toCm(it, units.length) }
    val t = target.toDoubleOrNull()?.let { Units.toKg(it, units.weight) }
    val valid = name.isNotBlank() && h != null && h in 90.0..250.0 && (!hasTarget || (t != null && t in 25.0..350.0))
    AccentButton("Save profile", {
        c.write {
            c.profileRepo.updateProfile(p.copy(
                name = name.trim(), age = age, ageRecordedOn = Clock.dateKey(Clock.today()), heightCm = h!!,
                targetWeightKg = if (hasTarget) t else null, activityLevel = activity, experience = experience,
                workoutDaysMask = days, workoutTimeMin = wTime, wakeTimeMin = wake, sleepTimeMin = sleep,
            ))
        }
        toaster.show("Profile saved"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
}

@Composable
fun EditTargetsForm(c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    val rows = c.profileRepo.targets.collectAsState(initial = null).value
    if (rows == null) { Caption("Loading…"); return }
    val today = Clock.today()
    fun cur(t: String) = Targets.on(rows, t, today)
    val waterUnit = if (units.volume == VolumeUnit.FL_OZ) VolumeUnit.FL_OZ else VolumeUnit.L
    var water by remember { mutableStateOf(cur(TargetType.WATER_ML)?.let { Fmt.trim(Units.mlTo(it, waterUnit), 2) } ?: "") }
    var steps by remember { mutableStateOf(cur(TargetType.STEPS)?.roundToInt()?.toString() ?: "") }
    var kcal by remember { mutableStateOf(cur(TargetType.CALORIES)?.roundToInt()?.toString() ?: "") }
    var protein by remember { mutableStateOf(cur(TargetType.PROTEIN_G)?.roundToInt()?.toString() ?: "") }
    var sleepH by remember { mutableStateOf(cur(TargetType.SLEEP_MIN)?.let { Fmt.trim(it / 60.0, 2) } ?: "") }
    var workouts by remember { mutableDoubleStateOf(cur(TargetType.WEEKLY_WORKOUTS) ?: 4.0) }

    FormHeader("Daily targets", Duo.TrackChanges, th.success, false, null)
    Caption("Saving creates a new target version starting today (${today}). Earlier days are still judged against the old targets.")
    Spacer(Modifier.height(14.dp))
    @Composable fun field(label: String, v: String, unit: String, dec: Boolean, set: (String) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = FitType.section, color = th.text, modifier = Modifier.width(96.dp))
            NumberInput(v, set, unit, Modifier.weight(1f), decimal = dec, big = false)
        }
    }
    field("Water", water, waterUnit.label, true) { water = it }
    field("Steps", steps, "steps", false) { steps = it }
    field("Calories", kcal, "kcal", false) { kcal = it }
    field("Protein", protein, "g", false) { protein = it }
    field("Sleep", sleepH, "hours", true) { sleepH = it }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Workouts / week", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
        Stepper(workouts.roundToInt(), { workouts = it.toDouble() }, 0..14)
    }
    Spacer(Modifier.height(20.dp))
    val vals = mapOf(
        TargetType.WATER_ML to water.toDoubleOrNull()?.let { Units.toMl(it, waterUnit) },
        TargetType.STEPS to steps.toDoubleOrNull(),
        TargetType.CALORIES to kcal.toDoubleOrNull(),
        TargetType.PROTEIN_G to protein.toDoubleOrNull(),
        TargetType.SLEEP_MIN to sleepH.toDoubleOrNull()?.times(60.0),
        TargetType.WEEKLY_WORKOUTS to workouts,
    )
    val valid = vals.values.all { it != null && it >= 0 }
    AccentButton("Save targets", {
        c.write { vals.forEach { (k, v) -> c.profileRepo.setTarget(k, v!!) } }
        toaster.show("Targets updated from today"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
}


@Composable
private fun PipSettingsCard(container: AppContainer, dev: Boolean) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var key by remember(settings.geminiKey) { mutableStateOf(settings.geminiKey) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    GlassCard {
        CardHeader(Duo.AutoAwesome, "Pip · AI buddy", th.accentBright)
        Spacer(Modifier.height(10.dp))
        Caption("Questions about your logs are answered offline from your own data. General health & fitness questions go to an online AI with only a short, question-specific summary — never your full history or notes. Online answers are tagged.")
        Spacer(Modifier.height(12.dp))
        if (dev) {
        Text("Gemini API key", style = FitType.label, color = th.textDim)
        if (settings.geminiKey.isBlank() && com.myfit.tracker.BuildConfig.GEMINI_KEY.isNotBlank()) Caption("Built-in key active ✓ — you only need your own key if you want to use a different one.", color = th.success)
        Spacer(Modifier.height(6.dp))
        Glass(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    key, { key = it.trim() }, singleLine = true,
                    textStyle = FitType.body.copy(color = th.text),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(th.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (key.isEmpty()) Text("Paste your key", style = FitType.body, color = th.textFaint); inner() } },
                )
                Text("Paste", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
                    clipboard.getText()?.text?.trim()?.let { key = it }
                }.padding(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (key != settings.geminiKey) "Save key" else "Saved", {
                container.write { container.settings.setGeminiKey(key) }; toaster.show(if (key.isBlank()) "Key removed" else "Key saved on this phone")
            }, height = 44.dp)
            GlassButton(if (testing) "Testing…" else "Test", {
                if (testing || key.isBlank()) return@GlassButton
                testing = true; status = null
                scope.launch {
                    status = runCatching { container.settings.setGeminiKey(key); "Working ✓ using ${container.pipBrain.testKey(key)}" }
                        .getOrElse { "Not working: ${it.message}" }
                    testing = false
                }
            }, height = 44.dp)
        }
        status?.let { Spacer(Modifier.height(6.dp)); Caption(it, color = if (it.startsWith("Working")) th.success else th.danger) }
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow("Online answers", "Off = Pip only answers from your data, fully offline.", settings.onlineAi) { container.write { container.settings.setOnlineAi(it) } }
        Spacer(Modifier.height(6.dp))
        ToggleRow("Pip speaks", "Pip reads its chat replies aloud. Also a mute button in the chat.", settings.pipVoice) { container.write { container.settings.setPipVoice(it) } }
        Spacer(Modifier.height(6.dp))
        if (dev) Caption("Recommended: in Google Cloud Console restrict this key to Android app com.myfit.tracker with SHA-1 ${container.pipBrain.certSha1.chunked(2).joinToString(":")}", color = th.textFaint)
    }
}

@Composable
private fun GlassSlider(title: String, hint: String, value: Float, range: ClosedFloatingPointRange<Float>, onDone: (Float) -> Unit) {
    val th = LocalFitTheme.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.section, color = th.text)
            Caption(hint)
        }
        Text("${(v * 100).toInt()}%", style = FitType.label, color = th.textDim)
    }
    Slider(
        v, { v = it }, valueRange = range,
        onValueChangeFinished = { onDone(v) },
        colors = SliderDefaults.colors(thumbColor = th.accentBright, activeTrackColor = th.accent, inactiveTrackColor = th.textFaint.copy(alpha = 0.3f)),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun VoiceSettingsCard(container: AppContainer, dev: Boolean) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val voice = container.pipVoice
    val packState by voice.pack.state.collectAsState()
    val lastEngine by voice.lastEngine.collectAsState()
    val lastError by voice.lastError.collectAsState()
    var key by remember(settings.elevenKey) { mutableStateOf(settings.elevenKey) }
    var status by remember { mutableStateOf<String?>(null) }
    GlassCard {
        CardHeader(Duo.AutoAwesome, "Pip's voice", th.water)
        Spacer(Modifier.height(8.dp))
        Caption("Auto tries ElevenLabs (most realistic), then Azure (natural English + real Urdu, free 500k characters a month), then the on-device voice — instant and fully offline.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Auto", "On-device", "Phone voice").forEachIndexed { i, label ->
                GlassChip(label, settings.voiceEngine == i, { container.write { container.settings.setVoiceEngine(i) } })
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("On-device voice pack", style = FitType.section, color = th.text)
        when (val st = packState) {
            com.myfit.tracker.ai.voice.VoicePack.State.Missing -> {
                Caption("Bright, youthful neural voice (English + Hindustani for Urdu). One-time download, about ${com.myfit.tracker.ai.voice.VoicePack.SIZE_MB} MB — use Wi-Fi.")
                Spacer(Modifier.height(8.dp))
                GlassButton("Download voice pack", { voice.installPack() }, height = 44.dp)
            }
            is com.myfit.tracker.ai.voice.VoicePack.State.Downloading -> {
                Caption("Downloading & installing… ${(st.progress * 100).toInt()}%")
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { st.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = th.accent, trackColor = th.textFaint.copy(alpha = 0.25f),
                )
                Spacer(Modifier.height(6.dp))
                Text("Cancel", style = FitType.label, color = th.textDim, modifier = Modifier.clickableNoRipple { voice.pack.cancel() }.padding(4.dp))
            }
            com.myfit.tracker.ai.voice.VoicePack.State.Ready -> {
                Caption("Installed ✓ — works offline.", color = th.success)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Remove", { voice.pack.delete(); toaster.show("Voice pack removed") }, height = 40.dp)
                }
            }
            is com.myfit.tracker.ai.voice.VoicePack.State.Failed -> {
                Caption(st.message, color = th.danger)
                Spacer(Modifier.height(8.dp))
                GlassButton("Try again", { voice.installPack() }, height = 44.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (dev) {
        Text("ElevenLabs key (optional)", style = FitType.section, color = th.text)
        if (settings.elevenKey.isBlank() && com.myfit.tracker.BuildConfig.ELEVEN_KEY.isNotBlank()) Caption("Built-in key active ✓", color = th.success)
        Caption("Free plan ≈ 10,000 characters a month (roughly 100–150 replies). When it runs out Pip switches to the on-device voice by itself.")
        Spacer(Modifier.height(6.dp))
        Glass(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    key, { key = it.trim() }, singleLine = true,
                    textStyle = FitType.body.copy(color = th.text),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(th.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (key.isEmpty()) Text("Paste your ElevenLabs key", style = FitType.body, color = th.textFaint); inner() } },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (key != settings.elevenKey) "Save key" else "Saved", {
                container.write { container.settings.setElevenKey(key) }; voice.resetEleven()
                toaster.show(if (key.isBlank()) "Key removed" else "Key saved on this phone")
            }, height = 44.dp)
            GlassButton("Check", {
                if (key.isBlank()) return@GlassButton
                status = "Checking…"
                scope.launch {
                    status = runCatching { voice.checkEleven(key) }.fold(
                        { q -> if (q == null) "Key works ✓" else "Key works ✓ · ${q.second - q.first} of ${q.second} characters left this month" },
                        { e -> "Not working: ${e.message}" },
                    )
                }
            }, height = 44.dp)
        }
        status?.let { Spacer(Modifier.height(6.dp)); Caption(it, color = if (it.startsWith("Key works")) th.success else th.textDim) }
        }
        lastError?.let { Spacer(Modifier.height(6.dp)); Caption(it, color = th.warning) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Test voice", { voice.speak("Hi! I'm Pip, your fitness buddy. Ready to crush today's goals?", force = true) }, height = 44.dp)
            Spacer(Modifier.width(10.dp))
            lastEngine?.let { Caption("Last spoke with: $it") }
        }
    }
}

/** Health Connect / sensors status with a one-tap fix if something isn't allowed yet. */
@Composable
private fun HealthStatusCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val ctx = LocalContext.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hs = container.healthSync
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tick) { granted = hs.granted() }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val health = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { res ->
        granted = res
        runtime.launch(com.myfit.tracker.ui.onboarding.runtimePermissions())
        if (res.isNotEmpty()) scope.launch { val r = hs.sync(30); container.settings.setLastHealthSync(Clock.now(), r.message) }
    }
    val allHc = hs.isAvailable && hs.dataPermissions.all { it in granted }
    val allRuntime = com.myfit.tracker.ui.onboarding.runtimePermissions().all { com.myfit.tracker.ui.onboarding.hasPerm(ctx, it) }
    GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Activity) }) {
        CardHeader(Duo.Watch, "Samsung Health & sensors", th.accent) {
            if (allHc && allRuntime) androidx.compose.material3.Icon(Duo.CheckCircle, null, tint = th.success, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Caption(when {
            !hs.isAvailable -> "Health Connect isn't available on this phone — steps come from the phone sensor." + if (!allRuntime) " Some phone permissions are still off." else ""
            allHc && allRuntime -> "Everything is connected. Data syncs by itself every 30 minutes" + (settings.lastHealthSync?.let { " · last ${Clock.localDateOf(it)}" } ?: "") + "."
            else -> "Some data isn't allowed yet, so it can't sync automatically."
        })
        if (!(allHc && allRuntime) && (hs.isAvailable || !allRuntime)) {
            Spacer(Modifier.height(10.dp))
            GlassButton("Allow everything", {
                if (hs.isAvailable) runCatching { health.launch(hs.allPermissions) } else runtime.launch(com.myfit.tracker.ui.onboarding.runtimePermissions())
            }, icon = Duo.Check, height = 44.dp)
        }
    }
}

/** Backup AI services: Pip and food photos switch to these automatically when Gemini is slow or busy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiProvidersCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val router = container.aiRouter
    GlassCard {
        CardHeader(Duo.Cloud, "AI services", th.water)
        Spacer(Modifier.height(8.dp))
        Caption("Pip's chat and food photos use the first service that answers. If one is slow, busy or out of free quota, the next one takes over automatically.")
        Spacer(Modifier.height(10.dp))
        Text("Try first", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Auto (Gemini)", "groq" to "Groq", "openrouter" to "OpenRouter", "mistral" to "Mistral").forEach { (id, label) ->
                GlassChip(label, settings.aiPrimary == id, { container.write { container.settings.setAiPrimary(id) } })
            }
        }
        Spacer(Modifier.height(4.dp))
        Caption("Groq is the fastest. Order after your pick: Gemini → Groq → OpenRouter → Mistral.", color = th.textFaint)
        listOf(
            Triple("groq", "Groq", settings.groqKey to com.myfit.tracker.BuildConfig.GROQ_KEY),
            Triple("openrouter", "OpenRouter", settings.openRouterKey to com.myfit.tracker.BuildConfig.OPENROUTER_KEY),
            Triple("mistral", "Mistral", settings.mistralKey to com.myfit.tracker.BuildConfig.MISTRAL_KEY),
            Triple("azure", "Azure Speech (voice)", settings.azureKey to com.myfit.tracker.BuildConfig.AZURE_SPEECH_KEY),
        ).forEach { (id, label, keys) ->
            val (own, builtIn) = keys
            var key by remember(own) { mutableStateOf(own) }
            var status by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                val active = own.isNotBlank() || builtIn.isNotBlank()
                Text(if (own.isNotBlank()) "Your key" else if (builtIn.isNotBlank()) "Built-in ✓" else "Not set", style = FitType.caption, color = if (active) th.success else th.textFaint)
            }
            Spacer(Modifier.height(6.dp))
            Glass(Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        key, { key = it.trim() }, singleLine = true,
                        textStyle = FitType.body.copy(color = th.text),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(th.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (key.isEmpty()) Text(if (builtIn.isNotBlank()) "Using built-in key" else "Paste $label key", style = FitType.body, color = th.textFaint); inner() } },
                    )
                    if (key != own) Text("Save", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
                        container.write { container.settings.setAiKey(id, key) }
                        if (id == "azure") container.pipVoice.resetEleven() else router.compat(id).reset()
                        toaster.show(if (key.isBlank()) "Key removed" else "$label key saved on this phone")
                    }.padding(6.dp))
                }
            }
            if (id == "azure") {
                var region by remember(settings.azureRegion) { mutableStateOf(settings.azureRegion) }
                Spacer(Modifier.height(6.dp))
                Glass(Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(
                            region, { region = it.trim().lowercase() }, singleLine = true,
                            textStyle = FitType.body.copy(color = th.text), cursorBrush = androidx.compose.ui.graphics.SolidColor(th.accent), modifier = Modifier.weight(1f),
                            decorationBox = { inner -> Box { if (region.isEmpty()) Text(com.myfit.tracker.BuildConfig.AZURE_SPEECH_REGION.ifBlank { "Region, e.g. centralindia" }, style = FitType.body, color = th.textFaint); inner() } },
                        )
                        if (region != settings.azureRegion) Text("Save", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
                            container.write { container.settings.setAiKey("azure_region", region) }; container.pipVoice.resetEleven(); toaster.show("Region saved")
                        }.padding(6.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                GlassButton("Test voice", { container.pipVoice.speak("Hi! I'm Pip. Chalo, aaj workout karte hain!", force = true) }, height = 40.dp)
            } else {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("Test", {
                        val k = key.ifBlank { builtIn }
                        if (k.isBlank()) { status = "Add a key first"; return@GlassButton }
                        status = "Testing…"
                        scope.launch { status = runCatching { "Working ✓ ${router.test(id, k)}" }.getOrElse { "Not working: ${com.myfit.tracker.ai.AiRouter.shortMsg(it)}" } }
                    }, height = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    status?.let { Caption(it, color = if (it.startsWith("Working")) th.success else th.textDim) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        router.lastProvider?.let { Caption("Last answer came from $it.", color = th.textFaint) }
    }
}
