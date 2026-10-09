package com.myfit.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.MyFitRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as MyFitApplication).container
        (application as MyFitApplication).onUiStart()
        com.myfit.tracker.social.Invite.handle(intent)
        intent?.getStringExtra(com.myfit.tracker.notify.LiveUpdates.EXTRA_OPEN)?.let { com.myfit.tracker.ui.nav.Launch.open.value = it }
        com.myfit.tracker.notify.LiveUpdates.start(container)
        com.myfit.tracker.ui.pip.Buddy.init(this)
        com.myfit.tracker.ui.programs.ProgramEngine.init(this)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("smoke", false) == true) SmokeSetup.ensureProfile(container)
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) runCatching { DemoData.seed(container) }
        if (BuildConfig.DEBUG && getSharedPreferences("demo", MODE_PRIVATE).getBoolean("seeded", false)) com.myfit.tracker.domain.BadgeEngine.quiet = true
        val crash = CrashGuard.lastCrash(this)
        setContent {
            var report by remember { mutableStateOf(crash) }
            val r = report
            if (r != null) CrashScreen(
                r,
                onSafe = { CrashGuard.setSafe(this, true); CrashGuard.clear(this); report = null },
                onNormal = { CrashGuard.setSafe(this, false); CrashGuard.clear(this); report = null },
            ) else MyFitRoot(container)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        com.myfit.tracker.social.Invite.handle(intent)
        intent.getStringExtra(com.myfit.tracker.notify.LiveUpdates.EXTRA_OPEN)?.let { com.myfit.tracker.ui.nav.Launch.open.value = it }
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) com.myfit.tracker.update.AppUpdater.installIfReady(this)
    }
}

@androidx.compose.runtime.Composable
private fun CrashScreen(text: String, onSafe: () -> Unit, onNormal: () -> Unit) {
    val clip = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101418)).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MyFit closed unexpectedly last time", color = Color.White, fontSize = 22.sp)
        Text("Your data is safe. Copy the report below and send it so it can be fixed. Safe mode turns off the animated glass effects.", color = Color(0xFFB0BEC5), fontSize = 14.sp)
        Button(onClick = { clip.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy crash report") }
        Button(onClick = onSafe, modifier = Modifier.fillMaxWidth()) { Text("Open in safe mode") }
        OutlinedButton(onClick = onNormal, modifier = Modifier.fillMaxWidth()) { Text("Open normally", color = Color.White) }
        Spacer(Modifier.height(4.dp))
        Text(text, color = Color(0xFFE0E0E0), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
    }

}
