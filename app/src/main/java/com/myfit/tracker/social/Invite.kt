package com.myfit.tracker.social

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.myfit.tracker.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Friend invites: a web link (works for people who don't have the app yet — the page offers the download and an
 * "Open in MyFit" button) and the app's own myfit://invite?c=CODE deep link.
 */
object Invite {
    /** Friend code waiting to be added (from a link that opened the app). */
    val pending = MutableStateFlow<String?>(null)

    fun link(code: String, name: String): String =
        AppUpdater.SITE + "invite.html?c=" + Uri.encode(code) + "&n=" + Uri.encode(name.take(24))

    /** Pulls a friend code out of a myfit:// or invite-page link, or a bare code. */
    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim()
        val u = runCatching { Uri.parse(t) }.getOrNull()
        val c = u?.takeIf { it.scheme == "myfit" || it.path?.contains("invite") == true }?.getQueryParameter("c")
            ?: t.takeIf { it.matches(Regex("^[A-Za-z0-9]{4,12}$")) }
        return c?.uppercase()
    }

    fun handle(intent: Intent?) { parse(intent?.dataString)?.let { pending.value = it } }

    fun share(ctx: Context, code: String, name: String) {
        val text = "Join me on MyFit Tracker! Let's race each other in the Arena 🏃\n${link(code, name)}\n\nMy friend code: $code"
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Invite a friend")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun qr(text: String, px: Int = 640, fg: Int = 0xFF111318.toInt(), bg: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) fg else bg }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}
