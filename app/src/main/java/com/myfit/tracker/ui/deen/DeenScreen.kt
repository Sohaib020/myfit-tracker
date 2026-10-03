package com.myfit.tracker.ui.deen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.myfit.tracker.AppContainer
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

private val DeenGreen = Color(0xFF2FA37A)
private val DeenGold = Color(0xFFD9A441)

/** One-shot tab request from the Home card (e.g. "qibla", "dhikr"). */
object DeenLaunch { @Volatile var tab: Int? = null }

@Composable
fun DeenScreen(container: AppContainer) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(DeenLaunch.tab.also { DeenLaunch.tab = null } ?: 0) }
    var refresh by remember { mutableIntStateOf(0) }
    val hijri = remember(refresh) { Hijri.today(ctx) }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Shariah & Health", { nav.pop() }, hijri.label)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf("Prayer & Qibla", "Fasting", "Dhikr & Sunnah", "Hajj, Umrah & Halal")
            items(tabs.size) { i -> GlassChip(tabs[i], tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> { item { PrayerHero(refresh) { refresh++ } }; item { QiblaCard(refresh) }; item { PrayerSettings { refresh++ } } }
                1 -> { item { FastingToday(container) }; item { SunnahFasts(hijri) }; item { QadaCard() }; item { NightWater(container) } }
                2 -> { item { TasbeehCard() }; item { Checklist("Morning & evening adhkar", "adhkar", Adhkar) }; item { Checklist("Sunnah habits", "sunnah", SunnahHabits) } }
                else -> { item { UmrahTracker() }; item { HalalChecker() } }
            }
            item {
                Caption("Times are calculated from the sun for your location; your local masjid may differ by a few minutes. For rulings, ask a qualified scholar.",
                    Modifier.padding(horizontal = 6.dp), color = LocalFitTheme.current.textFaint)
            }
        }
    }
}

// ------------------------------------------------------------------ prayer

@SuppressLint("MissingPermission")
private fun lastLocation(ctx: Context): Place? {
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return null
    val near = Cities.minByOrNull { PrayerCalc.distanceKm(loc.latitude, loc.longitude, it.lat, it.lng) }
    val name = near?.takeIf { PrayerCalc.distanceKm(loc.latitude, loc.longitude, it.lat, it.lng) < 40 }?.name ?: "My location"
    return Place(name, loc.latitude, loc.longitude)
}

@Composable
private fun LocationPicker(onSet: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val p = if (ok) lastLocation(ctx) else null
        if (p != null) { DeenPrefs.setPlace(ctx, p); ReminderScheduler.reschedule(ctx); onSet() }
        else toaster.show(if (ok) "Couldn't get a location fix — pick your city below" else "Pick your city below")
    }
    Text("Where are you?", style = FitType.section, color = th.text)
    Caption("Used only on this phone to calculate prayer times and the Qibla.")
    Spacer(Modifier.height(8.dp))
    AccentButton("Use my location", { perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth(), icon = Duo.TrackChanges, height = 46.dp)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Cities) { c -> GlassChip(c.name, false, { DeenPrefs.setPlace(ctx, c); ReminderScheduler.reschedule(ctx); onSet() }) }
    }
}

