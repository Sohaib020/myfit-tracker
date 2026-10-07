package com.myfit.tracker.ui.onboarding

import com.myfit.tracker.ui.theme.Duo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.ActivityLevel
import com.myfit.tracker.data.db.Experience
import com.myfit.tracker.data.db.FitnessGoal
import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.db.UserProfile
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.EnergyEstimate
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.glucose.DiabetesType
import com.myfit.tracker.ui.glucose.GlucoseConfigStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.GlassSegmented
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.RulerPicker
import com.myfit.tracker.ui.components.WheelPicker
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.roundToInt

@Stable
private class SetupState(units: UnitPrefs) {
    var name by mutableStateOf("")
    var age by mutableIntStateOf(28)
    var dobDay by mutableIntStateOf(1)
    var dobMonth by mutableIntStateOf(1)
    var dobYear by mutableIntStateOf(java.time.LocalDate.now().year - 28)
    var trainDays by mutableIntStateOf(4)
    val dob: java.time.LocalDate get() {
        val ym = java.time.YearMonth.of(dobYear, dobMonth)
        return ym.atDay(dobDay.coerceAtMost(ym.lengthOfMonth()))
    }
    fun syncAge() { age = java.time.Period.between(dob, java.time.LocalDate.now()).years.coerceIn(13, 100) }
    var sex by mutableStateOf<String?>(null)
    var lengthUnit by mutableStateOf(if (units.length == LengthUnit.CM) LengthUnit.CM else LengthUnit.IN)
    var heightCm by mutableDoubleStateOf(175.0)
    var weightUnit by mutableStateOf(units.weight)
    var weightDisplay by mutableDoubleStateOf(if (units.weight == WeightUnit.KG) 75.0 else 165.0)
    var hasTarget by mutableStateOf(true)
    var targetDisplay by mutableDoubleStateOf(if (units.weight == WeightUnit.KG) 72.0 else 159.0)
    var goals by mutableStateOf(setOf<String>())
    var activity by mutableStateOf(ActivityLevel.MODERATE)
    var experience by mutableStateOf(Experience.INTERMEDIATE)
    var workoutDays by mutableIntStateOf(0b0011111)        // Mon–Fri
    var workoutTime by mutableIntStateOf(18 * 60)
    var wake by mutableIntStateOf(7 * 60)
    var sleep by mutableIntStateOf(23 * 60)
    var waterL by mutableStateOf("3.0")
    var steps by mutableStateOf("8000")
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var sleepH by mutableStateOf("8")
    var suggested by mutableStateOf(false)
    var muslim by mutableStateOf<String?>(null)
    var diabetes by mutableStateOf<String?>(null)          // DiabetesTypes value ("none", "type1", …)
    var treatment by mutableStateOf("")
    var cgm by mutableStateOf<Boolean?>(null)
    var low by mutableStateOf("70")
    var high by mutableStateOf("180")

    val weightKg get() = Units.toKg(weightDisplay, weightUnit)
    val targetKg get() = if (hasTarget) Units.toKg(targetDisplay, weightUnit) else null
}

