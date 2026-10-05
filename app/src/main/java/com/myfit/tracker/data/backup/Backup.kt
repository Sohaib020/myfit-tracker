package com.myfit.tracker.data.backup

import android.content.Context
import android.net.Uri
import com.myfit.tracker.BuildConfig
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup of everything you logged: the database, settings, preferences, your plans and photos — one .zip.
 * Not included: the offline AI model / voice pack / buddy packs (re-downloadable) and caches. Saved API keys are
 * encrypted with this phone's keystore, so after restoring on another phone you re-enter them.
 */
object Backup {
    private const val FORMAT = 1
    private val SKIP_FILES = setOf("llm", "voice", "buddy", "datastore", "restore_staging")
    private val SKIP_PREFS = listOf("com.google", "FirebaseHeartBeat", "WebViewChromiumPrefs", "social_upload", "com.facebook")

    data class Info(val createdAt: Long, val appVersion: String, val dbVersion: Int, val bytes: Long)

    /** Writes the backup zip to [out]. Returns the number of bytes of data included. */
    suspend fun write(ctx: Context, out: OutputStream): Long = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext as MyFitApplication
        val db = app.container.db
        // flush the write-ahead log so the main db file is complete
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        var total = 0L
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, f: File) {
                if (!f.isFile) return
                zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry(); total += f.length()
            }
            val meta = JSONObject().put("format", FORMAT).put("app", BuildConfig.VERSION_NAME).put("code", BuildConfig.VERSION_CODE)
                .put("db", db.openHelper.readableDatabase.version).put("createdAt", System.currentTimeMillis())
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(meta.toString().toByteArray()); zip.closeEntry()
            put("db/${AppDatabase.NAME}", app.getDatabasePath(AppDatabase.NAME))
            File(app.filesDir, "datastore").listFiles()?.forEach { put("datastore/${it.name}", it) }
            File(app.filesDir.parentFile, "shared_prefs").listFiles()?.filter { f -> SKIP_PREFS.none { f.name.startsWith(it) } }?.forEach { put("prefs/${it.name}", it) }
            app.filesDir.listFiles()?.filter { it.name !in SKIP_FILES }?.forEach { top ->
                top.walkTopDown().filter { it.isFile }.forEach { f -> put("files/" + f.relativeTo(app.filesDir).path.replace('\\', '/'), f) }
            }
        }
        total
    }

    suspend fun writeTo(ctx: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "w")?.use { write(ctx, it) } ?: throw IllegalStateException("Couldn't open that file")
    }

    /** Reads only the manifest (to show what a backup is before restoring). */
    fun peek(input: InputStream): Info? = runCatching {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: return@use null
                if (e.name == "manifest.json") {
                    val o = JSONObject(z.readBytes().decodeToString())
                    return@use Info(o.getLong("createdAt"), o.optString("app"), o.optInt("db"), 0)
                }
            }
        }
    }.getOrNull()

    /**
     * Restores a backup: unpacks to a staging folder, checks it, then swaps it in and restarts the app.
     * Your current data is replaced. Throws (changing nothing) if the file isn't a valid MyFit backup.
     */
    suspend fun restore(ctx: Context, input: InputStream) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext as MyFitApplication
        val stage = File(app.filesDir, "restore_staging").apply { deleteRecursively(); mkdirs() }
        try {
            var meta: JSONObject? = null
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val out = File(stage, e.name).canonicalFile
                    if (!out.path.startsWith(stage.canonicalPath + File.separator)) continue   // zip-slip guard
                    if (e.name == "manifest.json") { meta = JSONObject(z.readBytes().decodeToString()); continue }
                    out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) }
                }
            }
            val m = meta ?: throw IllegalArgumentException("That isn't a MyFit backup")
            if (m.optInt("format") > FORMAT) throw IllegalArgumentException("This backup is from a newer MyFit — update the app first")
            val dbFile = File(stage, "db/${AppDatabase.NAME}")
            if (!dbFile.isFile) throw IllegalArgumentException("The backup has no database")
            val current = app.container.db.openHelper.readableDatabase.version
            if (m.optInt("db") > current) throw IllegalArgumentException("This backup is from a newer MyFit — update the app first")

            // swap in: database, settings, prefs, files
            runCatching { app.container.db.close() }
            val dbPath = app.getDatabasePath(AppDatabase.NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbPath.path + it).delete() }
            dbFile.copyTo(dbPath, overwrite = true)
            File(stage, "datastore").takeIf { it.isDirectory }?.let { src -> val dst = File(app.filesDir, "datastore"); dst.deleteRecursively(); src.copyRecursively(dst, true) }
            File(stage, "prefs").takeIf { it.isDirectory }?.listFiles()?.forEach { it.copyTo(File(app.filesDir.parentFile, "shared_prefs/${it.name}"), true) }
            File(stage, "files").takeIf { it.isDirectory }?.let { src ->
                src.walkTopDown().filter { it.isFile }.forEach { f -> f.copyTo(File(app.filesDir, f.relativeTo(src).path), true) }
            }
        } finally {
            stage.deleteRecursively()
        }
    }
}
