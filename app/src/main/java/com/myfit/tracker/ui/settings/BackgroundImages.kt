package com.myfit.tracker.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

/** Custom wallpapers are copied into private storage (downscaled) so they survive the source photo being deleted. */
object BackgroundImages {
    private const val MAX_EDGE = 1600

    fun import(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val name = "bg_${System.currentTimeMillis()}.jpg"
        File(context.filesDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        // remove older wallpapers
        context.filesDir.listFiles { f -> f.name.startsWith("bg_") && f.name != name }?.forEach { it.delete() }
        name
    }.getOrNull()

    fun load(dir: File, name: String): Bitmap? = runCatching {
        val f = File(dir, name)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()
}
