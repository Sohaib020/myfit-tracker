package com.myfit.tracker.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.myfit.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from this app's GitHub Releases (until it's on a store). Checks the latest release, downloads the
 * APK in the background (Wi-Fi by default), then asks Android to install it — Android always shows one
 * "Install" confirmation for apps outside a store; nothing installs silently.
 */
object AppUpdater {
    const val REPO = "Sohaib020/myfit-tracker"
    const val SITE = "https://sohaib020.github.io/myfit-tracker/"
    const val LATEST_APK = "https://github.com/$REPO/releases/latest/download/MyFitTracker.apk"

    data class Release(val build: Int, val tag: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val date: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val r: Release) : State
        data class Downloading(val r: Release, val done: Long, val total: Long) : State
        data class Ready(val r: Release, val file: File) : State
        data class Failed(val msg: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    val current: Int get() = BuildConfig.VERSION_CODE

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("updater", Context.MODE_PRIVATE)
    fun autoDownload(c: Context) = prefs(c).getBoolean("auto", true)
    fun setAutoDownload(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()
    fun dismissed(c: Context) = prefs(c).getInt("dismissed", 0)
    fun dismiss(c: Context, build: Int) = prefs(c).edit().putInt("dismissed", build).apply()

    private fun dir(c: Context) = File(c.cacheDir, "updates").apply { mkdirs() }

    fun unmetered(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Queries GitHub for the newest release. */
    suspend fun check(c: Context, force: Boolean = false): State = withContext(Dispatchers.IO) {
        val p = prefs(c)
        if (!force && System.currentTimeMillis() - p.getLong("last", 0) < 6 * 3600_000L && state.value !is State.Idle) return@withContext state.value
        state.value = State.Checking
        val s = runCatching {
            val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json"); connectTimeout = 15_000; readTimeout = 15_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            val tag = j.getString("tag_name")
            val build = tag.removePrefix("build-").toIntOrNull() ?: 0
            val assets = j.getJSONArray("assets")
            var url = ""; var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i); val n = a.getString("name")
                if (n.endsWith(".apk") && (url.isEmpty() || n == "MyFitTracker.apk")) { url = a.getString("browser_download_url"); size = a.optLong("size") }
            }
            p.edit().putLong("last", System.currentTimeMillis()).apply()
            val r = Release(build, tag, j.optString("body").lineSequence().filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") || it.isBlank() }.joinToString("\n").take(600),
                url, size, j.optString("published_at").take(10))
            when {
                url.isEmpty() || build <= current -> State.UpToDate
                else -> File(dir(c), "MyFitTracker-$build.apk").takeIf { it.exists() && (size <= 0 || it.length() == size) }?.let { State.Ready(r, it) } ?: State.Available(r)
            }
        }.getOrElse { State.Failed("Couldn't reach GitHub — check your connection") }
        state.value = s
        s
    }

    /** Downloads the APK with progress (resumes nothing; small enough to restart). */
    suspend fun download(c: Context, r: Release): State = withContext(Dispatchers.IO) {
        dir(c).listFiles()?.filter { it.name != "MyFitTracker-${r.build}.apk" }?.forEach { it.delete() }
        val out = File(dir(c), "MyFitTracker-${r.build}.apk"); val tmp = File(out.path + ".part")
        val s = runCatching {
            var conn = URL(r.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true; conn.connectTimeout = 20_000; conn.readTimeout = 30_000
            // GitHub redirects to a CDN on another host; follow manually if needed
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn = URL(loc).openConnection() as HttpURLConnection
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            var done = 0L; var lastEmit = 0L
            conn.inputStream.use { inp -> tmp.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (done - lastEmit > 256 * 1024) { lastEmit = done; state.value = State.Downloading(r, done, total) }
                }
            } }
            if (total > 0 && done != total) error("incomplete")
            tmp.renameTo(out)
            State.Ready(r, out)
        }.getOrElse { tmp.delete(); State.Failed("Download failed — try again on Wi-Fi") }
        state.value = s
        s
    }

    fun canInstall(c: Context) = Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    /** Opens the one-time "Allow from this source" screen. */
    fun openInstallPermission(c: Context) {
        c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun autoInstall(c: Context) = prefs(c).getBoolean("autoInstall", true)
    fun setAutoInstall(c: Context, on: Boolean) = prefs(c).edit().putBoolean("autoInstall", on).apply()

    /** Installs a downloaded update: session install (silent after the first time on Android 12+), else the classic installer screen. */
    fun install(c: Context, f: File) {
        if (canInstall(c) && runCatching { SelfInstaller.install(c.applicationContext, f) }.isSuccess) return
        installLegacy(c, f)
    }

    /** Called when the app goes to the background: a ready update installs then, so it never interrupts you. */
    fun installIfReady(c: Context) {
        val s = state.value
        if (s is State.Ready && autoInstall(c) && canInstall(c) && s.file.exists()) runCatching { SelfInstaller.install(c.applicationContext, s.file) }
    }

    private fun installLegacy(c: Context, f: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called on app start: check (at most every 6 h) and, on Wi-Fi, fetch the update in the background. */
    suspend fun autoRun(c: Context) {
        val s = check(c)
        if (s is State.Available && autoDownload(c) && unmetered(c)) download(c, s.r)
    }
}
