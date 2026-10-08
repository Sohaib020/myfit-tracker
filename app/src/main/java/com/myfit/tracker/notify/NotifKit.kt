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
            rv.setTextColor(R.id.n_chrono, c.kind.color)
        } else if (c.value != null) {
            rv.setViewVisibility(R.id.n_value, View.VISIBLE)
            rv.setTextViewText(R.id.n_value, c.value)
            rv.setTextColor(R.id.n_value, c.kind.color)
        }
    }

    fun small(ctx: Context, c: NCard, points: List<Float> = emptyList()): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_live_small).apply {
        setImageViewResource(R.id.n_icon, c.kind.tracker)
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        clock(this, c)
        if (c.progress != null) {
            setViewVisibility(R.id.n_track, View.VISIBLE)
            setImageViewBitmap(R.id.n_track, track(ctx, c.kind, c.progress, points, small = true))
        }
    }

    fun big(ctx: Context, c: NCard, points: List<Float> = emptyList()): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_live_big).apply {
        setImageViewResource(R.id.n_icon, c.kind.tracker)
        setTextViewText(R.id.n_eyebrow, (c.eyebrow ?: "MYFIT").uppercase())
        setTextColor(R.id.n_eyebrow, c.kind.color)
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        if (c.image != null) { setViewVisibility(R.id.n_image, View.VISIBLE); setImageViewBitmap(R.id.n_image, c.image) }
        clock(this, c)
        if (c.unit != null && (c.chrono != null || c.value != null)) { setViewVisibility(R.id.n_unit, View.VISIBLE); setTextViewText(R.id.n_unit, c.unit) }
        if (c.chip != null) { setViewVisibility(R.id.n_chip, View.VISIBLE); setTextViewText(R.id.n_chip, c.chip); setTextColor(R.id.n_chip, c.kind.color) }
        when {
            c.progress != null -> {
                setViewVisibility(R.id.n_track, View.VISIBLE)
                setImageViewBitmap(R.id.n_track, track(ctx, c.kind, c.progress, points, small = false))
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
     * The progress track: rounded bar filled to [progress] with a soft gradient, milestone dots at [points] and the
     * kind's round badge riding the front — like a car moving along a ride route.
     */
    fun track(ctx: Context, kind: NKind, progress: Float, points: List<Float>, small: Boolean): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels - 64 * dm.density).toInt().coerceIn(320, 1400)
        val h = ((if (small) 16 else 30) * dm.density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val badge = h * if (small) 0.95f else 0.92f
        val left = badge / 2; val right = w - badge / 2
        val barH = h * if (small) 0.32f else 0.26f
        val cy = h / 2f
        val f = progress.coerceIn(0f, 1f)
        val x = left + (right - left) * f
        // track
        p.color = (kind.color and 0x00FFFFFF) or 0x26000000
        cv.drawRoundRect(RectF(left, cy - barH / 2, right, cy + barH / 2), barH, barH, p)
        // fill
        if (x > left + 1) {
            p.shader = LinearGradient(left, 0f, x, 0f, (kind.color and 0x00FFFFFF) or 0xCC000000.toInt(), kind.color, Shader.TileMode.CLAMP)
            cv.drawRoundRect(RectF(left, cy - barH / 2, x, cy + barH / 2), barH, barH, p)
            p.shader = null
        }
        // milestones
        points.filter { it in 0.02f..0.98f }.forEach { pt ->
            val px = left + (right - left) * pt
            val passed = pt <= f
            p.color = if (passed) 0xFFFFFFFF.toInt() else (kind.color and 0x00FFFFFF) or 0x66000000
            cv.drawCircle(px, cy, barH * 0.9f, p)
            if (passed) { p.color = kind.color; cv.drawCircle(px, cy, barH * 0.45f, p) }
        }
        // finish flag dot
        p.color = if (f >= 1f) kind.color else (kind.color and 0x00FFFFFF) or 0x80000000.toInt()
        cv.drawCircle(right, cy, barH * 1.05f, p)
        // badge with a white ring and soft shadow
        p.color = 0x33000000; cv.drawCircle(x, cy + h * 0.04f, badge / 2, p)
        p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(x, cy, badge / 2, p)
        ContextCompat.getDrawable(ctx, kind.tracker)?.let { d ->
            val r = (badge / 2 * 0.86f).toInt()
            d.setBounds((x - r).toInt(), (cy - r).toInt(), (x + r).toInt(), (cy + r).toInt()); d.draw(cv)
        }
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
