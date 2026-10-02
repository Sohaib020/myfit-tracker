package com.myfit.tracker.ui.vitals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.health.VitalsReader
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme

private class BrandGuide(val name: String, val icon: ImageVector, val pkg: String, val body: String)

private val GUIDES = listOf(
    BrandGuide(
        "Samsung Health / Galaxy Watch", Duo.Watch, "com.sec.android.app.shealth",
        "Open Samsung Health → tap the menu (⋮ or ≡) → Settings → Health Connect (on some versions: Connected services → Health Connect) " +
            "and allow it to write the data you want — steps, heart rate, sleep, exercise, blood oxygen, weight. Your Galaxy Watch syncs into Samsung Health, so it comes along automatically.",
    ),
    BrandGuide(
        "Google Fit / Fitbit / Pixel Watch", Duo.Watch, "com.fitbit.FitbitMobile",
        "Fitbit app: tap your profile (Today tab) → Fitbit settings or App settings → Health Connect and turn on sharing. " +
            "Google Fit: Profile → Settings → Manage connected apps → Health Connect → Sync Fit with Health Connect. Pixel Watch data goes through the Fitbit app.",
    ),
    BrandGuide(
        "Garmin Connect", Duo.Watch, "com.garmin.android.apps.connectmobile",
        "Garmin added Health Connect sharing in 2025 — update Garmin Connect first. Then: More (☰) → Settings → Connected apps → Health Connect, opt in, and choose which data to share. " +
            "If you don't see the option, your app version or region may not have it yet.",
    ),
    BrandGuide(
        "Xiaomi / Redmi (Mi Fitness)", Duo.Watch, "com.xiaomi.wearable",
        "Open Mi Fitness → Profile → Third-party data (or Add accounts / Connected apps) → Health Connect and allow access. " +
            "Older Mi Fit / Zepp Life setups may need switching to Mi Fitness first.",
    ),
    BrandGuide(
        "Amazfit (Zepp)", Duo.Watch, "com.huami.watch.hmwatchmanager",
        "Open Zepp → Profile → Add accounts (or Third-party access) → Health Connect and allow the data types you want to share.",
    ),
    BrandGuide(
        "Oura ring", Duo.RadioButtonUnchecked, "com.ouraring.oura",
        "Open Oura → Settings (or ☰ menu) → Data sharing / Connected apps → Health Connect, then allow the data to share. Oura mainly shares sleep, heart rate, HRV and activity.",
    ),
    BrandGuide(
        "Polar Flow", Duo.Watch, "fi.polar.polarflow",
        "Open Polar Flow → More / Settings → General settings → Health Connect (sometimes under Connected services) and turn sharing on.",
    ),
    BrandGuide(
        "WHOOP", Duo.Watch, "com.whoop.android",
        "Open WHOOP → More → App settings → Integrations → Health Connect and connect it. WHOOP mostly shares workouts, sleep and heart rate.",
    ),
    BrandGuide(
        "Withings (scales, BP monitors, watches)", Duo.MonitorWeight, "com.withings.wiscale2",
        "Open Withings Health Mate → Profile → Settings → Health Connect (or Partner apps) and allow sharing. Good for weight, body fat and blood pressure readings.",
    ),
    BrandGuide(
        "Huawei / Honor", Duo.Watch, "",
        "Huawei Health doesn't share with Health Connect. The paid third-party app “Health Sync” can copy Huawei Health data into Health Connect: install it, choose Huawei Health as the source and Health Connect as the destination.",
    ),
    BrandGuide(
        "Budget bands (Noise, boAt, Fire-Boltt and similar)", Duo.Watch, "",
        "Most of these apps don't share with Health Connect yet. Check the band's app settings for “Health Connect” or “Google Fit”. If it isn't there, you can log key numbers yourself (weight, blood pressure) or use the camera heart-rate estimate.",
    ),
)