@Composable
private fun PrayerHero(refresh: Int, onChange: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val place = remember(refresh) { DeenPrefs.place(ctx) }
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(20_000) } }
    val today = remember(now.hour) { LocalDate.now() }
    val times = remember(refresh, today) { prayerTimes(ctx, today) }
    var logTick by remember { mutableIntStateOf(0) }
    GlassCard {
        if (place == null || times == null) { LocationPicker(onChange); return@GlassCard }
        val nowMin = now.hour * 60 + now.minute
        val next = times.entries.firstOrNull { it.key.isSalah && it.value > nowMin } ?: times.entries.first()
        val until = (next.value - nowMin).let { if (it < 0) it + 1440 else it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEXT · ${place.name.uppercase()}", style = FitType.overline, color = DeenGreen)
                Text("${next.key.label}  ${next.key.urdu}", style = FitType.title, color = th.text)
                Caption("${clock(next.value)} · in ${until / 60}h ${until % 60}m")
            }
            Text(place.name, style = FitType.caption, color = th.accentBright, modifier = Modifier.clickableNoRipple { DeenPrefs.setPlace(ctx, Place("", 0.0, 0.0)); ctx.getSharedPreferences("deen_prefs", Context.MODE_PRIVATE).edit().remove("lat").apply(); onChange() }.padding(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        times.forEach { (pr, min) ->
            val done = remember(logTick, today) { DeenPrefs.prayed(ctx, today, pr) }
            val isNext = pr == next.key
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (isNext) DeenGreen.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pr.label, style = FitType.body, color = if (pr.isSalah) th.text else th.textDim, modifier = Modifier.width(84.dp))
                Text(pr.urdu, style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
                Text(clock(min), style = FitType.label, color = th.text)
                if (pr.isSalah) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (done) DeenGreen else th.textFaint.copy(alpha = 0.2f))
                            .clickableNoRipple { tick(); DeenPrefs.setPrayed(ctx, today, pr, !done); logTick++ },
                        contentAlignment = Alignment.Center,
                    ) { if (done) Icon(Duo.Check, "Prayed", tint = Color.White, modifier = Modifier.size(16.dp)) }
                } else Spacer(Modifier.width(38.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        val count = Prayer.entries.count { it.isSalah && DeenPrefs.prayed(ctx, today, it) }.let { logTick.let { _ -> it } }
        Caption("$count of 5 prayers ticked today · private to this phone")
    }
}

@Composable
private fun PrayerSettings(onChange: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var t by remember { mutableIntStateOf(0) }
    val method = remember(t) { DeenPrefs.method(ctx) }
    val hanafi = remember(t) { DeenPrefs.hanafi(ctx) }
    val adhanOn = remember(t) { DeenPrefs.adhanOn(ctx) }
    fun changed() { t++; ReminderScheduler.reschedule(ctx); onChange() }
    GlassCard {
        CardHeader(Duo.Tune, "Prayer settings", DeenGreen)
        Spacer(Modifier.height(10.dp))
        Text("Calculation method", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        CalcMethod.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().clickableNoRipple { DeenPrefs.setMethod(ctx, m); changed() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (m == method) DeenGreen else th.textFaint.copy(alpha = 0.25f)))
                Spacer(Modifier.width(10.dp))
                Text(m.label, style = FitType.body, color = th.text)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Asr", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Hanafi (later)", hanafi, { DeenPrefs.setHanafi(ctx, true); changed() })
            GlassChip("Shafi'i, Maliki, Hanbali", !hanafi, { DeenPrefs.setHanafi(ctx, false); changed() })
        }
        Spacer(Modifier.height(10.dp))
        Text("Hijri date adjustment (moon sighting)", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        val adj = remember(t) { DeenPrefs.hijriAdjust(ctx) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-1, 0, 1).forEach { v -> GlassChip(if (v > 0) "+$v day" else if (v < 0) "$v day" else "None", adj == v, { DeenPrefs.setHijriAdjust(ctx, v); changed() }) }
        }
        Spacer(Modifier.height(12.dp))
        com.myfit.tracker.ui.settings.ToggleRow("Prayer-time reminders", "A notification at each prayer time", adhanOn) { v -> DeenPrefs.setAdhanOn(ctx, v); changed() }
        if (adhanOn) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Prayer.entries.filter { it.isSalah }.forEach { p ->
                val on = remember(t) { DeenPrefs.adhan(ctx, p) }
                GlassChip(p.label, on, { DeenPrefs.setAdhan(ctx, p, !on); changed() })
            }
        }
    }
}

// ------------------------------------------------------------------ qibla

