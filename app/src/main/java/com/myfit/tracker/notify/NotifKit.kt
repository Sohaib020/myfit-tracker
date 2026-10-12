package com.myfit.tracker.notify

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myfit.tracker.R

/** Accent colour, emoji and badge for each kind of notification. */
enum class NKind(val color: Int, val emoji: String) {
    RUN(0xFF22C27A.toInt(), "🏃"), LIFT(0xFFFF7A2F.toInt(), "🏋️"), REST(0xFF3F7BFF.toInt(), "⏱️"), FLAME(0xFFFF5A36.toInt(), "🔥"),
    DROP(0xFF2F9BFF.toInt(), "💧"), PILL(0xFF9B6BFF.toInt(), "💊"), BELL(0xFFFFA51F.toInt(), "🔔"), MOON(0xFF5B6CFF.toInt(), "🌙"),
    TROPHY(0xFFF2A900.toInt(), "🏆"), HEART(0xFFFF4F86.toInt(), "🤝"), SUGAR(0xFFE2453C.toInt(), "🩸"), MEAL(0xFFFF7A59.toInt(), "🍽️"),
    GOALS(0xFF12B886.toInt(), "🎯");

    /** Round badge drawn in the card header and riding the progress track. */
    val tracker: Int get() = when (this) {
        RUN, GOALS -> R.drawable.nt_run; LIFT, TROPHY -> R.drawable.nt_lift; REST, MOON, PILL, BELL -> R.drawable.nt_rest
        FLAME, SUGAR, HEART, MEAL -> R.drawable.nt_flame; DROP -> R.drawable.nt_drop
    }
}

/**
 * Everything a card can show. Anything left null is hidden.
 * [chrono] = the time a live clock counts from (or down to, with [countDown]); [progress] 0..1; [indeterminate] = a
 * moving bar for open-ended activities; [stats] = up to three value/label pairs; [eyebrow] = small caps label
 * ("WORKOUT · LIVE"); [unit] = word next to the big clock ("left", "elapsed").
 */
data class NCard(
    val kind: NKind, val title: String, val text: String,
    val chrono: Long? = null, val countDown: Boolean = false,
    val value: String? = null, val chip: String? = null,
    val progress: Float? = null, val indeterminate: Boolean = false, val progressLabel: String? = null,
    val stats: List<Pair<String, String>> = emptyList(), val image: Bitmap? = null,
    val eyebrow: String? = null, val unit: String? = null,
    /** > 1 = the bar is split into this many pills (one per exercise); progress fills them in order. */
    val segments: Int = 0,
)

/**
 * Two looks:
 *  - [apply]: everyday notifications — clean, professional text with one emoji (reminders, nudges, reports, friends).
 *  - [live]: things happening right now (workout, rest, run, fast) on phones without Android 16 Live Updates — a
 *    ride-hailing-style card with a big live clock and a progress track with a moving badge.
 */
