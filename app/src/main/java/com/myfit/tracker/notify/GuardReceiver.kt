package com.myfit.tracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.gym.WorkoutGuard
import kotlinx.coroutines.flow.first

/** R20 auto-pause: the periodic idle check (alarm) and the "Still training?" buttons. */
class GuardReceiver : BroadcastReceiver() {
    companion object {
        const val CHECK = "com.myfit.tracker.guard.CHECK"
        const val RESUME = "com.myfit.tracker.guard.RESUME"
        const val END = "com.myfit.tracker.guard.END"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as? MyFitApplication)?.container ?: return
        val pending = goAsync()
        c.write {
            try {
                WorkoutGuard.load(context)
                val wid = WorkoutGuard.paused.value?.first ?: c.workoutRepo.inProgress.first()?.id
                when (intent.action) {
                    CHECK -> WorkoutGuard.check(c)
                    RESUME -> if (wid != null) WorkoutGuard.resume(c, wid)
                    END -> if (wid != null) WorkoutGuard.end(c, wid)
                }
            } finally { pending.finish() }
        }
    }
}