@Composable
private fun QiblaCard(refresh: Int) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val place = remember(refresh) { DeenPrefs.place(ctx) } ?: return
    val qibla = remember(place) { PrayerCalc.qibla(place.lat, place.lng).toFloat() }
    val km = remember(place) { PrayerCalc.distanceKm(place.lat, place.lng, 21.4225, 39.8262).roundToInt() }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) { hasSensor = false; onDispose { } }
        else {
            val rot = FloatArray(9); val ori = FloatArray(3)
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, e.values)
                    SensorManager.getOrientation(rot, ori)
                    val a = Math.toDegrees(ori[0].toDouble()).toFloat().let { if (it < 0) it + 360 else it }
                    // smooth across the 0/360 seam
                    var d = a - azimuth; if (d > 180) d -= 360; if (d < -180) d += 360
                    azimuth = (azimuth + d * 0.15f + 360) % 360
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }
    var diff = qibla - azimuth; if (diff > 180) diff -= 360; if (diff < -180) diff += 360
    val aligned = hasSensor && abs(diff) < 4f
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) { if (aligned && !wasAligned) tick(); wasAligned = aligned }
    val dial by animateFloatAsState(-azimuth, spring(0.9f, 120f), label = "dial")
    GlassCard {
        CardHeader(Duo.TrackChanges, "Qibla", DeenGold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp).rotate(if (hasSensor) dial else 0f)) {
                val r = size.minDimension / 2
                drawCircle(th.textFaint.copy(alpha = 0.25f), r, style = Stroke(2.dp.toPx()))
                for (i in 0 until 72) rotate(i * 5f) {
                    val long = i % 18 == 0
                    drawLine(th.textFaint.copy(alpha = if (long) 0.9f else 0.4f), Offset(center.x, center.y - r + 4.dp.toPx()), Offset(center.x, center.y - r + (if (long) 16 else 9).dp.toPx()), 2.dp.toPx())
                }
                // qibla needle (rotated within the dial so it points to Makkah when the dial is north-up)
                rotate(qibla) {
                    drawLine(if (aligned) DeenGreen else DeenGold, center, Offset(center.x, center.y - r * 0.78f), 7.dp.toPx(), StrokeCap.Round)
                    drawCircle(if (aligned) DeenGreen else DeenGold, 11.dp.toPx(), Offset(center.x, center.y - r * 0.80f))
                }
                drawCircle(th.text, 6.dp.toPx(), center)
            }
            Text("N", style = FitType.label, color = th.danger, modifier = Modifier.align(Alignment.TopCenter))
        }
        Text(
            if (!hasSensor) "Face ${qibla.roundToInt()}° from north (no compass sensor on this phone)"
            else if (aligned) "You're facing the Qibla ✓" else "Turn ${if (diff > 0) "right" else "left"} ${abs(diff).roundToInt()}°",
            style = FitType.section, color = if (aligned) DeenGreen else th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Caption("Qibla ${qibla.roundToInt()}° from true north · Makkah is ${"%,d".format(km)} km away. Keep the phone flat and away from metal; wave it in a figure-8 if the needle jumps.", Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ fasting

@Composable
private fun FastingToday(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val tick = rememberTick()
    val today = remember { LocalDate.now() }
    val times = remember { prayerTimes(ctx, today) }
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(20_000) } }
    var t by remember { mutableIntStateOf(0) }
    val fasting = remember(t) { today.toString() in DeenPrefs.fasted(ctx) }
    val suhoor = remember(t) { DeenPrefs.suhoorAlert(ctx) }
    GlassCard {
        CardHeader(Duo.Bedtime, "Today's fast", DeenGold)
        Spacer(Modifier.height(10.dp))
        if (times == null) { Caption("Set your location in Prayer & Qibla to see suhoor and iftar times."); return@GlassCard }
        val fajr = times.getValue(Prayer.FAJR); val maghrib = times.getValue(Prayer.MAGHRIB)
        val nowMin = now.hour * 60 + now.minute
        Row {
            Column(Modifier.weight(1f)) { Caption("Suhoor ends (Fajr)"); Text(clock(fajr), style = FitType.title, color = th.text) }
            Column(Modifier.weight(1f)) { Caption("Iftar (Maghrib)"); Text(clock(maghrib), style = FitType.title, color = th.text) }
        }
        Spacer(Modifier.height(6.dp))
        val msg = when {
            nowMin < fajr -> "Suhoor ends in ${(fajr - nowMin) / 60}h ${(fajr - nowMin) % 60}m"
            nowMin < maghrib -> "Iftar in ${(maghrib - nowMin) / 60}h ${(maghrib - nowMin) % 60}m"
            else -> "Fast complete for today — may Allah accept it."
        }
        Text(msg, style = FitType.section, color = DeenGreen)
        if (nowMin in fajr until maghrib) {
            Spacer(Modifier.height(6.dp))
            com.myfit.tracker.ui.components.GlassProgressBar(((nowMin - fajr).toFloat() / (maghrib - fajr)).coerceIn(0f, 1f), DeenGold, height = 8.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton(if (fasting) "Fasting today ✓" else "I'm fasting today", { tick(); DeenPrefs.setFasted(ctx, today, !fasting); t++ }, Modifier.weight(1f), height = 44.dp)
            GlassButton("Fast timer", { nav.push(Overlay.Fasting) }, Modifier.weight(1f), icon = Duo.Timer, height = 44.dp)
        }
        Spacer(Modifier.height(6.dp))
        com.myfit.tracker.ui.settings.ToggleRow("Suhoor alert", "45 minutes before Fajr", suhoor) { v -> DeenPrefs.setSuhoorAlert(ctx, v); ReminderScheduler.reschedule(ctx); t++ }
        val year = remember(t) { DeenPrefs.fasted(ctx).count { it.startsWith(today.year.toString()) } }
        Caption("$year fasts logged in ${today.year}")
    }
}

@Composable
private fun SunnahFasts(hijri: Hijri.Day) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val list = remember { Hijri.upcomingSunnahFasts(ctx, 30) }
    GlassCard {
        CardHeader(Duo.CalendarMonth, "Sunnah fasts — next 30 days", DeenGreen)
        Spacer(Modifier.height(8.dp))
        if (hijri.month == 9) { Text("Ramadan Mubarak 🌙 — day ${hijri.day} of Ramadan", style = FitType.section, color = DeenGold); Spacer(Modifier.height(6.dp)) }
        list.take(10).forEach { (d, why) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), style = FitType.label, color = th.text, modifier = Modifier.width(100.dp))
                Text(why, style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) Caption("None in the next 30 days.")
        Caption("Hijri dates are calculated (Umm al-Qura); local moon sighting can shift them by a day — adjust in Prayer settings if needed.", color = th.textFaint)
    }
}