object NotifKit {
    private fun chronoBase(epochMs: Long): Long = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - epochMs)

    /** "💧 Time to drink water" — adds the kind's emoji unless the title already starts with one. */
    fun titled(c: NCard): String {
        val t = c.title.trim()
        val first = t.codePointAt(0)
        val hasEmoji = t.isNotEmpty() && (Character.getType(first) == Character.OTHER_SYMBOL.toInt() || first >= 0x1F000)
        return if (hasEmoji) t else "${c.kind.emoji} $t"
    }

    /** Everyday text notification: emoji title, readable body, optional native progress bar. */
    fun apply(ctx: Context, b: NotificationCompat.Builder, c: NCard): NotificationCompat.Builder {
        val body = listOfNotNull(c.text.takeIf { it.isNotBlank() }, c.progressLabel).joinToString("\n")
        b.setContentTitle(titled(c))
            .setContentText(c.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(titled(c)))
            .setColor(c.kind.color)
        (c.chip ?: c.value)?.let { b.setSubText(it) }
        if (c.progress != null) b.setProgress(1000, (c.progress.coerceIn(0f, 1f) * 1000).toInt(), false)
        if (c.image != null) b.setLargeIcon(c.image)
        return b
    }

    /** Live card in the system frame (collapsed + expanded custom views). */
    fun live(ctx: Context, b: NotificationCompat.Builder, c: NCard, points: List<Float> = emptyList()): NotificationCompat.Builder = b
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(small(ctx, c, points))
        .setCustomBigContentView(big(ctx, c, points))
        .setColor(c.kind.color)
        .setContentTitle(titled(c)).setContentText(c.text)     // watches, lock screen, accessibility

    private fun clock(rv: RemoteViews, c: NCard) {
        if (c.chrono != null) {
            rv.setViewVisibility(R.id.n_chrono, View.VISIBLE)
            rv.setChronometer(R.id.n_chrono, chronoBase(c.chrono), null, true)
            if (Build.VERSION.SDK_INT >= 24) rv.setChronometerCountDown(R.id.n_chrono, c.countDown)
        } else if (c.value != null) {
            rv.setViewVisibility(R.id.n_value, View.VISIBLE)
            rv.setTextViewText(R.id.n_value, c.value)
        }
        // R20: the big clock uses the shade's own primary text colour (readable in light and dark); colour stays in the accents
    }

    fun small(ctx: Context, c: NCard, points: List<Float> = emptyList()): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_live_small).apply {
        setImageViewResource(R.id.n_icon, c.kind.tracker)
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        clock(this, c)
        if (c.progress != null) {
            setViewVisibility(R.id.n_track, View.VISIBLE)
            setImageViewBitmap(R.id.n_track, track(ctx, c.kind, c.progress, points, small = true, segments = c.segments))
        }
    }

    fun big(ctx: Context, c: NCard, points: List<Float> = emptyList()): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_live_big).apply {
        setImageViewResource(R.id.n_icon, c.kind.tracker)
        setTextViewText(R.id.n_eyebrow, (c.eyebrow ?: "MYFIT").uppercase())
        setTextColor(R.id.n_eyebrow, c.kind.color)
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        val img = c.image ?: c.progress?.let { ring(c.kind, it, c.chip ?: "${(it.coerceIn(0f, 1f) * 100).toInt()}%") }
        if (img != null) { setViewVisibility(R.id.n_image, View.VISIBLE); setImageViewBitmap(R.id.n_image, img) }
        clock(this, c)
        if (c.unit != null && (c.chrono != null || c.value != null)) { setViewVisibility(R.id.n_unit, View.VISIBLE); setTextViewText(R.id.n_unit, c.unit) }
        // header pill: a live dot while a clock runs, else the card's own chip ("Paused", "Iftar" …)
        val pill = if (c.chrono != null) "● LIVE" else c.chip
        if (pill != null) { setViewVisibility(R.id.n_chip, View.VISIBLE); setTextViewText(R.id.n_chip, pill); setTextColor(R.id.n_chip, c.kind.color) }
        when {
            c.progress != null -> {
                setViewVisibility(R.id.n_track, View.VISIBLE)
                setImageViewBitmap(R.id.n_track, track(ctx, c.kind, c.progress, points, small = false, segments = c.segments))
            }
            c.indeterminate -> {
                setViewVisibility(R.id.n_bar_ind, View.VISIBLE)
                if (Build.VERSION.SDK_INT >= 31) setColorStateList(R.id.n_bar_ind, "setIndeterminateTintList", ColorStateList.valueOf(c.kind.color))
            }
        }
        if (c.progressLabel != null) { setViewVisibility(R.id.n_bar_label, View.VISIBLE); setTextViewText(R.id.n_bar_label, c.progressLabel) }
        if (c.stats.isNotEmpty()) {
            setViewVisibility(R.id.n_stats, View.VISIBLE)
            val ids = listOf(Triple(R.id.n_stat1, R.id.n_stat1_v, R.id.n_stat1_l), Triple(R.id.n_stat2, R.id.n_stat2_v, R.id.n_stat2_l), Triple(R.id.n_stat3, R.id.n_stat3_v, R.id.n_stat3_l))
            c.stats.take(3).forEachIndexed { i, (v, l) ->
                setViewVisibility(ids[i].first, View.VISIBLE); setTextViewText(ids[i].second, v); setTextViewText(ids[i].third, l)
            }
        }
    }

    /**
     * R20 progress bar: a slim rounded track that fills with a soft gradient and ends in a glowing tip — or, with
     * [segments] > 1, one pill per step (done = solid, current = part-filled, next = faint). [points] = small ticks.
     */
    fun track(ctx: Context, kind: NKind, progress: Float, points: List<Float>, small: Boolean, segments: Int = 0): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels - 64 * dm.density).toInt().coerceIn(320, 1400)
        val h = ((if (small) 8 else 12) * dm.density).toInt().coerceAtLeast(12)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val col = kind.color
        val faint = (col and 0x00FFFFFF) or 0x2E000000
        val barH = h * if (small) 0.62f else 0.5f
        val cy = h / 2f
        val f = progress.coerceIn(0f, 1f)
        val left = h / 2f; val right = w - h / 2f
        fun pill(x0: Float, x1: Float, paint: Paint) { if (x1 > x0) cv.drawRoundRect(RectF(x0, cy - barH / 2, x1, cy + barH / 2), barH, barH, paint) }
        if (segments in 2..16) {
            val gap = barH * 0.9f
            val segW = (right - left - gap * (segments - 1)) / segments
            val filled = f * segments
            for (i in 0 until segments) {
                val x0 = left + i * (segW + gap); val x1 = x0 + segW
                p.shader = null; p.color = faint; pill(x0, x1, p)
                val part = (filled - i).coerceIn(0f, 1f)
                if (part > 0f) {
                    p.shader = LinearGradient(x0, 0f, x1, 0f, (col and 0x00FFFFFF) or 0xB3000000.toInt(), col, Shader.TileMode.CLAMP)
                    pill(x0, maxOf(x0 + barH, x0 + segW * part), p)
                }
            }
            p.shader = null
        } else {
            val x = left + (right - left) * f
            p.color = faint; pill(left, right, p)
            if (f > 0f) {
                p.shader = LinearGradient(left, 0f, maxOf(x, left + barH), 0f, (col and 0x00FFFFFF) or 0x99000000.toInt(), col, Shader.TileMode.CLAMP)
                pill(left, maxOf(x, left + barH), p); p.shader = null
                // glowing tip
                p.color = (col and 0x00FFFFFF) or 0x55000000; cv.drawCircle(maxOf(x, left + barH / 2), cy, h / 2f, p)
                p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(maxOf(x, left + barH / 2), cy, barH * 0.42f, p)
            }
            points.filter { it in 0.02f..0.98f }.forEach { pt ->
                val px = left + (right - left) * pt
                p.color = if (pt <= f) 0xE6FFFFFF.toInt() else (col and 0x00FFFFFF) or 0x8C000000.toInt()
                cv.drawCircle(px, cy, barH * 0.22f, p)
            }
        }
        return bmp
    }

    /** Progress ring with a short centre label in the kind's colour (readable on light and dark shades). */
    fun ring(kind: NKind, progress: Float, label: String, sizePx: Int = 200): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val stroke = sizePx * 0.1f
        val r = RectF(stroke / 2 + 2, stroke / 2 + 2, sizePx - stroke / 2 - 2, sizePx - stroke / 2 - 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = (kind.color and 0x00FFFFFF) or 0x2E000000; cv.drawArc(r, 0f, 360f, false, p)
        p.color = kind.color; cv.drawArc(r, -90f, 360f * progress.coerceIn(0.015f, 1f), false, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = kind.color; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            textSize = sizePx * (if (label.length <= 4) 0.27f else 0.2f)
        }
        cv.drawText(label.take(6), sizePx / 2f, sizePx / 2f - (t.descent() + t.ascent()) / 2, t)
        return bmp
    }

    /** Daily goal rings (steps / water / calories …) for the daily card's image slot. */
    fun rings(values: List<Pair<Float, Int>>, sizePx: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val stroke = sizePx * 0.09f
        values.take(3).forEachIndexed { i, (frac, color) ->
            val inset = stroke / 2 + i * (stroke * 1.25f)
            val r = RectF(inset, inset, sizePx - inset, sizePx - inset)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
            p.color = (color and 0x00FFFFFF) or 0x33000000; cv.drawArc(r, 0f, 360f, false, p)
            p.color = color; cv.drawArc(r, -90f, 360f * frac.coerceIn(0.02f, 1f), false, p)
        }
        return bmp
    }
}
