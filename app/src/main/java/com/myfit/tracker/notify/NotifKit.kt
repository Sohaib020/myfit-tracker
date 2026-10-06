package com.myfit.tracker.notify

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.myfit.tracker.R

/** Which animated icon + accent a notification card uses. */
enum class NKind(val color: Int) {
    RUN(0xFF22C27A.toInt()), LIFT(0xFFFF7A2F.toInt()), REST(0xFF3F7BFF.toInt()), FLAME(0xFFFF5A36.toInt()),
    DROP(0xFF2F9BFF.toInt()), PILL(0xFF9B6BFF.toInt()), BELL(0xFFFFA51F.toInt()), MOON(0xFF1F8F6E.toInt()),
    TROPHY(0xFFF2A900.toInt()), HEART(0xFFFF4F86.toInt()), SUGAR(0xFFE2453C.toInt()), MEAL(0xFFFF7A59.toInt());

    /** The ProgressBar (playing this kind's frame animation) inside the card layouts. */
    val animView: Int get() = when (this) {
        RUN -> R.id.n_anim_run; LIFT -> R.id.n_anim_lift; REST -> R.id.n_anim_rest; FLAME -> R.id.n_anim_flame
        DROP -> R.id.n_anim_drop; PILL -> R.id.n_anim_pill; BELL -> R.id.n_anim_bell; MOON -> R.id.n_anim_moon
        TROPHY -> R.id.n_anim_trophy; HEART -> R.id.n_anim_heart; SUGAR -> R.id.n_anim_sugar; MEAL -> R.id.n_anim_meal
    }

    /** Small badge that rides the Android 16 progress bar. */
    val tracker: Int get() = when (this) {
        RUN -> R.drawable.nt_run; LIFT -> R.drawable.nt_lift; REST -> R.drawable.nt_rest; FLAME -> R.drawable.nt_flame
        DROP -> R.drawable.nt_drop; else -> R.drawable.nt_run
    }
}

/**
 * Everything a card can show. Anything left null is hidden.
 * [chrono] = the time a live clock counts from (or down to, with [countDown]); [progress] 0..1; [indeterminate] = a
 * moving bar for open-ended activities; [stats] = up to three value/label pairs under the bar.
 */
data class NCard(
    val kind: NKind, val title: String, val text: String,
    val chrono: Long? = null, val countDown: Boolean = false,
    val value: String? = null, val chip: String? = null,
    val progress: Float? = null, val indeterminate: Boolean = false, val progressLabel: String? = null,
    val stats: List<Pair<String, String>> = emptyList(), val image: Bitmap? = null,
)

/** Builds the animated card notifications (custom views in the system's own notification frame). */
object NotifKit {
    private val allAnims = NKind.entries.map { it.animView }

    private fun chronoBase(epochMs: Long): Long = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - epochMs)

    fun small(ctx: Context, c: NCard): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_card_small).apply {
        allAnims.forEach { setViewVisibility(it, if (it == c.kind.animView) View.VISIBLE else View.GONE) }
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        if (c.chrono != null) {
            setViewVisibility(R.id.n_chrono_small, View.VISIBLE)
            setChronometer(R.id.n_chrono_small, chronoBase(c.chrono), null, true)
            if (Build.VERSION.SDK_INT >= 24) setChronometerCountDown(R.id.n_chrono_small, c.countDown)
            setTextColor(R.id.n_chrono_small, c.kind.color)
        } else if (c.value != null) {
            setViewVisibility(R.id.n_value_small, View.VISIBLE)
            setTextViewText(R.id.n_value_small, c.value)
            setTextColor(R.id.n_value_small, c.kind.color)
        }
        if (c.progress != null) {
            setViewVisibility(R.id.n_bar_small, View.VISIBLE)
            setProgressBar(R.id.n_bar_small, 1000, (c.progress.coerceIn(0f, 1f) * 1000).toInt(), false)
            tint(this, R.id.n_bar_small, c.kind.color)
        }
    }

    fun big(ctx: Context, c: NCard): RemoteViews = RemoteViews(ctx.packageName, R.layout.notif_card_big).apply {
        allAnims.forEach { setViewVisibility(it, if (it == c.kind.animView) View.VISIBLE else View.GONE) }
        setTextViewText(R.id.n_title, c.title)
        setTextViewText(R.id.n_text, c.text)
        if (c.image != null) { setViewVisibility(R.id.n_image, View.VISIBLE); setImageViewBitmap(R.id.n_image, c.image) }
        if (c.chrono != null) {
            setViewVisibility(R.id.n_chrono, View.VISIBLE)
            setChronometer(R.id.n_chrono, chronoBase(c.chrono), null, true)
            if (Build.VERSION.SDK_INT >= 24) setChronometerCountDown(R.id.n_chrono, c.countDown)
            setTextColor(R.id.n_chrono, c.kind.color)
        } else if (c.value != null) {
            setViewVisibility(R.id.n_value, View.VISIBLE)
            setTextViewText(R.id.n_value, c.value)
            setTextColor(R.id.n_value, c.kind.color)
        }
        if (c.chip != null) { setViewVisibility(R.id.n_chip, View.VISIBLE); setTextViewText(R.id.n_chip, c.chip) }
        when {
            c.progress != null -> {
                setViewVisibility(R.id.n_bar, View.VISIBLE)
                setProgressBar(R.id.n_bar, 1000, (c.progress.coerceIn(0f, 1f) * 1000).toInt(), false)
                tint(this, R.id.n_bar, c.kind.color)
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

    private fun tint(rv: RemoteViews, id: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= 31) rv.setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(color))
    }

    /** Puts the card on a compat builder: collapsed + expanded custom views inside the system frame. */
    fun apply(ctx: Context, b: NotificationCompat.Builder, c: NCard): NotificationCompat.Builder = b
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(small(ctx, c))
        .setCustomBigContentView(big(ctx, c))
        .setColor(c.kind.color)
        .setContentTitle(c.title).setContentText(c.text)     // used by watches, the lock screen and accessibility

    /** Daily summary rings (steps / water / calories …) drawn into a bitmap for the card's image slot. */
    fun rings(values: List<Pair<Float, Int>>, sizePx: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        val stroke = sizePx * 0.09f
        values.take(3).forEachIndexed { i, (frac, color) ->
            val inset = stroke / 2 + i * (stroke * 1.25f)
            val r = android.graphics.RectF(inset, inset, sizePx - inset, sizePx - inset)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; strokeCap = android.graphics.Paint.Cap.ROUND }
            p.color = (color and 0x00FFFFFF) or 0x33000000; cv.drawArc(r, 0f, 360f, false, p)
            p.color = color; cv.drawArc(r, -90f, 360f * frac.coerceIn(0.02f, 1f), false, p)
        }
        return bmp
    }
}
