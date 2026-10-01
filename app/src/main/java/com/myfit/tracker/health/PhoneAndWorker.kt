package com.myfit.tracker.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.PhoneStepSnapshot
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The phone's hardware step counter (cumulative since boot). Used only when Health Connect has no steps. */
object PhoneSteps {
    fun hasSensor(ctx: Context) = ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Reads the current counter value (waits up to 4 s for the first sensor event). */
    suspend fun read(ctx: Context): Long? {
        if (!hasPermission(ctx)) return null
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(4000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    suspend fun snapshot(ctx: Context, db: AppDatabase) {
        val v = read(ctx) ?: return
        val s = Stamp.now()
        db.healthDao().insertSnapshot(PhoneStepSnapshot(at = s.at, counter = v, zoneId = s.zoneId, localDate = s.localDate))
        db.healthDao().pruneSnapshots(s.at - TimeUnit.DAYS.toMillis(400))
    }
}

/** Background refresh every 30 min: Health Connect window + phone counter snapshot. */
class HealthSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MyFitApplication).container
        runCatching { PhoneSteps.snapshot(applicationContext, c.db) }
        runCatching {
            val g = c.healthSync.granted()
            if (c.healthSync.backgroundPermission in g || g.isNotEmpty()) {
                val r = c.healthSync.sync(3)
                c.settings.setLastHealthSync(Clock.now(), r.message)
            }
        }
        runCatching { c.social.start(); c.social.uploadNow() }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "health_sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HealthSyncWorker>(30, TimeUnit.MINUTES).build(),
            )
        }
    }
}

/** Shown by Health Connect when you tap "privacy policy" on MyFit's permission screen. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF120204)).systemBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(24.dp)
            ) {
                Text("How MyFit uses your health data", color = Color.White, fontSize = 22.sp)
                Spacer(Modifier.height(16.dp))
                listOf(
                    "MyFit only READS data from Health Connect (steps, distance, calories, floors, workouts, heart rate, resting heart rate, HRV, blood oxygen and sleep). It never writes to or deletes anything in Samsung Health or Health Connect.",
                    "Everything is stored only on this phone. There is no account, no server, no advertising and no analytics.",
                    "Imported data is kept separate from what you log by hand, labelled with its source, and never counted twice.",
                    "If you use Pip's online answers, only a short summary of the specific numbers needed for your question is sent to Google Gemini. Your full history and notes are never sent.",
                    "You can revoke access at any time in Health Connect settings.",
                ).forEach {
                    Text("•  $it", color = Color(0xCCFFFFFF), fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