@Composable
private fun QadaCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    var owed by remember { mutableIntStateOf(DeenPrefs.qadaOwed(ctx)) }
    GlassCard {
        CardHeader(Duo.EditNote, "Qada (make-up) fasts", DeenGold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$owed", style = FitType.title, color = th.text)
                Caption(if (owed == 0) "Nothing owed" else "fast${if (owed == 1) "" else "s"} still to make up")
            }
            GlassButton("−", { if (owed > 0) { tick(); owed--; DeenPrefs.setQadaOwed(ctx, owed) } }, height = 44.dp)
            Spacer(Modifier.width(8.dp))
            GlassButton("+", { tick(); owed++; DeenPrefs.setQadaOwed(ctx, owed) }, height = 44.dp)
        }
        Caption("Tap − each time you make one up.", color = th.textFaint)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NightWater(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val times = remember { prayerTimes(ctx) } ?: return
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    val target = com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.WATER_ML, LocalDate.now()) ?: 2500.0
    val start = times.getValue(Prayer.MAGHRIB); val end = times.getValue(Prayer.FAJR) + 1440
    val glasses = (target / 250).roundToInt().coerceIn(6, 14)
    val step = (end - start) / glasses
    GlassCard {
        CardHeader(Duo.WaterDrop, "Night hydration plan", th.water)
        Spacer(Modifier.height(6.dp))
        Caption("$glasses glasses (${(glasses * 250 / 1000.0)} L) between iftar and suhoor, spread out so your body can absorb them:")
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until glasses).forEach { i ->
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(th.water.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(clock((start + i * step) % 1440), style = FitType.caption, color = th.text)
                }
            }
        }
        Caption("Break the fast with dates and water, as the Prophet ﷺ did, then eat slowly.", color = th.textFaint)
    }
}