@Composable
internal fun DevicesContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val hs = container.healthSync
    val reader = remember { VitalsReader(container) }
    var refresh by remember { mutableIntStateOf(0) }
    val sources by produceState<Map<String, Map<String, Int>>?>(null, refresh) {
        value = runCatching { reader.sources(7) }.getOrDefault(emptyMap())
    }
    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh++ }
    var open by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Connected devices & apps", { nav.pop() }, "Where your health data comes from") {
            GlassIconButton(Duo.Sync, { refresh++ })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val src = sources
            item {
                GlassCard {
                    CardHeader(Duo.HealthAndSafety, "How it works", th.accent)
                    Spacer(Modifier.height(8.dp))
                    Caption("Your watch or ring syncs to its own app (Samsung Health, Fitbit, Garmin…). That app shares with Health Connect, Android's shared health store, and MyFit reads from there. So each brand app has to be allowed to write to Health Connect, and MyFit has to be allowed to read.")
                }
            }
            when {
                !hs.isAvailable -> item {
                    GlassCard {
                        CardHeader(Duo.LinkOff, "Health Connect not available", th.warning)
                        Spacer(Modifier.height(8.dp))
                        Caption("Health Connect isn't available on this phone or needs updating. On Android 14 and newer it's built in (Settings → Security & privacy → Privacy → Health Connect); on older phones install “Health Connect” from the Play Store.")
                    }
                }
                src == null -> item { GlassCard { Caption("Checking which apps shared data this week…") } }
                src.isEmpty() -> item {
                    GlassCard {
                        CardHeader(Duo.Lock, "MyFit can't read anything yet", th.warning)
                        Spacer(Modifier.height(8.dp))
                        Caption("Allow MyFit to read your data in Health Connect, then come back here to see which apps are sending it.")
                        Spacer(Modifier.height(12.dp))
                        AccentButton("Allow access", { runCatching { permLauncher.launch(hs.allPermissions) } }, Modifier.fillMaxWidth(), icon = Duo.Check, height = 48.dp)
                    }
                }
                else -> {
                    val allApps = src.values.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
                        .entries.sortedByDescending { it.value }.map { it.key }
                    item {
                        GlassCard {
                            CardHeader(Duo.Link, "Sending data this week", th.success)
                            Spacer(Modifier.height(10.dp))
                            if (allApps.isEmpty()) {
                                Caption("No app has written data in the last 7 days. Open your watch app, let it sync, and check that it's allowed to write to Health Connect (guides below).")
                            } else allApps.forEach { pkg ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconBubble(brandIcon(pkg), brandColor(pkg, th), 32.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(appName(pkg), style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val kinds = src.filterValues { it.containsKey(pkg) }.keys
                                        Caption(kinds.joinToString(" · "))
                                    }
                                }
                            }
                        }
                    }
                    item { SectionTitle("By data type · last 7 days") }
                    VitalsReader.ALL_TYPES.forEach { type ->
                        item(key = "type_$type") { TypeCard(type, src[type]) }
                    }
                }
            }
            item {
                GlassCard {
                    CardHeader(Duo.Tune, "Primary source & duplicates", th.water)
                    Spacer(Modifier.height(8.dp))
                    Caption("When your phone and watch both count steps, Health Connect removes the overlap using an app priority list, so MyFit's daily totals aren't double-counted. If a number looks off, move the app you trust most to the top: Health Connect → App permissions (or Data and access) → pick the data type → Data sources & priority.")
                    if (hs.isAvailable) {
                        Spacer(Modifier.height(12.dp))
                        GlassButton("Open Health Connect", { runCatching { ctx.startActivity(hs.settingsIntent()) } }, Modifier.fillMaxWidth(), icon = Duo.Gear, height = 44.dp)
                        Spacer(Modifier.height(8.dp))
                        GlassButton("Review MyFit permissions", { runCatching { permLauncher.launch(hs.allPermissions) } }, Modifier.fillMaxWidth(), icon = Duo.Lock, height = 44.dp)
                    }
                }
            }
            item { SectionTitle("Set up your brand") }
            items(GUIDES, key = { it.name }) { g ->
                val expanded = open == g.name
                val active = g.pkg.isNotEmpty() && src?.values?.any { it.containsKey(g.pkg) } == true
                GlassCard(onClick = { open = if (expanded) null else g.name }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(g.icon, if (g.pkg.isNotEmpty()) brandColor(g.pkg, th) else th.textDim, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = FitType.section, color = th.text)
                            if (active) Caption("Connected · sent data this week", color = th.success)
                        }
                        Icon(if (expanded) Duo.KeyboardArrowDown else Duo.KeyboardArrowRight, null, tint = th.textDim, modifier = Modifier.size(22.dp))
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        Text(g.body, style = FitType.body, color = th.text)
                        Spacer(Modifier.height(6.dp))
                        Caption("Steps may vary by app version.", color = th.textFaint)
                    }
                }
            }
            item {
                Caption(
                    "No watch at all? Your phone counts steps by itself, and you can measure your pulse with the camera (beta) from the Vitals screen.",
                    Modifier.padding(horizontal = 6.dp), color = th.textFaint,
                )
            }
        }
    }
}

@Composable
private fun TypeCard(type: String, apps: Map<String, Int>?) {
    val th = LocalFitTheme.current
    val icon = when (type) {
        VitalsReader.TYPE_STEPS -> Duo.Footprints
        VitalsReader.TYPE_HEART -> Duo.Favorite
        VitalsReader.TYPE_SLEEP -> Duo.Bedtime
        VitalsReader.TYPE_EXERCISE -> Duo.FitnessCenter
        VitalsReader.TYPE_SPO2 -> Duo.Drop
        VitalsReader.TYPE_WEIGHT -> Duo.MonitorWeight
        VitalsReader.TYPE_BP -> Duo.Pulse
        else -> Duo.WaterDrop
    }
    val color: Color = when (type) {
        VitalsReader.TYPE_STEPS -> th.steps
        VitalsReader.TYPE_HEART, VitalsReader.TYPE_BP -> th.danger
        VitalsReader.TYPE_SLEEP -> th.sleep
        VitalsReader.TYPE_EXERCISE -> th.accent
        VitalsReader.TYPE_SPO2 -> th.water
        VitalsReader.TYPE_WEIGHT -> th.fat
        else -> th.carbs
    }
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, color, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(type, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        when {
            apps == null -> Caption("MyFit isn't allowed to read this yet" + if (type == VitalsReader.TYPE_GLUCOSE) " (turn on blood-sugar tracking to ask)." else ".")
            apps.isEmpty() -> Caption("No app sent this in the last 7 days.")
            else -> {
                val total = apps.values.sum().coerceAtLeast(1)
                apps.entries.forEachIndexed { i, (pkg, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(brandIcon(pkg), null, tint = brandColor(pkg, th), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(appName(pkg), style = FitType.body, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption("$n record" + (if (n == 1) "" else "s") + " · ${n * 100 / total}%")
                    }
                    if (i == 0 && apps.size > 1) Caption("Most data from ${appName(pkg)}", color = th.textFaint)
                }
                if (type == VitalsReader.TYPE_STEPS && apps.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Caption("Several apps count steps — Health Connect keeps only the highest-priority source where they overlap, so they aren't added together.", color = th.textFaint)
                }
            }
        }
    }
}