private const val STEPS = 14

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(container: AppContainer, units: UnitPrefs) {
    val th = LocalFitTheme.current
    val s = remember { SetupState(units) }
    var step by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // prefill the name from the signed-in account
    val account by container.social.user.collectAsState()
    LaunchedEffect(account) { if (s.name.isBlank()) account?.displayName?.takeIf { it.isNotBlank() }?.let { s.name = it.substringBefore(' ').take(40) } }

    BackHandler(enabled = step > 0) { step-- }

    val canNext = when (step) {
        1 -> s.name.isNotBlank()
        2 -> s.age in 13..100
        3 -> s.sex != null
        4 -> s.diabetes != null && (s.diabetes == "none" || (s.treatment.isNotEmpty() && s.cgm != null &&
            (s.low.toIntOrNull() ?: 0) in 50..120 && (s.high.toIntOrNull() ?: 0) in 120..300 && (s.low.toIntOrNull() ?: 0) < (s.high.toIntOrNull() ?: 0)))
        5 -> true
        9 -> s.goals.isNotEmpty()
        13 -> listOf(s.waterL, s.steps, s.calories, s.protein, s.sleepH).all { it.toDoubleOrNull() != null && it.toDouble() > 0 }
        else -> true
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) {
            GlassProgressBar(step / STEPS.toFloat(), th.accent, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), height = 6.dp)
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(0.85f, 300f)) { it * dir / 3 } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it * dir / 3 } + fadeOut(tween(180)))
            },
            label = "setup",
        ) { st ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StepBody(st, s, container)
                Spacer(Modifier.height(24.dp))
            }
        }

        // bottom bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) GlassIconButton(Duo.ArrowBack, { step-- }, size = 52.dp)
            Spacer(Modifier.weight(1f))
            when (step) {
                0 -> AccentButton("Get started", { step = 1 }, Modifier.fillMaxWidth(), icon = Duo.ArrowForward)
                STEPS -> AccentButton(if (saving) "Saving…" else "Start tracking", {
                    if (saving) return@AccentButton
                    saving = true
                    val today = Clock.dateKey(Clock.today())
                    val profile = UserProfile(
                        name = s.name.trim(), age = s.age, ageRecordedOn = today, sex = s.sex ?: Sex.MALE,
                        heightCm = s.heightCm, startWeightKg = s.weightKg, targetWeightKg = s.targetKg,
                        activityLevel = s.activity, experience = s.experience, goals = s.goals.joinToString(","),
                        workoutDaysMask = s.workoutDays, workoutTimeMin = s.workoutTime, wakeTimeMin = s.wake,
                        sleepTimeMin = s.sleep, createdAt = 0, updatedAt = 0,
                    )
                    val targets = mapOf(
                        TargetType.WATER_ML to s.waterL.toDouble() * 1000.0,
                        TargetType.STEPS to s.steps.toDouble(),
                        TargetType.CALORIES to s.calories.toDouble(),
                        TargetType.PROTEIN_G to s.protein.toDouble(),
                        TargetType.SLEEP_MIN to s.sleepH.toDouble() * 60.0,
                        TargetType.WEEKLY_WORKOUTS to Integer.bitCount(s.workoutDays).toDouble(),
                    )
                    val ctx0 = appCtx
                    container.write {
                        val d = s.diabetes ?: "none"
                        container.settings.setDiabetesType(d)
                        if (d != "none") {
                            container.settings.setGlucose(true)
                            val gt = when (d) { "type1" -> DiabetesType.TYPE1; "type2" -> DiabetesType.TYPE2; "prediabetes" -> DiabetesType.PREDIABETES; "gestational" -> DiabetesType.GESTATIONAL; else -> DiabetesType.OTHER }
                            GlucoseConfigStore.save(ctx0, GlucoseConfigStore.get(ctx0).copy(type = gt, low = s.low.toIntOrNull() ?: 70, high = s.high.toIntOrNull() ?: 180,
                                treatment = s.treatment, cgm = s.cgm == true, setupDone = true))
                        }
                        if (s.sex == Sex.FEMALE) container.settings.setCycle(true)
                        // Shariah & Health is asked in a popup after Pip's tour (stays "unset" until then)
                        if (d != "none") { com.myfit.tracker.domain.HealthProfile.load(ctx0); if ("diabetes" !in com.myfit.tracker.domain.HealthProfile.selected.value) com.myfit.tracker.domain.HealthProfile.toggle(ctx0, "diabetes") }
                        container.settings.setUnits(UnitPrefs(weight = s.weightUnit, length = s.lengthUnit, volume = units.volume, distance = units.distance))
                        OnboardingVersion.markDone(ctx0)   // before the profile exists, so no catch-up flashes
                        ctx0.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE).edit().putBoolean("perms_v3", true).apply()
                        container.profileRepo.createProfile(profile, targets)
                    }
                }, Modifier.width(200.dp), enabled = !saving)
                else -> AccentButton("Next", { if (canNext) step++ }, Modifier.width(150.dp), icon = Duo.ArrowForward, enabled = canNext)
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.StepBody(st: Int, s: SetupState, container: AppContainer) {
    val th = LocalFitTheme.current
    when (st) {
        0 -> Welcome()
        1 -> {
            Header("What should I call you?", "Pip will greet you by name.")
            Glass(Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(22.dp)) {
                BasicTextField(
                    s.name, { s.name = it.take(40) }, singleLine = true,
                    textStyle = FitType.title.copy(color = th.text),
                    cursorBrush = SolidColor(th.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp).fillMaxWidth(),
                    decorationBox = { inner -> Box { if (s.name.isEmpty()) Text("Your name", style = FitType.title, color = th.textFaint); inner() } },
                )
            }
        }
        2 -> {
            Header("When were you born?", "Scroll the wheels to set your date of birth. Used for your targets — never shown to friends.")
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val nowY = java.time.LocalDate.now().year
            Row(Modifier.fillMaxWidth()) {
                listOf("DAY" to 0.8f, "MONTH" to 1.2f, "YEAR" to 1f).forEach { (t, w) -> Text(t, style = FitType.overline, color = th.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(w)) }
            }
            Spacer(Modifier.height(6.dp))
            // one selection band across all three wheels (iOS-style date picker)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Glass(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {}
                Row(Modifier.fillMaxWidth()) {
                    WheelPicker(31, s.dobDay - 1, { s.dobDay = it + 1; s.syncAge() }, { "${it + 1}" }, Modifier.weight(0.8f), itemHeight = 46.dp, highlight = false)
                    WheelPicker(12, s.dobMonth - 1, { s.dobMonth = it + 1; s.syncAge() }, { months[it] }, Modifier.weight(1.2f), itemHeight = 46.dp, highlight = false)
                    WheelPicker(88, (s.dobYear - (nowY - 100)).coerceIn(0, 87), { s.dobYear = nowY - 100 + it; s.syncAge() }, { "${nowY - 100 + it}" }, Modifier.weight(1f), itemHeight = 46.dp, highlight = false)
                }
            }
            Spacer(Modifier.height(22.dp))
            LaunchedEffect(Unit) { s.syncAge() }
            Glass(shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(th.success), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, style = FitType.caption) }
                    Spacer(Modifier.width(8.dp))
                    Text("You're ", style = FitType.section, color = th.text)
                    AnimatedContent(s.age, transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(spring(0.7f, 400f)) { if (up) it else -it } + fadeIn()).togetherWith(slideOutVertically(tween(150)) { if (up) -it else it } + fadeOut(tween(120)))
                    }, label = "age") { a -> Text("$a", style = FitType.section, color = th.accentBright) }
                    Text(" years old", style = FitType.section, color = th.text)
                }
            }
        }
        3 -> {
            Header("What's your sex?", "Used for energy estimates and body-composition context.")
            SexCard("Male", Duo.Male, s.sex == Sex.MALE) { s.sex = Sex.MALE }
            Spacer(Modifier.height(16.dp))
            SexCard("Female", Duo.Female, s.sex == Sex.FEMALE) { s.sex = Sex.FEMALE }
        }
        4 -> {
            Header("Do you have diabetes?", "Only people who manage diabetes see the blood-sugar tools. You can change this any time in Me.")
            val opts = buildList {
                add("none" to "No"); add("type1" to "Type 1"); add("type2" to "Type 2"); add("prediabetes" to "Prediabetes")
                if (s.sex == Sex.FEMALE) add("gestational" to "Gestational (pregnancy)")
                add("other" to "Other / not sure")
            }
            opts.forEach { (k, v) -> OptionRow(v, s.diabetes == k) { s.diabetes = k }; Spacer(Modifier.height(8.dp)) }
            if (s.diabetes != null && s.diabetes != "none") {
                Spacer(Modifier.height(14.dp))
                Text("How do you manage it?", style = FitType.label, color = th.textDim)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("insulin" to "Insulin", "tablets" to "Tablets", "both" to "Insulin + tablets", "diet" to "Diet & exercise").forEach { (k, v) ->
                        GlassChip(v, s.treatment == k, { s.treatment = k })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Do you wear a glucose sensor (CGM)?", style = FitType.label, color = th.textDim)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Yes", s.cgm == true, { s.cgm = true })
                    GlassChip("No, finger-prick", s.cgm == false, { s.cgm = false })
                }
                Spacer(Modifier.height(14.dp))
                Text("Target range (mg/dL)", style = FitType.label, color = th.textDim)
                TargetField("Low", s.low, "mg/dL", decimal = false) { s.low = it }
                TargetField("High", s.high, "mg/dL", decimal = false) { s.high = it }
                Caption("Most adults use 70–180 (ADA). Ask your doctor for your own range. MyFit records readings only — it never calculates doses.")
            }
        }
        5 -> {
            Header("Anything about your health or diet?", "Pick all that apply — or skip. Meal plans, workouts, your trainer and nutritionist adapt to it. Stays on this phone.")
            Column(Modifier.fillMaxWidth()) { com.myfit.tracker.ui.settings.HealthProfilePicker(container, compact = true) }
        }
        6 -> {
            Header("What's your height?", "Used for better progress tracking.")
            if (s.lengthUnit == LengthUnit.CM) {
                WheelPicker(101, (s.heightCm.roundToInt() - 120).coerceIn(0, 100), { s.heightCm = (it + 120).toDouble() }, { "${it + 120} cm" }, Modifier.fillMaxWidth())
            } else {
                val inches = (s.heightCm / Units.CM_PER_IN).roundToInt()
                WheelPicker(43, (inches - 48).coerceIn(0, 42), { s.heightCm = (it + 48) * Units.CM_PER_IN }, { val i = it + 48; "${i / 12}' ${i % 12}\"  ·  $i in" }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            GlassSegmented(listOf(LengthUnit.CM, LengthUnit.IN), s.lengthUnit, { it.label }, { s.lengthUnit = it }, Modifier.width(180.dp))
        }
        7 -> {
            Header("What's your current weight?", "This becomes your first weigh-in. You can log more any time.")
            WeightPicker(s.weightDisplay, s.weightUnit, { s.weightDisplay = it }) { u ->
                s.weightDisplay = round1(Units.kgTo(s.weightKg, u)); s.targetDisplay = round1(Units.kgTo(Units.toKg(s.targetDisplay, s.weightUnit), u)); s.weightUnit = u
            }
        }
        8 -> {
            Header("Target weight", "Optional. Used only to show distance to goal — never to judge a single weigh-in.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassChip("Set a target", s.hasTarget, { s.hasTarget = true })
                GlassChip("No target", !s.hasTarget, { s.hasTarget = false })
            }
            Spacer(Modifier.height(20.dp))
            if (s.hasTarget) {
                WeightPicker(s.targetDisplay, s.weightUnit, { s.targetDisplay = it }, null)
                val diff = s.targetDisplay - s.weightDisplay
                Caption("${Fmt.signed(diff)} ${s.weightUnit.label} from today's weight")
            }
        }
        9 -> {
            Header("Your fitness goal", "Choose what best matches your training journey.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FitnessGoal.all.forEach { g ->
                    GlassChip(g, g in s.goals, { s.goals = if (g in s.goals) s.goals - g else s.goals + g })
                }
            }
        }
        10 -> {
            Header("Activity & experience", "Outside the gym, how active is a normal day?")
            listOf(
                ActivityLevel.SEDENTARY to "Mostly sitting", ActivityLevel.LIGHT to "Light — some walking",
                ActivityLevel.MODERATE to "Moderate — on my feet often", ActivityLevel.ACTIVE to "Active — physical job / lots of walking",
                ActivityLevel.VERY_ACTIVE to "Very active — hard physical work daily",
            ).forEach { (k, v) -> OptionRow(v, s.activity == k) { s.activity = k }; Spacer(Modifier.height(8.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Training experience", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Experience.BEGINNER to "Beginner", Experience.INTERMEDIATE to "Intermediate", Experience.ADVANCED to "Advanced").forEach { (k, v) ->
                    GlassChip(v, s.experience == k, { s.experience = k })
                }
            }
        }
        11 -> {
            Header("How many days can you train each week?", "Pick a number you can realistically hit. Consistency beats ambition.")
            listOf(2 to "Light, recovery-focused.", 3 to "Balanced full body or upper/lower.", 4 to "Upper/lower or push/pull split.", 5 to "Targeted muscle splits.", 6 to "Athlete-level high volume.").forEach { (n, d) ->
                Glass(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(20.dp), onClick = {
                    s.trainDays = n
                    // pre-fill the week with a sensible spread (editable on the next page)
                    s.workoutDays = when (n) { 2 -> 0b0001001; 3 -> 0b0010101; 4 -> 0b0011011; 5 -> 0b0011111; else -> 0b0111111 }
                }) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$n days / week", style = FitType.section, color = th.text)
                            Caption(d)
                        }
                        if (s.trainDays == n) Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(th.accent), contentAlignment = Alignment.Center) { Text("✓", color = th.onAccent, style = FitType.caption) }
                    }
                    if (s.trainDays == n) Box(Modifier.matchParentSize().border(1.5.dp, th.accent, RoundedCornerShape(20.dp)))
                }
            }
        }
        12 -> {
            Header("Your week", "Planned workout days and your daily rhythm. Reminders respect your sleep hours.")
            val days = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    val on = s.workoutDays and (1 shl i) != 0
                    GlassChip(d, on, { s.workoutDays = s.workoutDays xor (1 shl i) })
                }
            }
            Spacer(Modifier.height(24.dp))
            TimeRow("Typical workout time") { MinuteOfDayChip(s.workoutTime) { s.workoutTime = it } }
            TimeRow("Wake-up time") { MinuteOfDayChip(s.wake) { s.wake = it } }
            TimeRow("Sleep time") { MinuteOfDayChip(s.sleep) { s.sleep = it } }
        }
        13 -> {
            Header("Daily targets", "Set them yourself, or let me suggest a starting point you can edit.")
            GlassButton("Suggest from my profile", {
                val male = s.sex == Sex.MALE
                val maint = EnergyEstimate.maintenance(s.weightKg, s.heightCm, s.age, male, s.activity)
                val adj = when {
                    "Lose Fat" in s.goals -> -400.0
                    "Build Muscle" in s.goals -> 250.0
                    else -> 0.0
                }
                s.calories = ((maint + adj) / 10).roundToInt().times(10).toString()
                s.protein = (s.weightKg * 1.8).roundToInt().toString()
                s.waterL = Fmt.num(s.weightKg * 0.035, 1)
                s.suggested = true
            }, icon = Duo.AutoAwesome)
            if (s.suggested) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DataBadge(DataKind.ESTIMATED)
                    Spacer(Modifier.width(8.dp))
                    Caption("Mifflin–St Jeor × activity; protein 1.8 g/kg; water 35 ml/kg. Starting points, not prescriptions.")
                }
            }
            Spacer(Modifier.height(16.dp))
            TargetField("Water to drink", s.waterL, "L/day") { s.waterL = it }
            TargetField("Steps", s.steps, "per day", decimal = false) { s.steps = it }
            TargetField("Calories to eat", s.calories, "${com.myfit.tracker.domain.EnergyUnit.label}/day", decimal = false) { s.calories = it }
            Caption("How much food to eat each day (intake). Calories you burn by moving are tracked separately.", Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp))
            TargetField("Protein to eat", s.protein, "g/day", decimal = false) { s.protein = it }
            TargetField("Sleep", s.sleepH, "hours") { s.sleepH = it }
        }
        14 -> {
            Spacer(Modifier.height(40.dp))
            Pip(PipMood.EXCITED, size = 150.dp)
            Spacer(Modifier.height(16.dp))
            Text("You're all set, ${s.name.trim()}!", style = FitType.display, color = th.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Caption("I'm Pip. I only ever talk about numbers you've actually logged — no guesses dressed up as facts.", Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Onboarding is versioned. When an update adds or changes setup questions, bump [CURRENT] and list the new
 * step numbers under it in [ADDED]; existing users are asked just those, once, on the first launch after updating.
 */
object OnboardingVersion {
    const val CURRENT = 2
    private val ADDED = mapOf(2 to listOf(2, 11, 12))   // v2: date of birth, training days, your week
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE)
    /** Profiles created before versioning existed count as v1. */
    fun stored(c: android.content.Context) = prefs(c).getInt("version", 1)
    fun markDone(c: android.content.Context) = prefs(c).edit().putInt("version", CURRENT).apply()
    fun pendingSteps(c: android.content.Context): List<Int> = ((stored(c) + 1)..CURRENT).flatMap { ADDED[it].orEmpty() }.distinct().sorted()
}

/** "A few new questions" — shown to existing users after an update that changed onboarding. Saves into their profile. */
@Composable
fun OnboardingCatchUp(container: AppContainer, profile: UserProfile, units: UnitPrefs, steps: List<Int>, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val s = remember {
        SetupState(units).apply {
            name = profile.name; sex = profile.sex
            val now = java.time.LocalDate.now()
            age = profile.age; dobYear = now.year - profile.age; dobMonth = now.monthValue; dobDay = 1
            workoutDays = profile.workoutDaysMask; trainDays = Integer.bitCount(profile.workoutDaysMask).coerceIn(2, 6)
            workoutTime = profile.workoutTimeMin ?: workoutTime; wake = profile.wakeTimeMin; sleep = profile.sleepTimeMin
        }
    }
    var i by remember { mutableIntStateOf(-1) }      // -1 = intro
    var saving by remember { mutableStateOf(false) }
    BackHandler(enabled = i > -1) { i-- }
    fun finish() {
        if (saving) return
        saving = true
        container.write {
            container.profileRepo.updateProfile(profile.copy(
                age = s.age, ageRecordedOn = Clock.dateKey(Clock.today()), workoutDaysMask = s.workoutDays,
                workoutTimeMin = s.workoutTime, wakeTimeMin = s.wake, sleepTimeMin = s.sleep,
            ))
            OnboardingVersion.markDone(ctx)
        }
        onDone()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (i >= 0) GlassProgressBar((i + 1) / steps.size.toFloat(), th.accent, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), height = 6.dp)
        AnimatedContent(i, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(spring(0.85f, 300f)) { it * dir / 3 } + fadeIn(tween(250)))
                .togetherWith(slideOutHorizontally(tween(220)) { -it * dir / 3 } + fadeOut(tween(180)))
        }, label = "catchup") { k ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (k < 0) {
                    Spacer(Modifier.height(48.dp))
                    Pip(PipMood.EXCITED, size = 130.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("A few new questions", style = FitType.display, color = th.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Caption("This update improves your plan. ${steps.size} quick question${if (steps.size == 1) "" else "s"} and you're back in.", Modifier.padding(horizontal = 12.dp))
                } else StepBody(steps[k], s)
                Spacer(Modifier.height(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (i >= 0) GlassIconButton(Duo.ArrowBack, { i-- }, size = 52.dp)
            else Text("Later", style = FitType.label, color = th.textDim, modifier = Modifier.clickable { onDone() }.padding(12.dp))
            Spacer(Modifier.weight(1f))
            val last = i == steps.lastIndex
            AccentButton(if (i < 0) "Let's go" else if (last) (if (saving) "Saving…" else "Done") else "Next",
                { if (last) finish() else i++ }, Modifier.width(160.dp), icon = if (last) null else Duo.ArrowForward, enabled = !saving)
        }
    }
}

private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

@Composable
private fun Welcome() {
    val th = LocalFitTheme.current
    Spacer(Modifier.height(60.dp))
    Pip(PipMood.WAVE, size = 170.dp)
    Spacer(Modifier.height(24.dp))
    Text("STEP INTO STRENGTH", style = FitType.overline, color = th.textDim)
    Spacer(Modifier.height(8.dp))
    Text("MYFIT\nTRACKER", style = FitType.hero.copy(fontSize = FitType.hero.fontSize * 1.3f, lineHeight = FitType.hero.lineHeight * 1.2f), color = th.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    Text("Your private logbook for training, food, water, sleep and body — recorded once, calculated honestly.", style = FitType.body, color = th.textDim, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Caption("Everything stays on this phone. No account needed.")
}

@Composable
private fun Header(title: String, sub: String) {
    val th = LocalFitTheme.current
    Spacer(Modifier.height(28.dp))
    Text(title, style = FitType.display.copy(fontSize = FitType.title.fontSize * 1.3f), color = th.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(sub, style = FitType.body, color = th.textDim, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun SexCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.size(170.dp), shape = RoundedCornerShape(30.dp), onClick = onClick) {
        if (selected) {
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(Brush.linearGradient(listOf(th.accentBright, th.accent, th.accent.copy(alpha = 0.7f))))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), 0f, size.height * 0.5f))
            })
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) th.onAccent else th.text, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, style = FitType.section, color = if (selected) th.onAccent else th.text)
        }
    }
}