// ------------------------------------------------------------------ dhikr

private val Phrases = listOf(
    "SubhanAllah" to "سُبْحَانَ ٱللَّٰهِ", "Alhamdulillah" to "ٱلْحَمْدُ لِلَّٰهِ", "Allahu Akbar" to "ٱللَّٰهُ أَكْبَرُ",
    "Astaghfirullah" to "أَسْتَغْفِرُ ٱللَّٰهَ", "La ilaha illallah" to "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", "Salawat" to "ﷺ",
)

@Composable
private fun TasbeehCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val today = remember { LocalDate.now() }
    var phrase by remember { mutableStateOf(Phrases.first()) }
    var target by remember { mutableIntStateOf(33) }
    var count by remember(phrase) { mutableIntStateOf(0) }
    var total by remember(phrase) { mutableIntStateOf(DeenPrefs.dhikr(ctx, today, phrase.first)) }
    val vib = remember { ctx.getSystemService(android.os.Vibrator::class.java) }
    val press by animateFloatAsState(if (count % 2 == 0) 1f else 0.97f, spring(0.4f, 900f), label = "tasbeeh")
    GlassCard {
        CardHeader(Duo.RadioButtonUnchecked, "Tasbeeh", DeenGreen)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(Phrases) { p -> GlassChip(p.first, p == phrase, { phrase = p }) } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(210.dp).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape)
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(DeenGreen.copy(alpha = 0.55f), DeenGreen.copy(alpha = 0.15f))))
                        val sweep = 360f * (count % target).toFloat() / target
                        drawArc(DeenGold, -90f, if (count > 0 && count % target == 0) 360f else sweep, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    .clickableNoRipple {
                        count++; total++
                        DeenPrefs.addDhikr(ctx, today, phrase.first, 1)
                        if (count % target == 0) runCatching { vib?.vibrate(android.os.VibrationEffect.createOneShot(180, 255)) } else tick()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phrase.second, style = FitType.section, color = Color.White, textAlign = TextAlign.Center)
                    Text("$count", style = FitType.display, color = Color.White)
                    Text("of $target · tap", style = FitType.caption, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(33, 99, 100).forEach { n -> GlassChip("$n", target == n, { target = n }) }
            Spacer(Modifier.weight(1f))
            Text("Reset", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { count = 0 }.padding(6.dp))
        }
        Caption("Today: $total × ${phrase.first}")
    }
}


private val Adhkar = listOf(
    "ayatkursi" to "Ayat al-Kursi (morning & evening)",
    "quls" to "Surah Ikhlas, Falaq & Nas — 3 times each",
    "istighfar" to "Sayyid al-Istighfar",
    "subhan100" to "SubhanAllahi wa bihamdihi — 100 times",
    "hasbi" to "HasbiyAllahu la ilaha illa Huwa — 7 times",
    "sleep" to "Adhkar before sleep",
)

private val SunnahHabits = listOf(
    "miswak" to "Miswak / brush before salah",
    "bismillah" to "Bismillah and eat with the right hand",
    "water3" to "Drink water sitting, in three sips",
    "dates" to "Dates (and honey) in the day",
    "walk" to "Walk to the masjid",
    "early" to "Sleep early on your right side",
    "third" to "Leave a third of the stomach for breath",
)

@Composable
private fun Checklist(title: String, key: String, items: List<Pair<String, String>>) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val today = remember { LocalDate.now() }
    var t by remember { mutableIntStateOf(0) }
    GlassCard {
        val done = remember(t) { items.count { DeenPrefs.checked(ctx, today, key + it.first) } }
        CardHeader(Duo.CheckCircle, title, DeenGreen) { Caption("$done/${items.size}") }
        Spacer(Modifier.height(6.dp))
        items.forEach { (id, label) ->
            val on = remember(t) { DeenPrefs.checked(ctx, today, key + id) }
            Row(Modifier.fillMaxWidth().clickableNoRipple { tick(); DeenPrefs.setChecked(ctx, today, key + id, !on); t++ }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) DeenGreen else th.textFaint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    if (on) Icon(Duo.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(label, style = FitType.body, color = if (on) th.textDim else th.text)
            }
        }
    }
}

