package com.myfit.tracker.data.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backups in your own Google Drive, in the hidden app-data folder (only MyFit can see it; it doesn't fill your
 * Drive file list and doesn't count against anything but your Drive storage). Uses Google's authorization
 * (drive.appdata scope only) — no Drive-wide access.
 */
object DriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val NAME = "myfit-backup.zip"
    private const val KEEP = 3

    sealed interface Auth {
        data class Token(val token: String) : Auth
        /** Google needs the user to pick an account / approve: launch this, then call [tokenFrom]. */
        data class NeedsUi(val intent: PendingIntent) : Auth
    }

    data class Remote(val id: String, val name: String, val modified: String, val size: Long)

    suspend fun authorize(activity: Activity): Auth {
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val r = Identity.getAuthorizationClient(activity).authorize(req).await()
        return if (r.hasResolution()) Auth.NeedsUi(r.pendingIntent!!) else Auth.Token(r.accessToken ?: throw IllegalStateException("Google didn't return access"))
    }

    fun tokenFrom(ctx: Context, data: android.content.Intent?): String =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken ?: throw IllegalStateException("Drive access wasn't granted")

    private fun conn(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.ok(): HttpURLConnection {
        if (responseCode !in 200..299) {
            val msg = runCatching { errorStream?.readBytes()?.decodeToString() }.getOrNull().orEmpty()
            throw IllegalStateException(if (responseCode == 403 && "accessNotConfigured" in msg) "Google Drive isn't enabled for MyFit yet" else "Drive error $responseCode")
        }
        return this
    }

    suspend fun list(token: String): List<Remote> = withContext(Dispatchers.IO) {
        val c = conn("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime,size)&pageSize=20", token).ok()
        val a = JSONObject(c.inputStream.readBytes().decodeToString()).optJSONArray("files")
        (0 until (a?.length() ?: 0)).map { i -> a!!.getJSONObject(i).let { Remote(it.getString("id"), it.optString("name"), it.optString("modifiedTime"), it.optString("size").toLongOrNull() ?: 0) } }
    }

    /** Builds a fresh backup and uploads it; keeps the newest [KEEP] copies. */
    suspend fun upload(ctx: Context, token: String): Long = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "drive_backup.zip")
        tmp.outputStream().use { Backup.write(ctx, it) }
        val boundary = "myfit" + System.nanoTime()
        val meta = JSONObject().put("name", NAME).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val c = conn("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token, "POST").apply {
            doOutput = true; setChunkedStreamingMode(256 * 1024)
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        c.outputStream.buffered().use { o ->
            o.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
            tmp.inputStream().use { it.copyTo(o) }
            o.write("\r\n--$boundary--\r\n".toByteArray())
        }
        c.ok()
        val size = tmp.length(); tmp.delete()
        runCatching { list(token).drop(KEEP).forEach { conn("https://www.googleapis.com/drive/v3/files/${it.id}", token, "DELETE").responseCode } }
        size
    }

    suspend fun restore(ctx: Context, token: String, id: String) = withContext(Dispatchers.IO) {
        val c = conn("https://www.googleapis.com/drive/v3/files/$id?alt=media", token).ok()
        c.inputStream.use { Backup.restore(ctx, it) }
    }
}
