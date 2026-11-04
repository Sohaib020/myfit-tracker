package com.myfit.tracker.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * R35 — reporting AI replies (Google Play's AI-generated content policy). A report goes to Firestore `aiReports`;
 * with no connection (or a build without Firebase) it waits in a small local queue and is sent on the next start.
 */
object AiReports {
    private const val PREF = "ai_reports"
    val REASONS = listOf("Harmful or unsafe", "Wrong or misleading", "Offensive", "Other")

    suspend fun submit(c: AppContainer, text: String, reason: String, where: String) = withContext(Dispatchers.IO) {
        val ok = runCatching { c.social.reportAi(text, reason, where) }.isSuccess
        if (!ok) queue(c.app, text, reason, where)
    }

    private fun queue(ctx: Context, text: String, reason: String, where: String) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val a = runCatching { JSONArray(sp.getString("q", "[]")) }.getOrDefault(JSONArray())
        if (a.length() >= 50) return
        a.put(JSONObject().put("t", text.take(2000)).put("r", reason).put("w", where))
        sp.edit().putString("q", a.toString()).apply()
    }

    /** Called once per app start: sends anything that was queued. */
    suspend fun flush(c: AppContainer) = withContext(Dispatchers.IO) {
        val sp = c.app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val a = runCatching { JSONArray(sp.getString("q", "[]")) }.getOrDefault(JSONArray())
        if (a.length() == 0) return@withContext
        val left = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (runCatching { c.social.reportAi(o.getString("t"), o.getString("r"), o.getString("w")) }.isFailure) left.put(o)
        }
        sp.edit().putString("q", left.toString()).apply()
    }
}

/** Small "Report" link under an AI reply; opens a sheet with reasons. */
@Composable
fun AiReportLink(c: AppContainer, text: String, where: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    var open by remember { mutableStateOf(false) }
    Text("Report", style = FitType.overline, color = th.textFaint, modifier = modifier.clickableNoRipple { open = true }.padding(2.dp))
    AiReportSheet(c, text, where, open) { open = false }
}

@Composable
fun AiReportSheet(c: AppContainer, text: String, where: String, visible: Boolean, onClose: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var reason by remember(visible) { mutableStateOf<String?>(null) }
    GlassSheet(visible, onClose) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Report this AI reply", style = FitType.title, color = th.text)
            Caption("AI can get things wrong. Reports are reviewed by the MyFit team and help make answers safer. The reply text is sent with your report.")
            AiReports.REASONS.forEach { r -> GlassChip(r, reason == r, { reason = r }, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(4.dp))
            AccentButton("Send report", {
                val r = reason ?: return@AccentButton
                scope.launch { AiReports.submit(c, text, r, where); toaster.show("Thanks — report sent"); onClose() }
            }, Modifier.fillMaxWidth(), icon = Duo.Flag, enabled = reason != null, height = 50.dp)
        }
    }
}
