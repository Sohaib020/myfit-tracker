package com.myfit.tracker.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myfit.tracker.AppContainer
import com.myfit.tracker.MainActivity
import com.myfit.tracker.R
import com.myfit.tracker.ui.activity.ActivityClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Live notifications for things that are running right now: a gym workout (switching to a rest countdown between
 * sets), the activity stopwatch, a fast, and downloads.
 *
 * On Android 16 they're posted as promoted "Live Updates", which Samsung One UI 8 shows in the Now Bar and the
 * status-bar chip; on older Android they're normal ongoing notifications with a live clock. The clock is drawn by
 * the system (chronometer), so nothing has to keep the app awake to update it.
 */
object LiveUpdates {
    const val CHANNEL = "live"
    const val WORKOUT = 4300
    const val STOPWATCH = 4301
    const val FASTING = 4302
    const val EXTRA_OPEN = "open"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun canPost(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Workout, rest timer, activity stopwatch and fasting while they run"
                setSound(null, null); enableVibration(false); setShowBadge(false)
            },
        )
    }

    private fun intent(c: Context, open: String): PendingIntent = PendingIntent.getActivity(
        c, open.hashCode(),
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_OPEN, open),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Posts/updates one live notification. [chrono] = the time the clock counts from (count-up) or to ([countDown]).
     * [chip] = short status-chip text when there is no clock (e.g. "Paused").
     */
    fun post(
        c: Context, id: Int, title: String, text: String, open: String,
        chrono: Long? = null, countDown: Boolean = false, chip: String? = null, progress: Pair<Int, Int>? = null,
        category: String = Notification.CATEGORY_PROGRESS,
    ) {
        if (!canPost(c)) return
        channel(c)
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        // NotificationCompat writes the Android 16 "promoted ongoing" request + chip text; older Android ignores them
        val n: Notification = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(intent(c, open))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(category)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (chrono != null) { setWhen(chrono); setShowWhen(true); setUsesChronometer(true); setChronometerCountDown(countDown) }
                else setShowWhen(false)
                if (chip != null) setShortCriticalText(chip)
                if (progress != null) setProgress(progress.second, progress.first.coerceIn(0, progress.second), false)
                setRequestPromotedOngoing(true)
            }
            .build()
        runCatching { nm.notify(id, n) }
    }

    const val TEST = 4309

    /**
     * Android 16+: may this app's live notifications be promoted (Now Bar / status chip)? null = can't tell
     * (older Android, where there is no promotion at all). Samsung additionally hides third-party ones unless
     * Developer options → "Live notifications for all apps" is on — Android can't report that.
     */
    fun promotionAllowed(c: Context): Boolean? = if (Build.VERSION.SDK_INT < 36) null else runCatching {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
    }.getOrNull()

    /** Opens the best settings screen for live notifications (app promotion page, else MyFit's notification settings). */
    fun openSettings(c: Context) {
        val pkg = c.packageName
        val tries = listOf("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS", "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS", android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        for (a in tries) {
            val i = Intent(a).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(c.packageManager) != null) { runCatching { c.startActivity(i) }.onSuccess { return } }
        }
    }

    /** Posts a 2-minute test live notification so you can check the Now Bar without starting a workout. */
    fun test(c: Context) {
        post(c, TEST, "MyFit live test", "If you see this in the Now Bar / status chip, live notifications work", "gym",
            chrono = System.currentTimeMillis() + 120_000, countDown = true, category = Notification.CATEGORY_STOPWATCH)
        scope.launch { delay(120_000); cancel(c, TEST) }
    }

    fun cancel(c: Context, id: Int) { runCatching { c.getSystemService(NotificationManager::class.java)?.cancel(id) } }

    /** Starts watching workouts, rest, the stopwatch and fasts. Call once from the UI start. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(container: AppContainer) {
        if (started) return
        started = true
        val app = container.app

        // ---- gym workout (+ rest countdown between sets)
        scope.launch {
            val workout = container.workoutRepo.inProgress.flatMapLatest { w -> if (w == null) flowOf(null) else container.workoutRepo.workoutView(w.id) }
            val rest = snapshotFlow { container.restTimer.state }
            combine(workout, rest) { v, r -> v to r }.collectLatest { (v, r) ->
                if (v == null) { cancel(app, WORKOUT); return@collectLatest }
                val t = v.totals
                val last = v.exercises.lastOrNull { it.sets.isNotEmpty() }?.exercise?.name ?: v.exercises.firstOrNull()?.exercise?.name
                if (r != null && r.endsAt > System.currentTimeMillis()) {
                    post(app, WORKOUT, "Rest · next ${r.label}", "${v.workout.name} · ${t.sets} sets done", "gym",
                        chrono = r.endsAt, countDown = true, category = Notification.CATEGORY_STOPWATCH)
                    // when the countdown hits zero, flip back to the workout clock
                    delay((r.endsAt - System.currentTimeMillis()).coerceAtLeast(0))
                    post(app, WORKOUT, "Rest over — go!", "${v.workout.name} · next ${r.label}", "gym", chrono = v.workout.startedAt, category = Notification.CATEGORY_STOPWATCH)
                } else {
                    post(app, WORKOUT, v.workout.name, "${t.sets} sets" + (last?.let { " · $it" } ?: "") + (if (r != null) " · rest over — go!" else ""), "gym",
                        chrono = v.workout.startedAt, category = Notification.CATEGORY_STOPWATCH)
                }
            }
        }

        // ---- activity stopwatch
        scope.launch {
            ActivityClock.load(app)
            snapshotFlow { Triple(ActivityClock.activity, ActivityClock.startedAt, ActivityClock.accumulated) }.distinctUntilChanged().collect { (a, s, acc) ->
                if (a == null) { cancel(app, STOPWATCH); return@collect }
                val name = com.myfit.tracker.domain.Burn.byId(a)?.name ?: "Activity"
                if (s > 0L) post(app, STOPWATCH, name, "Activity timer running", "stopwatch", chrono = s - acc, category = Notification.CATEGORY_STOPWATCH)
                else post(app, STOPWATCH, "$name · paused", "Paused at ${mmss(acc / 1000)}", "stopwatch", chip = "Paused", category = Notification.CATEGORY_STOPWATCH)
            }
        }

        // ---- home-screen widgets follow today's water and steps while the app is alive
        scope.launch {
            val today = com.myfit.tracker.domain.Clock.today()
            combine(container.logRepo.day(today), container.healthRepo.day(today)) { d, h -> d.water.sumOf { it.amountMl } to (h.daily?.steps ?: h.phoneSteps) }
                .distinctUntilChanged().collectLatest { delay(800); com.myfit.tracker.widget.Widgets.refresh(app); WearSync.push(app) }
        }

        // ---- fasting
        scope.launch {
            container.db.fastingDao().observeAll().map { l -> l.firstOrNull { it.endAt == null } }.distinctUntilChanged().collect { f ->
                if (f == null) { cancel(app, FASTING); return@collect }
                val goalAt = f.startAt + (f.targetHours * 3_600_000L).toLong()
                val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                val goalTxt = if (f.targetHours > 0) "Goal ${trim(f.targetHours)} h · until ${fmt.format(Instant.ofEpochMilli(goalAt).atZone(ZoneId.systemDefault()))}" else "Fasting"
                post(app, FASTING, "Fasting", goalTxt, "fasting", chrono = f.startAt, category = Notification.CATEGORY_PROGRESS)
            }
        }
    }

    private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
    private fun mmss(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