// ------------------------------------------------------------------ Hajj / Umrah

private val UmrahSteps = listOf("Ihram and niyyah", "Talbiyah", "Tawaf — 7 rounds", "2 rak'ah behind Maqam Ibrahim", "Drink Zamzam", "Sa'i — 7 laps (Safa → Marwah)", "Halq or taqsir")

@Composable
private fun UmrahTracker() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    var tawaf by remember { mutableIntStateOf(0) }
    var sai by remember { mutableIntStateOf(0) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var stepsStart by remember { mutableStateOf<Float?>(null) }
    var stepsNow by remember { mutableFloatStateOf(0f) }
    var t by remember { mutableIntStateOf(0) }
    DisposableEffect(startAt) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val s = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (startAt == null || s == null) onDispose { }
        else {
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { if (stepsStart == null) stepsStart = e.values[0]; stepsNow = e.values[0] }
                override fun onAccuracyChanged(x: Sensor?, a: Int) {}
            }
            sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sm.unregisterListener(l) }
        }
    }
    val now by produceState(System.currentTimeMillis(), startAt) { while (startAt != null) { value = System.currentTimeMillis(); delay(1000) } }
    GlassCard {
        CardHeader(Duo.DirectionsWalk, "Umrah / Hajj tracker", DeenGold)
        Spacer(Modifier.height(8.dp))
        if (startAt == null) {
            Caption("Counts your Tawaf rounds and Sa'i laps with big buttons, and tracks steps and time while you walk.")
            Spacer(Modifier.height(8.dp))
            AccentButton("Start", { startAt = System.currentTimeMillis(); stepsStart = null; tawaf = 0; sai = 0 }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 46.dp)
        } else {
            val mins = ((now - startAt!!) / 60000).toInt()
            val steps = stepsStart?.let { (stepsNow - it).roundToInt() } ?: 0
            Text("${mins / 60}h ${mins % 60}m · $steps steps", style = FitType.section, color = th.text)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Counter("Tawaf", tawaf, Modifier.weight(1f)) { if (tawaf < 7) { tawaf++; if (tawaf == 7) runCatching { ctx.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(250, 255)) } else tick() } }
                Counter("Sa'i", sai, Modifier.weight(1f)) { if (sai < 7) { sai++; if (sai == 7) runCatching { ctx.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(250, 255)) } else tick() } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Undo Tawaf", { if (tawaf > 0) tawaf-- }, Modifier.weight(1f), height = 40.dp)
                GlassButton("Undo Sa'i", { if (sai > 0) sai-- }, Modifier.weight(1f), height = 40.dp)
            }
            Spacer(Modifier.height(8.dp))
            GlassButton("Finish", { startAt = null }, Modifier.fillMaxWidth(), height = 42.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text("UMRAH STEPS", style = FitType.overline, color = th.textDim)
        val today = remember { LocalDate.now() }
        UmrahSteps.forEachIndexed { i, s ->
            val on = remember(t) { DeenPrefs.checked(ctx, today, "umrah$i") }
            Row(Modifier.fillMaxWidth().clickableNoRipple { tick(); DeenPrefs.setChecked(ctx, today, "umrah$i", !on); t++ }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) DeenGreen else th.textFaint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    if (on) Icon(Duo.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) else Text("${i + 1}", style = FitType.caption, color = th.textDim)
                }
                Spacer(Modifier.width(10.dp))
                Text(s, style = FitType.body, color = if (on) th.textDim else th.text)
            }
        }
    }
}