@Composable
private fun WeightPicker(display: Double, unit: WeightUnit, onChange: (Double) -> Unit, onUnit: ((WeightUnit) -> Unit)?) {
    val th = LocalFitTheme.current
    Glass(Modifier.size(width = 190.dp, height = 130.dp), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
            Text(Fmt.num(display, 1), style = FitType.display.copy(fontSize = FitType.display.fontSize * 1.4f), color = th.text)
            Spacer(Modifier.width(4.dp))
            Text(unit.label, style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    val (mn, mx) = if (unit == WeightUnit.KG) 30.0 to 250.0 else 66.0 to 551.0
    RulerPicker(display, onChange, mn, mx)
    Caption("Drag the ruler · 0.1 ${unit.label} precision")
    if (onUnit != null) {
        Spacer(Modifier.height(16.dp))
        GlassSegmented(listOf(WeightUnit.KG, WeightUnit.LB), unit, { it.label }, onUnit, Modifier.width(180.dp))
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    // grows with its text (long labels / big font sizes wrap onto 2 lines instead of running under the radio)
    Glass(Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        if (selected) Box(Modifier.matchParentSize().drawBehind { drawRect(th.accent.copy(alpha = 0.35f)) })
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(20.dp).drawBehind {
                drawCircle(if (selected) th.accentBright else th.textFaint, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                if (selected) drawCircle(th.accentBright, size.minDimension * 0.28f)
            })
        }
    }
}

@Composable
private fun TimeRow(label: String, content: @Composable () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun TargetField(label: String, value: String, unit: String, decimal: Boolean = true, onChange: (String) -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.section, color = th.text, modifier = Modifier.width(96.dp))
        NumberInput(value, onChange, unit, Modifier.weight(1f), decimal = decimal, big = false)
    }
}
