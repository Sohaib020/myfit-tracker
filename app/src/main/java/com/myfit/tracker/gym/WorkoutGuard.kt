package com.myfit.tracker.gym

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.activity.ActivityClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * R20 smart auto-pause, so a forgotten workout timer stops running up.
 *
 *  - Gym workouts: no set logged (and no rest timer running) for 5 minutes AND the phone is still (no walking, and
 *    the accelerometer is quiet) → the workout pauses and a "Still training?" notification offers Resume / End.
 *    The time after a normal rest (3 min after the last set) is trimmed; Resume moves the start forward by the
 *    paused time, so every duration and calorie figure only counts training time.
 *  - Run / walk / hike / stairs stopwatch: pauses when steps stop for 2 minutes and resumes by itself when you move.
 *    Cycling uses the accelerometer instead of steps. Sports, yoga, swimming etc. are never auto-paused.
 *
 * Checks run every minute while the app is alive and from a cheap inexact alarm when it isn't; the hardware step
 * counter keeps counting while the app sleeps, so a check after a gap still knows whether you moved.
 */
object WorkoutGuard {
    const val IDLE_MS = 5 * 60_000L
    const val GRACE_MS = 3 * 60_000L
    const val CARDIO_IDLE_MS = 2 * 60_000L
    private const val P = "workout_guard"
    private const val MOVED_STEPS = 10

