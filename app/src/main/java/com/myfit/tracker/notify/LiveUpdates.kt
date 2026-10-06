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

    /** Android 16+: "native" = system Live Update (Now Bar / lock screen); "rich" = our animated card. */
    fun nativeStyle(c: Context): Boolean = Build.VERSION.SDK_INT >= 36 &&
        c.getSharedPreferences("live_notif", Context.MODE_PRIVATE).getString("style", "native") != "rich"
    fun setRichStyle(c: Context, rich: Boolean) = c.getSharedPreferences("live_notif", Context.MODE_PRIVATE).edit().putString("style", if (rich) "rich" else "native").apply()

    /**
     * Posts/updates one live notification from a [card]. [points] = milestone dots on the Android 16 progress bar
     * (fractions 0..1). On Android 16 (default) it's a system Live Update with an animated progress bar and a
     * moving activity badge; otherwise (or if you pick "Rich card") it's our animated card.
     */
    fun post(
        c: Context, id: Int, card: NCard, open: String,
        points: List<Float> = emptyList(), category: String = Notification.CATEGORY_PROGRESS,
    ) {
        if (!canPost(c)) return
        channel(c)
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val n: Notification = if (nativeStyle(c)) nativeCard(c, card, open, points, category) else
            NotifKit.apply(c, NotificationCompat.Builder(c, CHANNEL), card)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(intent(c, open))
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(category)
                .apply {
                    if (card.chrono != null) { setWhen(card.chrono); setShowWhen(true); setUsesChronometer(true); setChronometerCountDown(card.countDown) }
                    else setShowWhen(false)
                }
                .build()
        runCatching { nm.notify(id, n) }
    }

    @android.annotation.TargetApi(36)
    private fun nativeCard(c: Context, card: NCard, open: String, points: List<Float>, category: String): Notification {
        val style = Notification.ProgressStyle().setStyledByProgress(true)
            .setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(c, card.kind.tracker))
        style.addProgressSegment(Notification.ProgressStyle.Segment(1000).setColor(card.kind.color))
        points.forEach { f -> style.addProgressPoint(Notification.ProgressStyle.Point((f.coerceIn(0f, 1f) * 1000).toInt().coerceIn(1, 999)).setColor(card.kind.color)) }
        if (card.progress != null) style.setProgress((card.progress.coerceIn(0f, 1f) * 1000).toInt()) else style.setProgressIndeterminate(true)
        val text = listOfNotNull(card.text, card.progressLabel).joinToString(" · ")
        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(card.title).setContentText(text)
            .setContentIntent(intent(c, open))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(category)
            .setColor(card.kind.color)
            .setStyle(style)
        if (card.chrono != null) { b.setWhen(card.chrono); b.setShowWhen(true); b.setUsesChronometer(true); b.setChronometerCountDown(card.countDown) }
        else b.setShowWhen(false)
        // ask for promotion (Now Bar / status chip); chip text when there's no clock
        val extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            (card.chip ?: card.value)?.let { putCharSequence("android.shortCriticalText", it.take(7)) }
        }
        b.addExtras(extras)
        return b.build()
    }

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
                val exs = v.exercises
                val doneEx = exs.count { it.sets.isNotEmpty() }
                val current = exs.lastOrNull { it.sets.isNotEmpty() }?.exercise?.name ?: exs.firstOrNull()?.exercise?.name
                val next = exs.firstOrNull { it.sets.isEmpty() }?.exercise?.name
                val frac = if (exs.isEmpty()) null else doneEx.toFloat() / exs.size
                val points = if (exs.size in 2..12) (1 until exs.size).map { it.toFloat() / exs.size } else emptyList()
                val vol = t.volumeKg?.let { com.myfit.tracker.domain.Fmt.int(it) + " kg" } ?: "—"
                val stats = listOf("${t.sets}" to "Sets", vol to "Volume", "$doneEx/${exs.size}" to "Exercises")
                if (r != null && r.endsAt > System.currentTimeMillis()) {
                    // rest countdown: the bar fills as the rest runs out; flips back when it ends
                    val total = (r.endsAt - r.startedAt).coerceAtLeast(1L)
                    while (true) {
                        val left = r.endsAt - System.currentTimeMillis()
                        if (left <= 0) break
                        post(app, WORKOUT, NCard(NKind.REST, "Rest · next ${r.label}", v.workout.name, chrono = r.endsAt, countDown = true,
                            progress = 1f - left.toFloat() / total, progressLabel = "${t.sets} sets done", chip = "Rest", stats = stats),
                            "gym", category = Notification.CATEGORY_STOPWATCH)
                        delay(minOf(5_000L, left))
                    }
                    post(app, WORKOUT, NCard(NKind.LIFT, "Rest over — go!", "Next: ${r.label}", chrono = v.workout.startedAt, progress = frac,
                        progressLabel = next?.let { "Up next: $it" }, stats = stats), "gym", points, Notification.CATEGORY_STOPWATCH)
                } else {
                    post(app, WORKOUT, NCard(NKind.LIFT, v.workout.name, current?.let { "Now: $it" } ?: "Workout in progress", chrono = v.workout.startedAt,
                        progress = frac, indeterminate = frac == null, progressLabel = next?.let { "Up next: $it" } ?: if (exs.isNotEmpty()) "Last exercise" else null,
                        stats = stats), "gym", points, Notification.CATEGORY_STOPWATCH)
                }
            }
        }

        // ---- activity stopwatch (walk, run, ride…): moving bar + live clock
        scope.launch {
            ActivityClock.load(app)
            snapshotFlow { Triple(ActivityClock.activity, ActivityClock.startedAt, ActivityClock.accumulated) }.distinctUntilChanged().collect { (a, s, acc) ->
                if (a == null) { cancel(app, STOPWATCH); return@collect }
                val name = com.myfit.tracker.domain.Burn.byId(a)?.name ?: "Activity"
                if (s > 0L) post(app, STOPWATCH, NCard(NKind.RUN, name, "Timer running · tap to open", chrono = s - acc, indeterminate = true), "stopwatch", category = Notification.CATEGORY_STOPWATCH)
                else post(app, STOPWATCH, NCard(NKind.RUN, "$name · paused", "Paused at ${mmss(acc / 1000)}", value = mmss(acc / 1000), chip = "Paused"), "stopwatch", category = Notification.CATEGORY_STOPWATCH)
            }
        }

        // ---- home-screen widgets follow today's water and steps while the app is alive
        scope.launch {
            val today = com.myfit.tracker.domain.Clock.today()
            combine(container.logRepo.day(today), container.healthRepo.day(today)) { d, h -> d.water.sumOf { it.amountMl } to (h.daily?.steps ?: h.phoneSteps) }
                .distinctUntilChanged().collectLatest { delay(800); com.myfit.tracker.widget.Widgets.refresh(app); WearSync.push(app) }
        }

        // ---- fasting: progress to the goal, phase milestones, refreshed every minute while it runs
        scope.launch {
            container.db.fastingDao().observeAll().map { l -> l.firstOrNull { it.endAt == null } }.distinctUntilChanged().collectLatest { f ->
                if (f == null) { cancel(app, FASTING); return@collectLatest }
                val goalMs = (f.targetHours * 3_600_000L).toLong()
                val goalAt = f.startAt + goalMs
                val fmt = com.myfit.tracker.domain.ClockFmt.f()
                val phases = listOf(12.0 to "Fat burning", 16.0 to "Ketosis", 18.0 to "Deep ketosis", 24.0 to "Autophagy")
                val points = if (f.targetHours > 0) phases.map { it.first / f.targetHours }.filter { it in 0.05..0.97 }.map { it.toFloat() } else emptyList()
                while (true) {
                    val elapsedH = (System.currentTimeMillis() - f.startAt) / 3_600_000.0
                    val phase = phases.lastOrNull { elapsedH >= it.first }?.second ?: if (elapsedH >= 4) "Blood sugar settling" else "Digesting"
                    val frac = if (goalMs > 0) ((System.currentTimeMillis() - f.startAt).toFloat() / goalMs) else null
                    val done = frac != null && frac >= 1f
                    val goalTxt = if (f.targetHours > 0) "Goal ${trim(f.targetHours)} h · ends ${fmt.format(Instant.ofEpochMilli(goalAt).atZone(ZoneId.systemDefault()))}" else "Open fast"
                    post(app, FASTING, NCard(NKind.FLAME, if (done) "Fasting goal reached!" else "Fasting · $phase", goalTxt, chrono = f.startAt,
                        progress = frac?.coerceAtMost(1f), indeterminate = frac == null,
                        progressLabel = frac?.let { if (done) "Break your fast gently" else "${(it * 100).toInt()}% of your goal" },
                        stats = listOf(fmt.format(Instant.ofEpochMilli(f.startAt).atZone(ZoneId.systemDefault())) to "Started", phase to "Phase",
                            (if (f.targetHours > 0) "${trim(f.targetHours)} h" else "—") to "Goal")),
                        "fasting", points, Notification.CATEGORY_PROGRESS)
                    delay(60_000)
                }
            }
        }
    }

    private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
    private fun mmss(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