@Composable
private fun Counter(label: String, n: Int, modifier: Modifier, onTap: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(modifier.height(130.dp), shape = RoundedCornerShape(26.dp), onClick = onTap, pressScale = 0.94f) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(DeenGreen.copy(alpha = if (n == 7) 0.35f else 0.12f)) })
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = FitType.overline, color = th.textDim)
            Text("$n / 7", style = FitType.display, color = th.text)
            Caption(if (n == 7) "Complete ✓" else "Tap each round")
        }
    }
}

// ------------------------------------------------------------------ halal

@Composable
private fun HalalChecker() {
    val th = LocalFitTheme.current
    var text by remember { mutableStateOf("") }
    val res = remember(text) { HalalCheck.scan(text) }
    GlassCard {
        CardHeader(Duo.Search, "Halal ingredient check", DeenGreen)
        Spacer(Modifier.height(8.dp))
        Caption("Paste or type a product's ingredients (or E-numbers). We flag ingredients that are haram or often doubtful.")
        Spacer(Modifier.height(8.dp))
        Glass(Modifier.fillMaxWidth().heightIn(min = 90.dp), shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.padding(14.dp)) {
                if (text.isEmpty()) Text("e.g. sugar, gelatin, E120, natural flavour…", style = FitType.body, color = th.textFaint)
                BasicTextField(text, { text = it.take(2000) }, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
            }
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            val (color, title) = when {
                res.haram.isNotEmpty() -> th.danger to "Contains haram ingredients"
                res.doubtful.isNotEmpty() -> th.warning to "Check the source (doubtful)"
                else -> DeenGreen to "No problem ingredients found"
            }
            Text(title, style = FitType.section, color = color)
            res.haram.forEach { Caption("• $it", color = th.danger) }
            res.doubtful.forEach { Caption("• $it", color = th.warning) }
            Caption("A trusted halal certificate on the pack is the most reliable sign.", color = th.textFaint)
        }
    }
}

/** Keyword-based halal flags for ingredient lists, product and food names. */
object HalalCheck {
    data class Result(val haram: List<String>, val doubtful: List<String>)
    private val haram = linkedMapOf(
        "pork" to "Pork", "bacon" to "Bacon (pork)", "ham " to "Ham (pork)", "lard" to "Lard (pig fat)", "pepperoni" to "Pepperoni (often pork)",
        "salami" to "Salami (often pork)", "prosciutto" to "Prosciutto (pork)", "chorizo" to "Chorizo (often pork)",
        "wine" to "Wine", "beer" to "Beer", "rum" to "Rum", "brandy" to "Brandy", "whisky" to "Whisky", "vodka" to "Vodka", "alcohol" to "Alcohol",
        "ethanol" to "Ethanol", "e120" to "E120 carmine (from insects)", "carmine" to "Carmine (from insects)", "cochineal" to "Cochineal (from insects)",
        "e441" to "E441 gelatine",
    )
    private val doubtful = linkedMapOf(
        "gelatin" to "Gelatin — halal only if from halal-slaughtered or fish source", "gelatine" to "Gelatine — check the source",
        "e471" to "E471 mono/diglycerides — may be animal fat", "e472" to "E472 — may be animal fat", "e422" to "E422 glycerol — may be animal",
        "e631" to "E631 — may be from meat or fish", "e627" to "E627 — may be from meat or fish", "e904" to "E904 shellac (insect)",
        "rennet" to "Animal rennet — check the source", "whey" to "Whey — depends on the rennet used", "l-cysteine" to "L-cysteine (E920) — may be from hair or feathers",
        "e920" to "E920 L-cysteine — check the source", "natural flavour" to "Natural flavour — may contain alcohol", "vanilla extract" to "Vanilla extract — usually made with alcohol",
        "shortening" to "Shortening — may be animal fat", "animal fat" to "Animal fat — check the source",
    )
    fun scan(text: String): Result {
        val t = " " + text.lowercase().replace(Regex("[^a-z0-9\\-]+"), " ") + " "
        val h = haram.filter { (k, _) -> t.contains(if (k.endsWith(" ")) " $k" else k) }.values.distinct()
        val d = doubtful.filter { (k, _) -> t.contains(k) }.values.distinct()
        return Result(h, d)
    }
}
