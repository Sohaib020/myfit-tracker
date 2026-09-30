package com.myfit.tracker.gym

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myfit.tracker.MainActivity
import com.myfit.tracker.R
import java.util.concurrent.TimeUnit

/**
 * App-scoped rest timer. It keeps counting past zero ("overtime") so the rest actually taken is
 * what gets recorded on the preceding set when the next set is completed or the timer is skipped.
 */
@Stable
class RestTimer(private val context: Context) {
    data class State(val setId: Long, val startedAt: Long, val endsAt: Long, val label: String)

    var state by mutableStateOf<State?>(null)
        private set

    fun start(setId: Long, seconds: Int, label: String, sound: Boolean, vibrate: Boolean) {
        val now = System.currentTimeMillis()
        state = State(setId, now, now + seconds * 1000L, label)
        schedule(seconds.toLong(), label, sound, vibrate)
    }

    fun add(seconds: Int, sound: Boolean, vibrate: Boolean) {
        val s = state ?: return
        val base = maxOf(s.endsAt, System.currentTimeMillis())
        val n = s.copy(endsAt = base + seconds * 1000L)
        state = n
        schedule((n.endsAt - System.currentTimeMillis()) / 1000, n.label, sound, vibrate)
    }

    /** Stops the timer and returns (setId, seconds actually rested) so the caller can store it. */
    fun stop(): Pair<Long, Long>? {
        val s = state ?: return null
        state = null
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        val rested = ((System.currentTimeMillis() - s.startedAt) / 1000).coerceAtLeast(0)
        return s.setId to rested
    }

    private fun schedule(delaySec: Long, label: String, sound: Boolean, vibrate: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        if (!sound && !vibrate) return
        wm.enqueue(
            OneTimeWorkRequestBuilder<RestAlertWorker>()
                .setInitialDelay(delaySec.coerceAtLeast(1), TimeUnit.SECONDS)
                .setInputData(workDataOf("label" to label, "sound" to sound, "vibrate" to vibrate))
                .addTag(TAG)
                .build()
        )
    }

    companion object { const val TAG = "rest_timer" }
}

/** Posts the "rest over" notification — only when the app is in the background (in-app alerts cover the foreground). */
class RestAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (visible) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you when your rest between sets is over"
            enableVibration(true)
        })
        val pi = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = inputData.getString("label") ?: "next set"
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest over")
            .setContentText("Time for $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .apply {
                var d = 0
                if (inputData.getBoolean("sound", true)) d = d or NotificationCompat.DEFAULT_SOUND
                if (inputData.getBoolean("vibrate", true)) d = d or NotificationCompat.DEFAULT_VIBRATE
                setDefaults(d)
            }
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, n) }
        return Result.success()
    }

    companion object { const val CHANNEL = "rest_timer"; const val NOTIF_ID = 4201 }
}

/** In-app alert when rest ends while the app is open. */
object Alerts {
    fun restOver(context: Context, sound: Boolean, vibrate: Boolean) {
        if (vibrate) runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 320), -1))
        }
        if (sound) runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 600)
        }
    }

    fun tick(context: Context) = runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createOneShot(30, 120))
    }
}
