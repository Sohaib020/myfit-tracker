package com.myfit.tracker.ai.ondevice

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.launch

/**
 * R21: the offline brain's slow first start (LiteRT-LM builds its GPU/CPU caches for this phone + app build) is done in
 * the background — right after the model download finishes, after every app update, and on the first app start of a
 * new version — so Pip answers quickly the first time someone actually types. Runs once per (app version, model file).
 */
class BrainWarmup(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val hub = OnDeviceAi.get(applicationContext)
        if (!hub.llm.available()) return Result.success()
        val key = key(applicationContext) ?: return Result.success()
        if (done(applicationContext) == key) return Result.success()
        return runCatching { hub.llm.warmUp() }.fold(
            onSuccess = { applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString("done", key).apply(); Result.success() },
            onFailure = { if (runAttemptCount < 2) Result.retry() else Result.failure() },
        )
    }

    companion object {
        private const val P = "brain_warmup"
        private const val WORK = "brain_warmup"

        private fun done(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE).getString("done", null)

        /** "versionCode|model file|size" — changes after an app update or a new model. */
        private suspend fun key(c: Context): String? {
            val f = OnDeviceAi.get(c).models.installedFile() ?: return null
            val v = runCatching { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0)) }.getOrDefault(0L)
            return "$v|${f.name}|${f.length()}"
        }

        /** Queue the warm-up if this app version + model hasn't been warmed yet. Cheap; safe to call often. */
        fun schedule(c: Context) {
            val app = c.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch { enqueue(app) }
        }

        private suspend fun enqueue(app: Context) {
            val k = key(app) ?: return
            if (done(app) == k) return
            val req = OneTimeWorkRequestBuilder<BrainWarmup>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
        }
    }
}
