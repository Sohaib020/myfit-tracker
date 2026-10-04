package com.myfit.tracker.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Installs an update through a PackageInstaller session. On Android 12+ the very first update still shows the system
 * confirmation (Android requires it while another app — the browser or file manager — is the installer of record);
 * after that MyFit is its own installer and later updates install without any tap.
 */
object SelfInstaller {
    fun install(c: Context, f: File) {
        val pi = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
            setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 26) setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(c, id, Intent(c, InstallResultReceiver::class.java).setPackage(c.packageName), flags)
            s.commit(pending.intentSender)
        }
    }
}

/** Session callback: shows the system confirmation when Android needs one; reports failures back to the updater. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> AppUpdater.state.value = AppUpdater.State.Failed("Update didn't install: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"}")
        }
    }
}

/** After a self-update, tell the user it worked (the app was closed to update). */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            val nm = c.getSystemService(android.app.NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("updates", "App updates", android.app.NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(c, 0, c.packageManager.getLaunchIntentForPackage(c.packageName), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(7001, androidx.core.app.NotificationCompat.Builder(c, "updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("MyFit Tracker updated").setContentText("Build ${com.myfit.tracker.BuildConfig.VERSION_CODE} is installed — tap to see what's new.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