    /** (workoutId, pausedAt) while a gym workout is auto-paused. */
    val paused = MutableStateFlow<Pair<Long, Long>?>(null)
    /** The stopwatch was paused by us (not by the user), so movement resumes it. */
    @Volatile var cardioAuto = false; private set
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        val p = prefs(c)
        val wid = p.getLong("pw", 0L)
        paused.value = if (wid > 0L) wid to p.getLong("pat", 0L) else null
        cardioAuto = p.getBoolean("ca", false)
        loaded = true
    }

    fun pausedAt(workoutId: Long): Long? = paused.value?.takeIf { it.first == workoutId }?.second

    /** Training time so far: frozen while paused. */
    fun activeMs(workoutId: Long, startedAt: Long, now: Long = System.currentTimeMillis()): Long =
        ((pausedAt(workoutId) ?: now) - startedAt).coerceAtLeast(0L)

    private fun setPaused(c: Context, v: Pair<Long, Long>?) {
        paused.value = v
        prefs(c).edit().putLong("pw", v?.first ?: 0L).putLong("pat", v?.second ?: 0L).apply()
    }

    private fun setCardioAuto(c: Context, on: Boolean) { cardioAuto = on; prefs(c).edit().putBoolean("ca", on).apply() }

    /** Resume after an auto-pause: the paused stretch is cut out by moving the start forward. Returns minutes trimmed. */
    suspend fun resume(container: AppContainer, workoutId: Long, trim: Boolean = true): Long {
        load(container.app)
        val at = pausedAt(workoutId) ?: return 0
        val now = System.currentTimeMillis()
        val w = container.workoutRepo.workoutView(workoutId).first()?.workout
        if (w != null && trim) container.workoutRepo.updateWorkoutMeta(workoutId, w.name, w.notes, w.startedAt + (now - at), null)
        setPaused(container.app, null)
        prefs(container.app).edit().putLong("lr", now).apply()
        schedule(container.app, IDLE_MS + 15_000)
        return (now - at) / 60_000
    }

    /** End from the notification: saved with its end at the moment it paused (or discarded if nothing was logged). */
    suspend fun end(container: AppContainer, workoutId: Long) {
        load(container.app)
        val v = container.workoutRepo.workoutView(workoutId).first() ?: return
        val at = pausedAt(workoutId)
        container.restTimer.stop()
        if (v.totals.sets == 0) container.workoutRepo.discard(workoutId)
        else {
            container.workoutRepo.finish(workoutId, v.workout.name, v.workout.notes)
            if (at != null) runCatching { container.workoutRepo.updateWorkoutMeta(workoutId, v.workout.name, v.workout.notes, v.workout.startedAt, at) }
        }
        setPaused(container.app, null)
    }

    /** Call when a workout finishes or is discarded in the app, so a stale pause can't linger. */
    fun clear(c: Context, workoutId: Long) { load(c); if (pausedAt(workoutId) != null) setPaused(c, null) }

    // ------------------------------------------------------------------ checks

    /** Wakes [GuardReceiver] after [delayMs] (inexact, no special permission) to run [check] if the app is asleep. */
    fun schedule(c: Context, delayMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(c, 4305, Intent(c, com.myfit.tracker.notify.GuardReceiver::class.java).setAction(com.myfit.tracker.notify.GuardReceiver.CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi) }
    }

    /** One pass over the gym workout and the stopwatch. Safe to call often; returns quickly when nothing runs. */
    suspend fun check(container: AppContainer) {
        val c = container.app
        load(c); ActivityClock.load(c)
        val w = container.workoutRepo.inProgress.first()
        val cardio = cardioMode(ActivityClock.activity)
        if (w == null && (cardio == null || !ActivityClock.active)) {
            if (paused.value != null && w == null) setPaused(c, null)
            return
        }
        val now = System.currentTimeMillis()
        val p = prefs(c)
        // ---- movement since the last check (the step counter keeps counting while we sleep)
        val steps = readSteps(c)
        var lastStepAt = p.getLong("lsa", 0L)
        if (steps != null) {
            val prev = p.getLong("sc", -1L)
            if (prev < 0 || steps < prev) p.edit().putLong("sc", steps).apply()               // first sample or phone rebooted
            else if (steps - prev >= MOVED_STEPS) { lastStepAt = now; p.edit().putLong("sc", steps).putLong("lsa", now).apply() }
        }
        val stepsQuiet = { window: Long -> steps == null || now - lastStepAt >= window }

        // ---- gym workout
        if (w != null && pausedAt(w.id) == null) {
            val v = container.workoutRepo.workoutView(w.id).first()
            val rest = container.restTimer.state
            val lastSet = v?.exercises?.flatMap { it.sets }?.maxOfOrNull { it.completedAt } ?: 0L
            val lastAct = maxOf(w.startedAt, lastSet, p.getLong("lr", 0L), rest?.endsAt ?: 0L)
            when {
                rest != null && rest.endsAt > now -> schedule(c, rest.endsAt - now + IDLE_MS)
                now - lastAct < IDLE_MS -> schedule(c, lastAct + IDLE_MS - now + 15_000)
                stepsQuiet(IDLE_MS) && accelStill(c) != false -> {
                    val at = minOf(now, maxOf(lastAct + GRACE_MS, if (steps != null) lastStepAt else 0L))
                    setPaused(c, w.id to at)
                }
                else -> schedule(c, 2 * 60_000L)                      // idle but moving (walking between machines): look again soon
            }
        }

        // ---- stopwatch (locomotion only)
        if (cardio != null && ActivityClock.active) {
            val running = ActivityClock.running
            when (cardio) {
                Cardio.STEPS -> if (steps != null) {
                    if (running && now - ActivityClock.startedAt >= CARDIO_IDLE_MS && now - lastStepAt >= CARDIO_IDLE_MS) {
                        ActivityClock.pauseAt(c, maxOf(lastStepAt, ActivityClock.startedAt)); setCardioAuto(c, true)
                    } else if (!running && cardioAuto && now - lastStepAt < 70_000L) {
                        ActivityClock.resume(c); setCardioAuto(c, false)
                    }
                }
                Cardio.MOTION -> {
                    val still = accelStill(c)
                    val since = p.getLong("ss", 0L)
                    if (still == null) Unit
                    else if (still) {
                        if (since == 0L) p.edit().putLong("ss", now).apply()
                        else if (running && now - since >= CARDIO_IDLE_MS) { ActivityClock.pauseAt(c, maxOf(since, ActivityClock.startedAt)); setCardioAuto(c, true) }
                    } else {
                        p.edit().putLong("ss", 0L).apply()
                        if (!running && cardioAuto) { ActivityClock.resume(c); setCardioAuto(c, false) }
                    }
                }
            }
            schedule(c, 60_000L)
        } else if (cardioAuto) setCardioAuto(c, false)
    }

    /** The user paused/resumed the stopwatch themselves: stop auto-resuming it. */
    fun userTouchedStopwatch(c: Context) { load(c); if (cardioAuto) setCardioAuto(c, false) }

    private enum class Cardio { STEPS, MOTION }

    private fun cardioMode(id: String?): Cardio? = when (id) {
        "walk", "brisk", "run", "jog", "hike", "stairs" -> Cardio.STEPS
        "cycle" -> Cardio.MOTION
        else -> null
    }

    /** Current hardware step count (since boot), or null without a step sensor / permission. */
    private suspend fun readSteps(c: Context): Long? {
        val sm = c.getSystemService(SensorManager::class.java) ?: return null
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            c.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return withTimeoutOrNull(2500) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) { sm.unregisterListener(this); if (cont.isActive) cont.resume(e.values[0].toLong()) }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    /** ~2 s accelerometer sample: true = lying/sitting still, false = moving, null = no sensor. */
    private suspend fun accelStill(c: Context): Boolean? {
        val sm = c.getSystemService(SensorManager::class.java) ?: return null
        val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
        val mags = ArrayList<Float>(64)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                synchronized(mags) { mags += sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME)
        try { kotlinx.coroutines.delay(2000) } finally { sm.unregisterListener(l) }
        val m = synchronized(mags) { mags.toList() }
        if (m.size < 8) return null
        val mean = m.average()
        val variance = m.sumOf { (it - mean) * (it - mean) } / m.size
        return variance < 0.06                                    // a phone on a bench or in a still pocket sits well under this
    }
}
