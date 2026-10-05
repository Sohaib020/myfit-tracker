package com.myfit.tracker.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.BuildConfig
import com.myfit.tracker.R
import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.data.db.GlucoseTag
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Glucose
import com.myfit.tracker.ui.glucose.DiabetesType
import com.myfit.tracker.ui.glucose.GlucoseConfig
import com.myfit.tracker.ui.glucose.GlucoseReport
import com.myfit.tracker.ui.glucose.MedReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "Report for your doctor": a designed A4 PDF — branded header with the app logo, patient details, key numbers,
 * then blood sugar, blood pressure & heart rate, weight / activity / sleep and medicines, each as tidy tables and
 * charts. Every page has a footer; the last page says what MyFit is with a QR code to download it.
 *
 * App details (name, version, website, support) are read from [AppInfo] / BuildConfig, so they stay current in
 * every build without editing this file.
 */
object AppInfo {
    const val NAME = "MyFit Tracker"
    const val TAGLINE = "Private fitness & health logbook"
    const val SUPPORT = "myfitnesstrack.support@gmail.com"
    val site get() = com.myfit.tracker.update.AppUpdater.SITE
    val version get() = BuildConfig.VERSION_NAME
}

object DoctorReport {
    private const val W = 595f
    private const val H = 842f
    private const val M = 36f
    private val INK = Color.rgb(20, 30, 32)
    private val SOFT = Color.rgb(96, 110, 112)
    private val FAINT = Color.rgb(150, 160, 162)
    private val LINE = Color.rgb(226, 232, 232)
    private val ZEBRA = Color.rgb(244, 248, 247)
    private val MINT = Color.rgb(44, 201, 167)
    private val MINT_DARK = Color.rgb(16, 120, 100)
    private val MINT_TINT = Color.rgb(228, 248, 242)
    private val RED = Color.rgb(214, 64, 64)
    private val AMBER = Color.rgb(232, 150, 30)
    private val longFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val dtFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** App logo (adaptive-icon layers composited) as a bitmap. */
    private fun logo(ctx: Context, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.24f, px * 0.24f, Path.Direction.CW) }
        c.clipPath(clip)
        listOf(R.drawable.ic_launcher_bg, R.drawable.ic_launcher_fg).forEach { id ->
            runCatching { BitmapFactory.decodeResource(ctx.resources, id) }.getOrNull()?.let { b ->
                // adaptive layers are 108dp with the visible part in the middle 72dp
                val inset = b.width * (18f / 108f)
                c.drawBitmap(b, android.graphics.Rect(inset.toInt(), inset.toInt(), (b.width - inset).toInt(), (b.height - inset).toInt()), RectF(0f, 0f, px.toFloat(), px.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        return out
    }

    suspend fun build(container: AppContainer, days: Int, cfg: GlucoseConfig): File = withContext(Dispatchers.IO) {
        val ctx = container.app
        val zone = Clock.zone()
        val today = Clock.today()
        val first = today.minusDays(days.toLong() - 1)
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val profile = runCatching { container.profileRepo.profile.first() }.getOrNull()
        val glucose = runCatching { container.db.glucoseDao().between(from, to) }.getOrDefault(emptyList())
        val bp = runCatching { container.db.bloodPressureDao().observeSince(from).first() }.getOrDefault(emptyList()).filter { it.takenAt < to }
        val meds = runCatching { container.db.medicationDao().active() }.getOrDefault(emptyList())
        val medLogs = runCatching { container.db.medicationLogDao().between(Clock.dateKey(first), Clock.dateKey(today)) }.getOrDefault(emptyList())
        val weights = runCatching { container.logRepo.weightsAll().first() }.getOrDefault(emptyList())
            .filter { !LocalDate.parse(it.localDate).isBefore(first) && !LocalDate.parse(it.localDate).isAfter(today) }
        val hcDaily = runCatching { container.healthRepo.dailyRange(first, today).first() }.getOrDefault(emptyList())
        val hcSleep = runCatching { container.healthRepo.sleepRange(first, today).first() }.getOrDefault(emptyList())
        val mmol = cfg.mmol
        val u = Glucose.unitLabel(mmol)
        fun g(v: Double) = Glucose.format(v, mmol)

        val doc = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()
        lateinit var c: Canvas
        var y = 0f
        val logoBmp = logo(ctx, 128)
        val qr = com.myfit.tracker.social.Invite.qr(AppInfo.site, 360)

        fun footer(cv: Canvas, n: Int) {
            val p = paint(7.5f, FAINT)
            cv.drawLine(M, H - 34f, W - M, H - 34f, Paint().apply { color = LINE; strokeWidth = 0.8f })
            cv.drawBitmap(logoBmp, null, RectF(M, H - 28f, M + 14f, H - 14f), Paint(Paint.FILTER_BITMAP_FLAG))
            cv.drawText("${AppInfo.NAME} · health summary · not a diagnosis", M + 20f, H - 18f, p)
            val right = "Page $n"
            cv.drawText(right, W - M - p.measureText(right), H - 18f, p)
        }
        fun newPage() {
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pages.size + 1).create())
            pages += pg; c = pg.canvas; y = M
            if (pages.size > 1) {
                // slim running header
                c.drawBitmap(logoBmp, null, RectF(M, y, M + 18f, y + 18f), Paint(Paint.FILTER_BITMAP_FLAG))
                c.drawText(AppInfo.NAME, M + 24f, y + 13f, paint(10f, INK, true))
                val nm = profile?.name.orEmpty()
                if (nm.isNotBlank()) c.drawText(nm, W - M - paint(9f, SOFT).measureText(nm), y + 13f, paint(9f, SOFT))
                y += 30f
            }
            footer(c, pages.size)
        }
        fun room(h: Float) { if (y + h > H - 46f) { doc.finishPage(pages.last()); newPage() } }

        fun section(title: String, sub: String? = null) {
            room(46f)
            y += 10f
            c.drawRoundRect(RectF(M, y, M + 4f, y + 18f), 2f, 2f, Paint().apply { color = MINT })
            c.drawText(title, M + 12f, y + 14f, paint(14f, INK, true))
            y += 22f
            if (sub != null) { c.drawText(sub, M + 12f, y + 8f, paint(8.5f, SOFT)); y += 14f }
            y += 4f
        }
        fun tiles(items: List<Triple<String, String, String?>>) {
            room(64f)
            val gap = 8f; val w = (W - 2 * M - gap * (items.size - 1)) / items.size
            items.forEachIndexed { i, (label, value, note) ->
                val x = M + i * (w + gap)
                c.drawRoundRect(RectF(x, y, x + w, y + 56f), 10f, 10f, Paint().apply { color = MINT_TINT })
                c.drawText(label.uppercase(), x + 10f, y + 15f, paint(7f, MINT_DARK, true))
                c.drawText(value, x + 10f, y + 35f, paint(15f, INK, true))
                if (note != null) c.drawText(note, x + 10f, y + 48f, paint(7.5f, SOFT))
            }
            y += 66f
        }
        /** Simple zebra table; [widths] are fractions of the content width. */
        fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>, colors: List<Int?>? = null) {
            val cw = W - 2 * M
            fun row(cells: List<String>, header: Boolean, idx: Int) {
                room(18f)
                if (header) c.drawRect(M, y, W - M, y + 18f, Paint().apply { color = INK })
                else if (idx % 2 == 0) c.drawRect(M, y, W - M, y + 17f, Paint().apply { color = ZEBRA })
                var x = M + 8f
                cells.forEachIndexed { i, t ->
                    val p = if (header) paint(7.5f, Color.WHITE, true) else paint(8.5f, if (i == cells.lastIndex && colors != null) (colors.getOrNull(idx) ?: INK) else INK)
                    var s = t; val max = cw * widths[i] - 10f
                    while (p.measureText(s) > max && s.length > 3) s = s.dropLast(2) + "…"
                    c.drawText(s, x, y + 12f, p)
                    x += cw * widths[i]
                }
                y += if (header) 18f else 17f
            }
            row(headers, true, -1)
            rows.forEachIndexed { i, r -> row(r, false, i) }
            y += 6f
        }
        fun note(text: String) {
            val p = paint(8f, SOFT)
            // wrap
            val words = text.split(' '); var line = ""
            words.forEach { w0 ->
                val t = if (line.isEmpty()) w0 else "$line $w0"
                if (p.measureText(t) > W - 2 * M) { room(12f); c.drawText(line, M, y + 9f, p); y += 12f; line = w0 } else line = t
            }
            if (line.isNotEmpty()) { room(12f); c.drawText(line, M, y + 9f, p); y += 12f }
        }

        // ================= page 1: header
        newPage()
        val band = RectF(0f, 0f, W, 118f)
        c.drawRect(band, Paint().apply { shader = LinearGradient(0f, 0f, W, 118f, Color.rgb(12, 20, 20), Color.rgb(16, 64, 56), Shader.TileMode.CLAMP) })
        c.drawBitmap(logoBmp, null, RectF(M, 26f, M + 52f, 78f), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawText(AppInfo.NAME, M + 64f, 46f, paint(18f, Color.WHITE, true))
        c.drawText("Health summary for your doctor", M + 64f, 66f, paint(11f, Color.rgb(180, 240, 225)))
        c.drawText("${first.format(longFmt)} – ${today.format(longFmt)}  ·  $days days", M + 64f, 84f, paint(9f, Color.rgb(200, 220, 216)))
        val gen = "Generated ${Instant.now().atZone(zone).format(dtFmt)}"
        c.drawText(gen, W - M - paint(8f, Color.rgb(200, 220, 216)).measureText(gen), 104f, paint(8f, Color.rgb(200, 220, 216)))
        y = 136f
        // patient card
        val age = profile?.let { it.age + ChronoUnit.YEARS.between(LocalDate.parse(it.ageRecordedOn), today).toInt() }
        c.drawRoundRect(RectF(M, y, W - M, y + 56f), 10f, 10f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
        c.drawRoundRect(RectF(M, y, W - M, y + 56f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; style = Paint.Style.STROKE; strokeWidth = 1f })
        val pc = listOf("Patient" to (profile?.name?.ifBlank { null } ?: "—"), "Age" to (age?.toString() ?: "—"),
            "Sex" to (profile?.sex?.toString()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—"), "Height" to (profile?.heightCm?.let { "${it.toInt()} cm" } ?: "—"),
            "Diabetes" to DiabetesType.label(cfg.type))
        val colW = (W - 2 * M) / pc.size
        pc.forEachIndexed { i, (k, v) ->
            val x = M + 12f + i * colW
            c.drawText(k.uppercase(), x, y + 20f, paint(7f, FAINT, true))
            var s = v; val p = paint(11f, INK, true); while (p.measureText(s) > colW - 16f && s.length > 3) s = s.dropLast(2) + "…"
            c.drawText(s, x, y + 38f, p)
        }
        y += 70f

        // key numbers
        val avgG = glucose.takeIf { it.isNotEmpty() }?.map { it.mgdl }?.average()
        val manual = glucose.filter { it.tag != GlucoseTag.CGM }
        val inRange = if (manual.isNotEmpty()) manual.count { Glucose.inRange(it.mgdl, cfg.low, cfg.high) } * 100 / manual.size else null
        val avgSys = bp.takeIf { it.isNotEmpty() }?.map { it.systolic }?.average()
        val avgDia = bp.takeIf { it.isNotEmpty() }?.map { it.diastolic }?.average()
        val rhr = hcDaily.mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average()
        tiles(listOf(
            Triple("Avg glucose", avgG?.let { "${g(it)} $u" } ?: "—", glucose.size.takeIf { it > 0 }?.let { "$it readings" }),
            Triple("In target", inRange?.let { "$it%" } ?: "—", "${g(cfg.low.toDouble())}–${g(cfg.high.toDouble())} $u"),
            Triple("Avg BP", if (avgSys != null && avgDia != null) "${avgSys.toInt()}/${avgDia.toInt()}" else "—", bp.size.takeIf { it > 0 }?.let { "$it readings · mmHg" }),
            Triple("Resting HR", rhr?.let { "${it.toInt()} bpm" } ?: "—", if (rhr != null) "from watch" else null),
        ))

        // ================= blood sugar
        section("Blood sugar", "Values in $u · target range ${g(cfg.low.toDouble())}–${g(cfg.high.toDouble())}")
        if (glucose.isEmpty()) note("No blood sugar readings in this period.")
        else {
            val lows = glucose.count { it.mgdl < Glucose.LOW }; val vLows = glucose.count { it.mgdl < Glucose.VERY_LOW }
            val highs = glucose.count { it.mgdl > cfg.high }
            tiles(listOf(
                Triple("Lowest", "${g(glucose.minOf { it.mgdl })}", null), Triple("Highest", "${g(glucose.maxOf { it.mgdl })}", null),
                Triple("Lows", "$lows", "very low: $vLows"), Triple("Above target", "$highs", null),
            ))
            if (glucose.size >= 2) {
                room(160f)
                drawGlucoseChart(c, M, y, W - 2 * M, 130f, glucose, from, to, cfg)
                y += 150f
            }
            val byTag = manual.groupBy { it.tag }
            val tagRows = Glucose.manualTags.mapNotNull { t -> byTag[t]?.let { l -> listOf(Glucose.tagLabel(t), "${l.size}", g(l.map { it.mgdl }.average()), "${g(l.minOf { it.mgdl })}–${g(l.maxOf { it.mgdl })}") } }
            if (tagRows.isNotEmpty()) table(listOf("When", "Readings", "Average", "Range"), listOf(0.4f, 0.18f, 0.2f, 0.22f), tagRows)
            val check = Glucose.cgmCheck(glucose, Clock.dateKey(today), cfg.low, cfg.high)
            val tir = check.tir
            if (tir != null && check.gmi != null) {
                table(listOf("Sensor time in range", "Very low", "Low", "In range", "High", "Very high"), listOf(0.3f, 0.14f, 0.14f, 0.14f, 0.14f, 0.14f),
                    listOf(listOf("${check.daysWithData} days", "%.0f%%".format(tir.veryLow), "%.0f%%".format(tir.low), "%.0f%%".format(tir.inRange), "%.0f%%".format(tir.high), "%.0f%%".format(tir.veryHigh))))
                note("Estimated A1c (GMI): %.1f%% — an estimate from sensor data that may differ from a laboratory A1c.".format(check.gmi))
            }
            val notable = glucose.filter { it.tag != GlucoseTag.CGM && (it.mgdl < Glucose.LOW || it.mgdl >= Glucose.VERY_HIGH) }.take(18)
            if (notable.isNotEmpty()) {
                room(40f); c.drawText("Lows and very highs", M, y + 10f, paint(10f, INK, true)); y += 16f
                table(listOf("Date & time", "Value", "When", "Note"), listOf(0.24f, 0.14f, 0.24f, 0.38f),
                    notable.map { r -> listOf(Instant.ofEpochMilli(r.takenAt).atZone(zone).format(dtFmt), g(r.mgdl), Glucose.tagLabel(r.tag), r.notes.take(60)) },
                    notable.map { if (it.mgdl < Glucose.LOW) RED else AMBER })
            }
        }

        // ================= blood pressure & heart rate
        section("Blood pressure & heart rate", "mmHg · pulse in beats per minute")
        if (bp.isEmpty() && rhr == null) note("No blood pressure or heart-rate data in this period.")
        if (bp.isNotEmpty()) {
            fun cat(s: Int, d: Int) = when { s >= 140 || d >= 90 -> "High"; s >= 130 || d >= 80 -> "Raised"; s < 90 || d < 60 -> "Low"; else -> "Normal" }
            val shown = bp.sortedByDescending { it.takenAt }.take(20)
            table(listOf("Date & time", "Systolic", "Diastolic", "Pulse", "Reading"), listOf(0.3f, 0.16f, 0.16f, 0.14f, 0.24f),
                shown.map { b -> listOf(Instant.ofEpochMilli(b.takenAt).atZone(zone).format(dtFmt), "${b.systolic}", "${b.diastolic}", b.pulse?.toString() ?: "—", cat(b.systolic, b.diastolic)) },
                shown.map { when (cat(it.systolic, it.diastolic)) { "High" -> RED; "Raised", "Low" -> AMBER; else -> MINT_DARK } })
            if (bp.size > shown.size) note("Showing the latest ${shown.size} of ${bp.size} readings. Average ${avgSys?.toInt()}/${avgDia?.toInt()} mmHg.")
        }
        if (rhr != null) note("Resting heart rate from the watch averaged ${rhr.toInt()} bpm over ${hcDaily.count { it.restingHr != null }} days.")

        // ================= weight, activity & sleep (weekly)
        section("Weight, activity & sleep", "Weekly averages")
        val weeks = generateSequence(first) { it.plusDays(7) }.takeWhile { !it.isAfter(today) }.toList()
        val wRows = weeks.map { ws ->
            val we = minOf(ws.plusDays(6), today)
            fun inW(d: String) = LocalDate.parse(d).let { !it.isBefore(ws) && !it.isAfter(we) }
            val wt = weights.filter { inW(it.localDate) }.map { it.weightKg }.takeIf { it.isNotEmpty() }?.average()
            val st = hcDaily.filter { inW(it.localDate) && it.steps != null }.map { it.steps!! }.takeIf { it.isNotEmpty() }?.average()
            val sl = hcSleep.filter { inW(it.localDate) }.groupBy { it.localDate }.values.map { l -> l.maxOf { (it.endAt - it.startAt) / 60_000.0 } }.takeIf { it.isNotEmpty() }?.average()
            listOf("${ws.format(dayFmt)} – ${we.format(dayFmt)}", wt?.let { "%.1f kg".format(it) } ?: "—", st?.let { "%,d".format(it.toLong()) } ?: "—", sl?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" } ?: "—")
        }
        if (wRows.all { it.drop(1).all { v -> v == "—" } }) note("No weight, step or sleep data in this period.")
        else table(listOf("Week", "Avg weight", "Avg steps / day", "Avg sleep"), listOf(0.4f, 0.2f, 0.2f, 0.2f), wRows)

        // ================= medicines
        section("Medicines", "As recorded by the patient — not a prescription")
        if (meds.isEmpty() && medLogs.isEmpty()) note("No medicines recorded.")
        else {
            table(listOf("Medicine", "Type", "Dose", "Scheduled", "Doses logged", "Taken"), listOf(0.26f, 0.18f, 0.14f, 0.14f, 0.14f, 0.14f),
                meds.map { m ->
                    val per = MedReminders.parseTimes(m.times).size
                    val n = medLogs.count { it.medicationId == m.id }
                    val expected = per * days
                    listOf(m.name, GlucoseReport.kindLabel(m.kind).replaceFirstChar { it.uppercase() }, m.dose?.let { "%s %s".format(if (it % 1.0 == 0.0) it.toLong().toString() else "%.1f".format(it), m.unit) } ?: m.unit,
                        if (per > 0) "${per}× day" else "As needed", "$n", if (expected > 0) "${(n * 100 / expected).coerceAtMost(100)}%" else "—")
                })
        }

        // ================= about
        room(170f)
        y += 14f
        c.drawRoundRect(RectF(M, y, W - M, y + 140f), 14f, 14f, Paint().apply { color = MINT_TINT })
        c.drawBitmap(qr, null, RectF(W - M - 124f, y + 12f, W - M - 8f, y + 128f), Paint())
        c.drawBitmap(logoBmp, null, RectF(M + 16f, y + 16f, M + 50f, y + 50f), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawText(AppInfo.NAME, M + 60f, y + 32f, paint(13f, INK, true))
        c.drawText(AppInfo.TAGLINE, M + 60f, y + 46f, paint(9f, SOFT))
        val about = listOf(
            "This summary was created by the patient in ${AppInfo.NAME} (version ${AppInfo.version}).",
            "Data comes from readings they entered and from connected devices via Health Connect.",
            "It is a record, not a diagnosis, and gives no medical or dosing advice.",
            "Download the app: ${AppInfo.site.removePrefix("https://")}",
            "Support: ${AppInfo.SUPPORT}",
        )
        about.forEachIndexed { i, t -> c.drawText(t, M + 16f, y + 70f + i * 13f, paint(8.5f, if (i >= 3) MINT_DARK else INK)) }
        y += 150f

        doc.finishPage(pages.last())
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "MyFit_health_summary_${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Health summary from ${AppInfo.NAME}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    private fun drawGlucoseChart(canvas: Canvas, x: Float, top: Float, w: Float, h: Float, rs: List<GlucoseReading>, from: Long, to: Long, cfg: GlucoseConfig) {
        val mmol = cfg.mmol
        val left = x + 30f; val cw = w - 30f
        val maxV = maxOf(300.0, rs.maxOf { it.mgdl } + 10); val minV = 40.0
        fun yFor(v: Double) = (top + h - ((v.coerceIn(minV, maxV) - minV) / (maxV - minV)) * h).toFloat()
        fun xFor(t: Long) = (left + (t - from).toDouble() / (to - from).coerceAtLeast(1L) * cw).toFloat()
        canvas.drawRoundRect(RectF(left, top, left + cw, top + h), 8f, 8f, Paint().apply { color = Color.rgb(250, 252, 252) })
        canvas.drawRect(left, yFor(cfg.high.toDouble()), left + cw, yFor(cfg.low.toDouble()), Paint().apply { color = Color.argb(46, 44, 201, 167) })
        val grid = Paint().apply { color = LINE; strokeWidth = 0.7f }
        val lab = paint(7f, FAINT)
        listOf(Glucose.LOW, cfg.high.toDouble(), Glucose.VERY_HIGH).forEach { v -> canvas.drawLine(left, yFor(v), left + cw, yFor(v), grid); canvas.drawText(Glucose.format(v, mmol), x, yFor(v) + 3f, lab) }
        val pts = Glucose.downsample(rs.map { it.takenAt to it.mgdl }, 400)
        if (pts.size > 1) {
            val path = Path(); pts.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(xFor(t), yFor(v)) else path.lineTo(xFor(t), yFor(v)) }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MINT_DARK; style = Paint.Style.STROKE; strokeWidth = 1.4f })
        }
        if (pts.size <= 150) pts.forEach { (t, v) -> canvas.drawCircle(xFor(t), yFor(v), 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (v < Glucose.LOW) RED else if (v > cfg.high) AMBER else MINT_DARK }) }
        canvas.drawText("Shaded band = target range · red = low · amber = above target", left, top + h + 12f, lab)
    }
}
