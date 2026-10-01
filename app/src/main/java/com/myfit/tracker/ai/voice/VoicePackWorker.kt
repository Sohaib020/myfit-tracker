package com.myfit.tracker.ai.voice

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfit.tracker.MyFitApplication

/** Fetches the on-device voice pack automatically the first time the phone is on Wi-Fi. */
class VoicePackWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MyFitApplication
        return if (app.container.pipVoice.pack.downloadNow()) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<VoicePackWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("voice-pack", ExistingWorkPolicy.KEEP, req)
        }
    }
}
