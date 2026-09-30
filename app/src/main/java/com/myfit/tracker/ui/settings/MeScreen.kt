package com.myfit.tracker.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
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
fun MeScreen(container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
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
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Text("Me", style = FitType.display, color = th.text)
                Caption("Profile, targets, look & feel.")
            }
        }

        // ---------- profile
        item {
            val p = profile
            GlassCard(onClick = { open(Sheet.EditProfile) }) {
                CardHeader(Icons.Rounded.Person, p?.name ?: "Profile", th.accentBright) {
                    Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Icons.Rounded.Edit, null, tint = th.textDim) }
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

        // ---------- targets
        item {
            GlassCard(onClick = { open(Sheet.EditTargets) }) {
                CardHeader(Icons.Rounded.TrackChanges, "Daily targets", th.success) {
                    Box(Modifier.size(20.dp)) { androidx.compose.material3.Icon(Icons.Rounded.Edit, null, tint = th.textDim) }
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

        // ---------- appearance
        item {
            GlassCard {
                CardHeader(Icons.Rounded.Palette, "Theme", th.fat)
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Themes.all.forEach { t ->
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
                                            drawBackdrop(t, null, backdrop.time.floatValue, full.width, full.height)
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
                CardHeader(Icons.Rounded.Wallpaper, "Background", th.water)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassChip("Theme art", settings.customBackground == null, { container.write { container.settings.setCustomBackground(null) } })
                    GlassChip("My photo", settings.customBackground != null, {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, icon = Icons.Rounded.Image)
                }
                if (settings.customBackground != null) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Choose a different photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, height = 44.dp)
                }
                Spacer(Modifier.height(14.dp))
                ToggleRow("Animated background", "Slowly drifting light. Turn off to save battery.", settings.animatedBackground) {
                    container.write { container.settings.setAnimated(it) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Glass intensity", style = FitType.section, color = th.text)
                var g by remember(settings.glassStrength) { mutableFloatStateOf(settings.glassStrength) }
                Slider(
                    g, { g = it }, valueRange = 0.4f..1.6f,
                    onValueChangeFinished = { container.write { container.settings.setGlassStrength(g) } },
                    colors = SliderDefaults.colors(thumbColor = th.accentBright, activeTrackColor = th.accent, inactiveTrackColor = th.textFaint.copy(alpha = 0.3f)),
                )
                if (!realBlurSupported) Caption("This phone runs Android 11 or older, so glass uses a frosted fallback instead of live blur.")
            }
        }

        // ---------- units
        item {
            GlassCard {
                CardHeader(Icons.Rounded.Straighten, "Units", th.warning)
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
                CardHeader(Icons.Rounded.Tune, "Dashboard & behaviour", th.steps)
                Spacer(Modifier.height(10.dp))
                DashCard.entries.forEach { card ->
                    ToggleRow(card.label, null, card in settings.dashCards) { on ->
                        container.write { container.settings.setDashCards(if (on) settings.dashCards + card else settings.dashCards - card) }
                    }
                }
                ToggleRow("Pip on the dashboard", "Your buddy. Only ever quotes numbers you've logged.", settings.pipEnabled) { container.write { container.settings.setPip(it) } }
                ToggleRow("Haptic feedback", null, settings.haptics) { container.write { container.settings.setHaptics(it) } }
            }
        }

        item {
            GlassCard {
                CardHeader(Icons.Rounded.Lock, "Privacy", th.textDim)
                Spacer(Modifier.height(10.dp))
                Caption("All data lives only on this phone. No account, no ads, no analytics. Backup & export arrive in a later build.")
                Spacer(Modifier.height(6.dp))
                Caption("Version ${BuildConfig.VERSION_NAME}")
            }
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

    FormHeader("Edit profile", Icons.Rounded.Person, th.accentBright, false, null)
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

    FormHeader("Daily targets", Icons.Rounded.TrackChanges, th.success, false, null)
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
