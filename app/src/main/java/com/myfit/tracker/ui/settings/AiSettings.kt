package com.myfit.tracker.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ai.ondevice.AiConfig
import com.myfit.tracker.ai.ondevice.AiCapPrompt
import com.myfit.tracker.ai.ondevice.AiQuota
import com.myfit.tracker.ai.ondevice.DeviceCheck
import com.myfit.tracker.ai.ondevice.ModelCatalog
import com.myfit.tracker.ai.ondevice.ModelStore
import com.myfit.tracker.ai.ondevice.OnDeviceAi
import com.myfit.tracker.ai.ondevice.RewardedAds
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Settings → "Offline brain": download Gemma to the phone, plus today's free online allowance. */
@Composable
fun OfflineBrainCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val ai = remember { OnDeviceAi.get(ctx) }
    val scope = rememberCoroutineScope()
    val cfg by ai.prefs.config.collectAsState(initial = AiConfig())
    val st by ai.models.state.collectAsState()
    val photo by remember { ai.quota.allowance(AiQuota.Kind.PHOTO) }.collectAsState(initial = null)
    val chat by remember { ai.quota.allowance(AiQuota.Kind.CHAT) }.collectAsState(initial = null)
    val spec = remember(cfg) { ModelCatalog.resolve(cfg) }
    val cap = remember(spec) { DeviceCheck.check(ctx, spec) }
    // poll while a download may be running (the worker updates the shared state too)
    LaunchedEffect(Unit) { while (true) { androidx.compose.runtime.withFrameMillis { }; ai.models.refresh(); delay(if (st is ModelStore.DlState.Downloading || st is ModelStore.DlState.Waiting) 1500 else 5000) } }

    GlassCard {
        CardHeader(Duo.Lock, "Offline brain", th.accentBright)
        Spacer(Modifier.height(8.dp))
        Caption("Download ${spec.label.substringBefore(" (")} (${ModelCatalog.approxBytes(spec).let { if (it > 0) "about " + DeviceCheck.gb(it) else "size unknown" }}, ${spec.license}) and Pip answers questions and reads food photos right on your phone — free, unlimited, and nothing leaves the device. Answers are slower than online and still estimates.")
        Spacer(Modifier.height(10.dp))
        when {
            cap.tier == DeviceCheck.Tier.UNSUPPORTED -> Caption(cap.reason ?: "This phone can't run it.", color = th.warning)
            cap.reason != null -> Caption(cap.reason, color = th.textDim)
        }
        if (cap.tier != DeviceCheck.Tier.UNSUPPORTED) {
            Spacer(Modifier.height(10.dp))
            when (val s = st) {
                is ModelStore.DlState.Installed -> {
                    Text("Installed ✓  ·  ${DeviceCheck.gb(ai.models.storageUsed())} used", style = FitType.label, color = th.success)
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Use the offline brain", "Off = always use the online AI (counts toward today's allowance)", cfg.useOnDevice) { v -> scope.launch { ai.prefs.setUseOnDevice(v) } }
                    Spacer(Modifier.height(8.dp))
                    var testing by remember { mutableStateOf(false) }
                    var testResult by remember { mutableStateOf<String?>(null) }
                    GlassButton(if (testing) "Testing… (first load can take a minute)" else "Test offline brain", {
                        if (!testing) { testing = true; testResult = null; scope.launch { testResult = ai.llm.selfTest(); testing = false } }
                    }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 44.dp)
                    testResult?.let { r -> Spacer(Modifier.height(6.dp)); Caption(r, color = if (r.startsWith("Works")) th.success else th.warning) }
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Delete download", { scope.launch { ai.models.deleteAll() } }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline, height = 44.dp)
                }
                is ModelStore.DlState.Downloading -> {
                    val p = if (s.total > 0) s.done.toFloat() / s.total else 0f
                    Text("Downloading… ${DeviceCheck.gb(s.done)} of ${if (s.total > 0) DeviceCheck.gb(s.total) else "?"}" + (if (s.bytesPerSec > 0) "  ·  ${"%.1f".format(s.bytesPerSec / 1_000_000.0)} MB/s" else ""), style = FitType.label, color = th.text)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = th.accent, trackColor = th.textFaint.copy(alpha = 0.25f))
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Pause", { scope.launch { ai.models.pause() } }, Modifier.fillMaxWidth(), height = 44.dp)
                }
                is ModelStore.DlState.Verifying -> {
                    Text("Checking the file…", style = FitType.label, color = th.text)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = th.success, trackColor = th.textFaint.copy(alpha = 0.25f))
                }
                is ModelStore.DlState.Waiting -> {
                    Caption(if (s.wifiOnly) "Waiting for Wi-Fi… the download starts on its own." else "Starting…")
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Cancel", { scope.launch { ai.models.pause() } }, Modifier.fillMaxWidth(), height = 44.dp)
                }
                is ModelStore.DlState.Failed, is ModelStore.DlState.NotInstalled -> {
                    if (s is ModelStore.DlState.Failed) { Caption(s.message, color = th.warning); Spacer(Modifier.height(8.dp)) }
                    if (ai.models.legacy() != null) { Caption("You have the older 2 GB text-only model: offline chat works, but it can't read meal photos. Download the photo model below — it replaces the old one.", color = th.accentBright); Spacer(Modifier.height(8.dp)) }
                    val partial = (s as? ModelStore.DlState.NotInstalled)?.partialBytes ?: 0L
                    ToggleRow("Allow mobile data", "Off = waits for Wi-Fi (recommended, it's a big file)", cfg.allowMobileData) { v -> scope.launch { ai.prefs.setAllowMobile(v) } }
                    Spacer(Modifier.height(8.dp))
                    val free = cap.freeBytes
                    ModelCatalog.approxBytes(spec).let { need -> if (need > 0 && free < need + 300_000_000L) Caption("Free up space first: ${DeviceCheck.gb(free)} free, needs about ${DeviceCheck.gb(need + 300_000_000L)}.", color = th.warning) }
                    AccentButton(if (partial > 0) "Resume download (${DeviceCheck.gb(partial)} done)" else "Download offline brain", { scope.launch { ai.models.start() } }, Modifier.fillMaxWidth(), icon = Duo.ArrowDownward, height = 48.dp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("TODAY'S FREE ONLINE AI", style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(4.dp))
        photo?.let { Caption(it.label()) }
        chat?.let { Caption(it.label()) }
        Caption("Answers from your own logs and from the offline brain never count. Resets at midnight; a short optional ad adds more.", color = th.textFaint)
    }
}

/** Developer options → on-device model, backend and allowance overrides. */
@Composable
fun AiDevCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val ai = remember { OnDeviceAi.get(ctx) }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val cfg by ai.prefs.config.collectAsState(initial = AiConfig())
    val llmStatus by ai.llm.status.collectAsState()
    var url by remember(cfg.modelUrlOverride) { mutableStateOf(cfg.modelUrlOverride) }
    var photoCap by remember(cfg.photoCapOverride) { mutableStateOf(if (cfg.photoCapOverride >= 0) cfg.photoCapOverride.toString() else "") }
    var chatCap by remember(cfg.chatCapOverride) { mutableStateOf(if (cfg.chatCapOverride >= 0) cfg.chatCapOverride.toString() else "") }
    GlassCard {
        CardHeader(Duo.PrecisionManufacturing, "On-device AI & allowance", th.fat)
        Spacer(Modifier.height(8.dp))
        Caption("Engine: ${llmStatus.name.lowercase()}${ai.llm.backendInUse.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}${ai.llm.lastError?.let { " · last error: $it" } ?: ""}")
        Spacer(Modifier.height(10.dp))
        Text("Model", style = FitType.label, color = th.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelCatalog.all.forEach { m -> GlassChip(m.label.substringBefore(" ("), cfg.modelPreset == m.id, { scope.launch { ai.prefs.setPreset(m.id) } }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Custom model URL (https, .litertlm)", style = FitType.label, color = th.textDim)
        DevField(url, { url = it }, "Leave empty for the preset")
        Spacer(Modifier.height(10.dp))
        Text("Backend", style = FitType.label, color = th.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Auto", "gpu" to "GPU", "cpu" to "CPU").forEach { (id, l) -> GlassChip(l, cfg.backendPref == id, { scope.launch { ai.prefs.setBackendPref(id); ai.llm.release() } }) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) { Text("Photo cap/day", style = FitType.label, color = th.textDim); DevField(photoCap, { photoCap = it.filter(Char::isDigit).take(4) }, "${AiQuota.Kind.PHOTO.defaultCap}") }
            Column(Modifier.weight(1f)) { Text("Chat cap/day", style = FitType.label, color = th.textDim); DevField(chatCap, { chatCap = it.filter(Char::isDigit).take(4) }, "${AiQuota.Kind.CHAT.defaultCap}") }
        }
        Spacer(Modifier.height(8.dp))
        GlassButton("Save", {
            scope.launch {
                ai.prefs.setUrlOverride(url)
                ai.prefs.setCaps(photoCap.toIntOrNull() ?: -1, chatCap.toIntOrNull() ?: -1)
                toaster.show("Saved")
            }
        }, Modifier.fillMaxWidth(), height = 44.dp)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Unlimited (testing)", "Ignore the daily caps on this phone", cfg.unlimitedDev) { v -> scope.launch { ai.prefs.setUnlimited(v) } }
        ToggleRow("Force cloud", "Skip the offline brain even when installed", cfg.forceCloud) { v -> scope.launch { ai.prefs.setForceCloud(v) } }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Reset today's usage", { scope.launch { ai.prefs.resetToday(); toaster.show("Usage reset") } }, Modifier.weight(1f), height = 44.dp)
            GlassButton("Show cap sheet", { AiCapPrompt.show(AiQuota.Kind.CHAT) }, Modifier.weight(1f), height = 44.dp)
        }
        Spacer(Modifier.height(6.dp))
        Caption(if (RewardedAds.usingTestAds) "Ads: Google test ads (set ADMOB_APP_ID / ADMOB_REWARDED_ID secrets for real ones)." else "Ads: live AdMob unit.", color = th.textFaint)
    }
}

@Composable
private fun DevField(value: String, onChange: (String) -> Unit, hint: String) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(18.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.CenterStart).padding(horizontal = 14.dp)) {
            if (value.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
            BasicTextField(value, onChange, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "Free uses finished" sheet: watch a rewarded ad for more, or wait until tomorrow. Host once at the root. */
@Composable
fun AiCapSheetHost() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val kind by AiCapPrompt.pending.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ai = remember { OnDeviceAi.get(ctx) }
    val k = kind
    val allowance by remember(k) { k?.let { ai.quota.allowance(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    GlassSheet(visible = k != null, onDismiss = { if (!busy) { AiCapPrompt.dismiss(); error = null } }) {
        if (k != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Pip(PipMood.CURIOUS, size = 72.dp, interactive = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today's free ${k.many} are used up", style = FitType.title, color = th.text)
                    Caption("They reset at midnight.")
                }
            }
            Spacer(Modifier.height(12.dp))
            Caption(when (k) {
                AiQuota.Kind.PHOTO -> "You can still search or scan a barcode for free. Download the offline brain in Settings for unlimited photo scans on this phone."
                AiQuota.Kind.CHAT -> "Questions about your own logs still work offline. Download the offline brain in Settings for unlimited answers on this phone."
            })
            error?.let { Spacer(Modifier.height(8.dp)); Caption(it, color = th.warning) }
            Spacer(Modifier.height(14.dp))
            val canAd = allowance?.canWatchAd != false
            if (canAd) AccentButton(if (busy) "Loading ad…" else "Watch a short ad  ·  +${k.adGrant} ${k.many}", {
                if (busy) return@AccentButton
                val act = ctx.activity() ?: return@AccentButton
                busy = true; error = null
                scope.launch {
                    val ok = runCatching { RewardedAds.show(act) }.onFailure { error = it.message }.getOrDefault(false)
                    busy = false
                    if (ok) { ai.quota.grant(k); AiCapPrompt.dismiss(); toaster.show("+${k.adGrant} ${k.many} added — try again") }
                    else if (error == null) error = "The ad was closed early, so nothing was added."
                }
            }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, enabled = !busy, height = 50.dp)
            else Caption("You've reached today's ad limit too. See you tomorrow!", color = th.textDim)
            Spacer(Modifier.height(8.dp))
            GlassButton("Maybe later", { if (!busy) AiCapPrompt.dismiss() }, Modifier.fillMaxWidth(), height = 46.dp)
        }
    }
}
