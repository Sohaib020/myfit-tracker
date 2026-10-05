package com.myfit.tracker.notify

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Targets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Sends today's steps, water and calories to the Galaxy Watch tile (Wearable Data Layer, path /myfit/today). */
object WearSync {
    @Volatile private var lastSig = ""

    suspend fun push(c: Context) {
        runCatching {
            val n = com.myfit.tracker.widget.loadNumbers(c)
            val k = (c.applicationContext as MyFitApplication).container
            val today = Clock.today()
            val kcal = k.nutritionRepo.dailyTotals(today, today).first().sumOf { it.kcal }
            val kcalGoal = Targets.on(k.profileRepo.targets.first(), TargetType.CALORIES, today) ?: 0.0
            val sig = "${n.steps}|${n.waterMl}|${kcal.toInt()}|${n.stepTarget}|${n.waterTarget}|$kcalGoal|$today"
            if (sig == lastSig) return
            // no watch paired → nothing to do (saves a Play-services round trip)
            if (Wearable.getNodeClient(c).connectedNodes.await().isEmpty()) return
            val req = PutDataMapRequest.create("/myfit/today").apply {
                dataMap.putLong("steps", n.steps ?: 0L)
                dataMap.putLong("stepGoal", n.stepTarget?.toLong() ?: 0L)
                dataMap.putDouble("water", n.waterMl)
                dataMap.putDouble("waterGoal", n.waterTarget ?: 0.0)
                dataMap.putDouble("kcal", kcal)
                dataMap.putDouble("kcalGoal", kcalGoal)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(c).putDataItem(req).await()
            lastSig = sig
        }
    }
}
