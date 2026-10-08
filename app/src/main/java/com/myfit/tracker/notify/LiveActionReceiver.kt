package com.myfit.tracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.ui.activity.ActivityClock
import kotlinx.coroutines.flow.first

/** Buttons on live notifications: rest +15 s / skip, stopwatch pause / resume. */
class LiveActionReceiver : BroadcastReceiver() {
    companion object {
        const val REST_ADD = "com.myfit.tracker.live.REST_ADD"
        const val REST_SKIP = "com.myfit.tracker.live.REST_SKIP"
        const val SW_PAUSE = "com.myfit.tracker.live.SW_PAUSE"
        const val SW_RESUME = "com.myfit.tracker.live.SW_RESUME"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as? MyFitApplication)?.container ?: return
        val pending = goAsync()
        c.write {
            try {
                when (intent.action) {
                    REST_ADD -> { val s = c.settings.settings.first(); c.restTimer.add(15, s.restSound, s.restVibrate) }
                    REST_SKIP -> c.restTimer.stop()?.let { (setId, sec) -> c.workoutRepo.recordRest(setId, sec) }
                    SW_PAUSE -> { ActivityClock.load(context); ActivityClock.pause(context) }
                    SW_RESUME -> { ActivityClock.load(context); ActivityClock.resume(context) }
                }
            } finally { pending.finish() }
        }
    }
}
