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
import kotlinx.coroutines.flow.first
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.w("LiveUpdates", e) })
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
     * Picked automatically: Android 16+ phones that allow promoted notifications get the system Live Update (Now Bar,
     * status-bar chip, lock screen); every other phone gets MyFit's live card.
     */
    fun nativeStyle(c: Context): Boolean = Build.VERSION.SDK_INT >= 36 && promotionAllowed(c) != false

    /** A button on a live notification: label + action handled by [LiveActionReceiver]. */
    data class Action(val label: String, val action: String)

    /**
     * Posts/updates one live notification from a [card]. [points] = milestone dots on the Android 16 progress bar
     * (fractions 0..1). On Android 16 (default) it's a system Live Update with an animated progress bar and a
     * moving activity badge; otherwise (or if you pick "Rich card") it's our animated card.
     */
    fun post(
        c: Context, id: Int, card: NCard, open: String,
        points: List<Float> = emptyList(), category: String = Notification.CATEGORY_PROGRESS,
        segments: List<Pair<Float, Int>> = emptyList(), actions: List<Action> = emptyList(),
    ) {
        if (!canPost(c)) return
        runCatching { postNow(c, id, card, open, points, category, segments, actions) }.onFailure { android.util.Log.w("LiveUpdates", it) }
    }

    private fun actionIntent(c: Context, a: Action): PendingIntent = PendingIntent.getBroadcast(
        c, a.action.hashCode(), Intent(c, LiveActionReceiver::class.java).setAction(a.action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun postNow(c: Context, id: Int, card: NCard, open: String, points: List<Float>, category: String, segments: List<Pair<Float, Int>>, actions: List<Action>) {
        channel(c)
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val n: Notification = if (nativeStyle(c)) nativeCard(c, card, open, points, category, segments, actions) else
            NotifKit.live(c, NotificationCompat.Builder(c, CHANNEL), card, points)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(intent(c, open))
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(category)
                .apply { actions.forEach { a -> addAction(R.drawable.ic_notification, a.label, actionIntent(c, a)) } }
                .apply {
                    if (card.chrono != null) { setWhen(card.chrono); setShowWhen(true); setUsesChronometer(true); setChronometerCountDown(card.countDown) }
                    else setShowWhen(false)
                }
                .build()
        runCatching { nm.notify(id, n) }
    }

    @android.annotation.TargetApi(36)
    private fun nativeCard(c: Context, card: NCard, open: String, points: List<Float>, category: String, segments: List<Pair<Float, Int>>, actions: List<Action>): Notification {
        val style = Notification.ProgressStyle().setStyledByProgress(true)
            .setProgressTrackerIcon(android.graphics.drawable.Icon.createWithResource(c, card.kind.tracker))
        // coloured route segments (exercises / fasting phases), else one segment in the kind's colour
        val segs = segments.filter { it.first > 0f }
        if (segs.isEmpty()) style.addProgressSegment(Notification.ProgressStyle.Segment(1000).setColor(card.kind.color))
        else {
            val total = segs.sumOf { it.first.toDouble() }.toFloat()
            var used = 0
            segs.forEachIndexed { i, (len, col) ->
                val n = if (i == segs.lastIndex) 1000 - used else (len / total * 1000).toInt().coerceAtLeast(1)
                used += n
                style.addProgressSegment(Notification.ProgressStyle.Segment(n.coerceAtLeast(1)).setColor(col))
            }
        }
        // Android 16 draws at most a few milestone dots: keep up to 4, spread across the bar
        val pts = if (points.size <= 4) points else (0 until 4).map { points[it * (points.size - 1) / 3] }
        pts.forEach { f -> style.addProgressPoint(Notification.ProgressStyle.Point((f.coerceIn(0f, 1f) * 1000).toInt().coerceIn(1, 999)).setColor(card.kind.color)) }
        if (card.progress != null) style.setProgress((card.progress.coerceIn(0f, 1f) * 1000).toInt()) else style.setProgressIndeterminate(true)
        val text = listOfNotNull(card.text, card.progressLabel).joinToString(" · ")
        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(card.title).setContentText(text)
            .setContentIntent(intent(c, open))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(category)
            .setColor(card.kind.color)
            .setStyle(style)
        card.eyebrow?.let { b.setSubText(it.lowercase().replaceFirstChar { ch -> ch.uppercase() }) }
        actions.forEach { a ->
            b.addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_notification), a.label, actionIntent(c, a)).build())
        }
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

    const val DAILY = 4303
    private const val DAILY_CHANNEL = "daily_goals"

    /** Daily goals card on/off (Me → Notifications). */
    fun dailyOn(c: Context) = c.getSharedPreferences("live_notif", Context.MODE_PRIVATE).getBoolean("daily", true)
    fun setDailyOn(c: Context, on: Boolean) {
        c.getSharedPreferences("live_notif", Context.MODE_PRIVATE).edit().putBoolean("daily", on).apply()
        if (!on) cancel(c, DAILY)
    }

    /** Starts watching workouts, rest, the stopwatch, fasts and today's goals. Call once from the UI start. */
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
                val cur = exs.lastOrNull { it.sets.isNotEmpty() } ?: exs.firstOrNull()
                val current = cur?.exercise?.name
                val setNo = (cur?.sets?.size ?: 0) + 1
                val next = exs.firstOrNull { it.sets.isEmpty() && it != cur }?.exercise?.name
                val frac = if (exs.isEmpty()) null else doneEx.toFloat() / exs.size
                val points = if (exs.size in 2..12) (1 until exs.size).map { it.toFloat() / exs.size } else emptyList()
                // one route segment per exercise
                val segs = if (exs.size in 2..12) exs.map { 1f to NKind.LIFT.color } else emptyList()
                val vol = t.volumeKg?.let { com.myfit.tracker.domain.Fmt.int(it) + " kg" } ?: "—"
                val stats = listOf("${t.sets}" to "Sets", vol to "Volume", "$doneEx/${exs.size}" to "Exercises")
                if (r != null && r.endsAt > System.currentTimeMillis()) {
                    // rest countdown: the badge moves along as the rest runs out; flips back when it ends
                    val total = (r.endsAt - r.startedAt).coerceAtLeast(1L)
                    val acts = listOf(Action("+15 s", LiveActionReceiver.REST_ADD), Action("Skip rest", LiveActionReceiver.REST_SKIP))
                    while (true) {
                        val left = r.endsAt - System.currentTimeMillis()
                        if (left <= 0) break
                        post(app, WORKOUT, NCard(NKind.REST, "Rest · then ${r.label}", "${t.sets} sets done · ${v.workout.name}", chrono = r.endsAt, countDown = true,
                            progress = 1f - left.toFloat() / total, chip = "Rest", stats = stats, eyebrow = "Resting", unit = "left"),
                            "gym", category = Notification.CATEGORY_STOPWATCH, actions = acts)
                        delay(minOf(5_000L, left))
                    }
                    post(app, WORKOUT, NCard(NKind.LIFT, "Rest over — let's go 💪", "Next: ${r.label}", chrono = v.workout.startedAt, progress = frac,
                        progressLabel = next?.let { "Then: $it" }, stats = stats, eyebrow = "Workout · live", unit = "elapsed", chip = "Go"),
                        "gym", points, Notification.CATEGORY_STOPWATCH, segs)
                } else {
                    post(app, WORKOUT, NCard(NKind.LIFT, current?.let { "$it · set $setNo" } ?: v.workout.name,
                        next?.let { "Up next: $it" } ?: if (exs.isNotEmpty()) "Last exercise — finish strong" else "Add your first exercise",
                        chrono = v.workout.startedAt, progress = frac, indeterminate = frac == null,
                        progressLabel = "${(((frac ?: 0f) * 100).toInt())}% of ${v.workout.name}",
                        stats = stats, eyebrow = "Workout · live", unit = "elapsed", chip = "$doneEx/${exs.size}"),
                        "gym", points, Notification.CATEGORY_STOPWATCH, segs)
                }
            }
        }

        // ---- run / walk / ride stopwatch: live clock, calorie estimate, pause & resume buttons
        scope.launch {
            ActivityClock.load(app)
            val kg = runCatching { kotlinx.coroutines.withTimeoutOrNull(1500) { container.logRepo.latestWeight().first()?.weightKg } }.getOrNull() ?: 70.0
            snapshotFlow { Triple(ActivityClock.activity, ActivityClock.startedAt, ActivityClock.accumulated) }.distinctUntilChanged().collectLatest { (a, s, acc) ->
                if (a == null) { cancel(app, STOPWATCH); return@collectLatest }
                val act = com.myfit.tracker.domain.Burn.byId(a)
                val name = act?.name ?: "Activity"
                if (s > 0L) {
                    while (true) {
                        val secs = (ActivityClock.elapsedMs() / 1000)
                        val kcal = act?.let { com.myfit.tracker.domain.Burn.kcal(it.met, kg, secs) } ?: 0.0
                        // a lap marker every 10 minutes: the badge runs around a 10-minute lap
                        val lap = (secs % 600) / 600f
                        post(app, STOPWATCH, NCard(NKind.RUN, "$name in progress", "≈ ${kcal.toInt()} kcal so far · lap ${secs / 600 + 1}",
                            chrono = s - acc, progress = lap, eyebrow = "$name · live", unit = "elapsed", chip = "Live",
                            stats = listOf("${kcal.toInt()}" to "kcal (est.)", "${secs / 60} min" to "Time", "${secs / 600 + 1}" to "Lap")),
                            "stopwatch", category = Notification.CATEGORY_STOPWATCH, actions = listOf(Action("Pause", LiveActionReceiver.SW_PAUSE)))
                        delay(30_000)
                    }
                } else post(app, STOPWATCH, NCard(NKind.RUN, "$name · paused", "Tap Resume when you're ready", value = mmss(acc / 1000), chip = "Paused",
                    eyebrow = "$name · paused", unit = "so far"), "stopwatch", category = Notification.CATEGORY_STOPWATCH,
                    actions = listOf(Action("Resume", LiveActionReceiver.SW_RESUME)))
            }
        }

        // ---- home-screen widgets + the daily goals card follow today's water, steps and food
        scope.launch {
            val today = com.myfit.tracker.domain.Clock.today()
            combine(container.logRepo.day(today), container.healthRepo.day(today), container.nutritionRepo.itemsOn(today), container.profileRepo.targets) { d, h, food, tg ->
                val water = d.water.sumOf { it.amountMl }
                val steps = (h.daily?.steps ?: h.phoneSteps)
                val kcal = food.sumOf { it.caloriesPerServing * it.quantity }
                listOf(water.toDouble(), (steps ?: 0L).toDouble(), kcal) to tg
            }.distinctUntilChanged().collectLatest { (v, tg) ->
                delay(800)
                com.myfit.tracker.widget.Widgets.refresh(app); WearSync.push(app)
                if (dailyOn(app)) postDaily(app, v[0], v[1], v[2], tg)
            }
        }

        // ---- fasting (incl. Ramadan): coloured phase segments, live clock, refreshed every minute
        scope.launch {
            container.db.fastingDao().observeAll().map { l -> l.firstOrNull { it.endAt == null } }.distinctUntilChanged().collectLatest { f ->
                if (f == null) { cancel(app, FASTING); return@collectLatest }
                val ramadan = f.notes == "Ramadan"
                val goalMs = (f.targetHours * 3_600_000L).toLong()
                val goalAt = f.startAt + goalMs
                val fmt = com.myfit.tracker.domain.ClockFmt.f()
                val phases = listOf(12.0 to "Fat burning", 16.0 to "Ketosis", 18.0 to "Deep ketosis", 24.0 to "Autophagy")
                val points = if (f.targetHours > 0 && !ramadan) phases.map { it.first / f.targetHours }.filter { it in 0.05..0.97 }.map { it.toFloat() } else emptyList()
                val phaseColors = listOf(0xFF5B8CFF.toInt(), 0xFFFFA51F.toInt(), 0xFFFF5A36.toInt(), 0xFFE0338A.toInt(), 0xFF8E5BFF.toInt())
                val segs = if (f.targetHours > 0 && !ramadan) {
                    val bounds = listOf(0.0) + phases.map { it.first }.filter { it < f.targetHours } + f.targetHours
                    bounds.zipWithNext().mapIndexed { i, (x, y) -> ((y - x) / f.targetHours).toFloat() to phaseColors[i.coerceAtMost(phaseColors.lastIndex)] }
                } else emptyList()
                while (true) {
                    val now = System.currentTimeMillis()
                    val elapsedH = (now - f.startAt) / 3_600_000.0
                    val phase = phases.lastOrNull { elapsedH >= it.first }?.second ?: if (elapsedH >= 4) "Blood sugar settling" else "Digesting"
                    val frac = if (goalMs > 0) ((now - f.startAt).toFloat() / goalMs) else null
                    val done = frac != null && frac >= 1f
                    val endsAt = fmt.format(Instant.ofEpochMilli(goalAt).atZone(ZoneId.systemDefault()))
                    val card = if (ramadan) NCard(NKind.MOON, if (done) "Iftar time 🌙 Ramadan Mubarak" else "Iftar at $endsAt",
                        if (done) "Break your fast with dates and water" else "Ramadan fast · started ${fmt.format(Instant.ofEpochMilli(f.startAt).atZone(ZoneId.systemDefault()))}",
                        chrono = if (done) f.startAt else goalAt, countDown = !done, progress = frac?.coerceAtMost(1f),
                        eyebrow = "Ramadan · live", unit = if (done) "fasted" else "to iftar", chip = "Iftar",
                        stats = listOf(endsAt to "Iftar", "${(elapsedH).toInt()} h ${((elapsedH % 1) * 60).toInt()} m" to "Fasted", "${trim(f.targetHours)} h" to "Total"))
                    else NCard(NKind.FLAME, if (done) "Fasting goal reached 🎉" else "Fasting · $phase", if (f.targetHours > 0) "Goal ${trim(f.targetHours)} h · ends $endsAt" else "Open fast",
                        chrono = f.startAt, progress = frac?.coerceAtMost(1f), indeterminate = frac == null,
                        progressLabel = frac?.let { if (done) "Break your fast gently" else "${(it * 100).toInt()}% of your goal" },
                        eyebrow = "Fasting · live", unit = "fasted", chip = frac?.let { "${(it.coerceAtMost(1f) * 100).toInt()}%" },
                        stats = listOf(fmt.format(Instant.ofEpochMilli(f.startAt).atZone(ZoneId.systemDefault())) to "Started", phase to "Phase",
                            (if (f.targetHours > 0) "${trim(f.targetHours)} h" else "—") to "Goal"))
                    post(app, FASTING, card, "fasting", points, Notification.CATEGORY_PROGRESS, segs)
                    delay(60_000)
                }
            }
        }
    }

    /** Quiet, dismissible daily card: steps, water and calories against today's targets, with goal rings. */
    private fun postDaily(c: Context, waterMl: Double, steps: Double, kcal: Double, tg: List<com.myfit.tracker.domain.Targets.Row>) {
        if (!canPost(c)) return
        if (waterMl <= 0 && steps <= 0 && kcal <= 0) return
        val today = com.myfit.tracker.domain.Clock.today()
        fun t(type: String) = com.myfit.tracker.domain.Targets.on(tg, type, today)
        val tSteps = t(com.myfit.tracker.data.db.TargetType.STEPS) ?: 8000.0
        val tWater = t(com.myfit.tracker.data.db.TargetType.WATER_ML) ?: 2500.0
        val tKcal = t(com.myfit.tracker.data.db.TargetType.CALORIES)
        val fs = steps / tSteps; val fw = waterMl / tWater; val fk = tKcal?.let { kcal / it }
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(DAILY_CHANNEL) == null) nm.createNotificationChannel(
            NotificationChannel(DAILY_CHANNEL, "Daily goals", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Today's steps, water and calories at a glance"; setSound(null, null); enableVibration(false); setShowBadge(false)
            })
        val done = listOf(fs, fw).count { it >= 1.0 } + (if (fk != null && fk in 0.9..1.1) 1 else 0)
        val title = when {
            done >= 2 -> "Great day — $done goals hit 🎯"
            fs < 0.5 && java.time.LocalTime.now().hour >= 17 -> "${com.myfit.tracker.domain.Fmt.int(tSteps - steps)} steps to go today"
            else -> "Today so far"
        }
        val rings = NotifKit.rings(listOfNotNull(fs.toFloat() to NKind.RUN.color, fw.toFloat() to NKind.DROP.color, fk?.toFloat()?.let { it to NKind.MEAL.color }), 160)
        val card = NCard(NKind.GOALS, title, "${com.myfit.tracker.domain.Fmt.int(steps)} steps · ${trim(waterMl / 1000)} L water" + (if (tKcal != null) " · ${kcal.toInt()} kcal" else ""),
            value = "${(fs * 100).toInt().coerceAtMost(999)}%", unit = "of steps goal", image = rings, eyebrow = "Daily goals",
            stats = listOfNotNull(com.myfit.tracker.domain.Fmt.int(steps) to "of ${com.myfit.tracker.domain.Fmt.int(tSteps)} steps",
                "${trim(waterMl / 1000)} L" to "of ${trim(tWater / 1000)} L water", tKcal?.let { "${kcal.toInt()}" to "of ${it.toInt()} kcal" }))
        val n = NotifKit.live(c, NotificationCompat.Builder(c, DAILY_CHANNEL), card)
            .setSmallIcon(R.drawable.ic_notification).setContentIntent(intent(c, "today"))
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(false).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        runCatching { nm.notify(DAILY, n) }
    }

    private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

    private fun mmss(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
