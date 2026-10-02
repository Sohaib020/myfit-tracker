package com.myfit.tracker.ui.glucose

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.data.db.GlucoseTag
import com.myfit.tracker.data.db.MedKind
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Glucose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds a 1–2 page A4 summary for a doctor or nurse. Runs off the main thread. */
object GlucoseReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val dtFmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.US)
    private val longFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    suspend fun build(container: AppContainer, days: Int, cfg: GlucoseConfig): File = withContext(Dispatchers.IO) {
        val zone = Clock.zone()
        val today = Clock.today()
        val first = today.minusDays(days.toLong() - 1)
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val readings = container.db.glucoseDao().between(from, to)
        val name = runCatching { container.profileRepo.profile.first()?.name }.getOrNull().orEmpty()
        val meds = runCatching { container.db.medicationDao().active() }.getOrDefault(emptyList())
        val logs = runCatching { container.db.medicationLogDao().between(Clock.dateKey(first), Clock.dateKey(today)) }.getOrDefault(emptyList())
        val carbs = runCatching { container.db.glucoseDao().dailyCarbsOnce(Clock.dateKey(first), Clock.dateKey(today)) }.getOrDefault(emptyList())
            .associate { it.date to it.carbs }
        val mmol = cfg.mmol
        val u = Glucose.unitLabel(mmol)
        fun f(v: Double) = Glucose.format(v, mmol)

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f

        val title = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val head = Paint().apply { color = Color.rgb(30, 30, 30); textSize = 12.5f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { color = Color.rgb(40, 40, 40); textSize = 10f; isAntiAlias = true }
        val small = Paint().apply { color = Color.rgb(100, 100, 100); textSize = 8.5f; isAntiAlias = true }

        fun newPage(): Boolean {
            if (pageNo >= 2) return false
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            page = p; c = p.canvas; y = M
            c?.drawText("MyFit Tracker — blood sugar summary · page $pageNo", M, H - 20f, small)
            return true
        }
        /** Makes room for [h] points; returns false when we're out of pages. */
        fun room(h: Float): Boolean = if (y + h > H - 40f) newPage() else true
        fun line(text: String, p: Paint = body, gap: Float = 4f) {
            if (!room(p.textSize + gap)) return
            y += p.textSize
            c?.drawText(text, M, y, p)
            y += gap
        }
        fun section(text: String) { y += 6f; line(text, head, 5f) }

        newPage()
        line("Blood sugar summary", title, 6f)
        line(if (name.isNotBlank()) "Name: $name" else "Name: —")
        line("Period: ${first.format(longFmt)} – ${today.format(longFmt)} ($days days)")
        line("Diabetes type (self-reported): ${DiabetesType.label(cfg.type)}    Target range: ${f(cfg.low.toDouble())}–${f(cfg.high.toDouble())} $u")
        line("Generated ${Instant.now().atZone(zone).format(dtFmt)} · values in $u", small)

        // ---- summary
        section("Readings")
        val manual = readings.filter { it.tag != GlucoseTag.CGM }
        val sensor = readings.filter { it.tag == GlucoseTag.CGM }
        if (readings.isEmpty()) line("No readings recorded in this period.")
        else {
            line("Total readings: ${readings.size} (finger-stick/manual ${manual.size}, sensor ${sensor.size})")
            line("Average: ${f(readings.map { it.mgdl }.average())} $u    Lowest: ${f(readings.minOf { it.mgdl })}    Highest: ${f(readings.maxOf { it.mgdl })}")
            if (manual.isNotEmpty()) {
                val inR = manual.count { Glucose.inRange(it.mgdl, cfg.low, cfg.high) } * 100 / manual.size
                line("Finger-stick readings in target range: $inR%")
            }
            val byTag = manual.groupBy { it.tag }
            Glucose.manualTags.forEach { t ->
                val l = byTag[t] ?: return@forEach
                line("  ${Glucose.tagLabel(t)}: average ${f(l.map { it.mgdl }.average())} $u  (n=${l.size}, range ${f(l.minOf { it.mgdl })}–${f(l.maxOf { it.mgdl })})")
            }
            val lows = readings.count { it.mgdl < Glucose.LOW }
            val vLows = readings.count { it.mgdl < Glucose.VERY_LOW }
            val highs = readings.count { it.mgdl > cfg.high }
            line("Lows (< ${f(Glucose.LOW)}): $lows, of which very low (< ${f(Glucose.VERY_LOW)}): $vLows    Above target: $highs")
        }

        // ---- sensor TIR
        val check = Glucose.cgmCheck(readings, Clock.dateKey(today), cfg.low, cfg.high)
        section("Sensor (CGM) data")
        val tir = check.tir
        if (tir != null && check.gmi != null) {
            line(String.format(Locale.US, "Time in range: very low %.0f%% · low %.0f%% · in range %.0f%% · high %.0f%% · very high %.0f%%",
                tir.veryLow, tir.low, tir.inRange, tir.high, tir.veryHigh))
            line(String.format(Locale.US, "Sensor mean %s %s · Estimated A1c (GMI) %.1f%% — may differ from lab A1c · %d days, %.0f%% of expected readings",
                f(tir.mean), u, check.gmi, check.daysWithData, check.coverage * 100))
        } else if (sensor.isNotEmpty()) line("Not enough sensor data for time in range (needs 14 days). Days with sensor data: ${check.daysWithData}.")
        else line("No sensor data in this period.")

        // ---- chart
        if (readings.size >= 2 && room(170f)) {
            section("Readings over time")
            drawChart(c, M, y + 4f, W - 2 * M, 130f, readings, from, to, cfg, small)
            y += 150f
        }

        // ---- lows/highs list
        val notable = readings.filter { it.tag != GlucoseTag.CGM && (it.mgdl < Glucose.LOW || it.mgdl >= Glucose.VERY_HIGH) }
        val sensorLows = sensor.filter { it.mgdl < Glucose.LOW }.groupBy { it.localDate }
        if (notable.isNotEmpty() || sensorLows.isNotEmpty()) {
            section("Lows and very highs")
            notable.take(30).forEach { r ->
                val t = Instant.ofEpochMilli(r.takenAt).atZone(zone).format(dtFmt)
                line("  $t   ${f(r.mgdl)} $u   ${Glucose.band(r.mgdl, cfg.high).label} · ${Glucose.tagLabel(r.tag)}${if (r.notes.isNotBlank()) " · " + r.notes.take(50) else ""}")
            }
            if (notable.size > 30) line("  …and ${notable.size - 30} more", small)
            sensorLows.entries.sortedBy { it.key }.take(14).forEach { (d, l) ->
                line("  $d   sensor below ${f(Glucose.LOW)}: ${l.size} readings, lowest ${f(l.minOf { it.mgdl })} $u")
            }
        }

        // ---- medicines
        section("Medicines (record only)")
        if (meds.isEmpty() && logs.isEmpty()) line("No medicines recorded.")
        meds.forEach { m ->
            val n = logs.count { it.medicationId == m.id }
            val dose = m.dose?.let { trim(it) + " " + m.unit } ?: m.unit
            val sched = MedReminders.parseTimes(m.times).size
            line("  ${m.name} — ${kindLabel(m.kind)}, $dose${if (sched > 0) ", scheduled ${sched}x/day" else ""} · doses logged: $n")
        }
        val orphan = logs.filter { l -> meds.none { it.id == l.medicationId } }.groupBy { it.name }
        orphan.forEach { (nm, l) -> line("  $nm — doses logged: ${l.size}") }

        // ---- carbs per day
        section("Daily carbs (food diary) and average glucose")
        var d = first
        val byDay = readings.groupBy { it.localDate }
        while (!d.isAfter(today)) {
            val k = Clock.dateKey(d)
            val cb = carbs[k]; val rs = byDay[k]
            if (cb != null || rs != null) {
                line("  ${d.format(dayFmt)}:  carbs ${cb?.let { "${it.toInt()} g" } ?: "—"}   ·   glucose avg ${rs?.let { f(it.map { r -> r.mgdl }.average()) + " " + u } ?: "—"}${rs?.let { "  (n=${it.size})" } ?: ""}")
            }
            d = d.plusDays(1)
        }

        y += 8f
        line("This summary was made from data entered or imported by the user in MyFit Tracker. It is not a diagnosis", small, 2f)
        line("and does not give medical or dosing advice. GMI is an estimate and may differ from a laboratory A1c.", small, 2f)

        page?.let { doc.finishPage(it) }
        val dir = File(container.app.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "glucose_" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Blood sugar summary")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    private fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    fun kindLabel(k: String): String = when (k) {
        MedKind.TABLET -> "tablet"; MedKind.INSULIN_RAPID -> "rapid insulin"; MedKind.INSULIN_LONG -> "long-acting insulin"; else -> "other"
    }

    private fun drawChart(c: Canvas?, x: Float, top: Float, w: Float, h: Float, rs: List<GlucoseReading>, from: Long, to: Long, cfg: GlucoseConfig, label: Paint) {
        val canvas = c ?: return
        val mmol = cfg.mmol
        val left = x + 34f
        val cw = w - 34f
        val maxV = maxOf(300.0, rs.maxOf { it.mgdl } + 10)
        val minV = 40.0
        fun yFor(v: Double) = (top + h - ((v.coerceIn(minV, maxV) - minV) / (maxV - minV)) * h).toFloat()
        fun xFor(t: Long) = (left + (t - from).toDouble() / (to - from).coerceAtLeast(1L) * cw).toFloat()
        val frame = Paint().apply { color = Color.rgb(200, 200, 200); style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val band = Paint().apply { color = Color.argb(40, 47, 211, 122); style = Paint.Style.FILL }
        canvas.drawRect(left, yFor(cfg.high.toDouble()), left + cw, yFor(cfg.low.toDouble()), band)
        canvas.drawRect(left, top, left + cw, top + h, frame)
        listOf(Glucose.LOW, cfg.high.toDouble(), Glucose.VERY_HIGH).forEach { v ->
            canvas.drawLine(left, yFor(v), left + cw, yFor(v), frame)
            canvas.drawText(Glucose.format(v, mmol), x, yFor(v) + 3f, label)
        }
        val pts = Glucose.downsample(rs.map { it.takenAt to it.mgdl }, 400)
        val line = Paint().apply { color = Color.rgb(40, 110, 220); style = Paint.Style.STROKE; strokeWidth = 1.2f; isAntiAlias = true }
        val dot = Paint().apply { color = Color.rgb(40, 110, 220); style = Paint.Style.FILL; isAntiAlias = true }
        val lowDot = Paint().apply { color = Color.rgb(220, 50, 50); style = Paint.Style.FILL; isAntiAlias = true }
        if (pts.size > 1) {
            val path = Path()
            pts.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(xFor(t), yFor(v)) else path.lineTo(xFor(t), yFor(v)) }
            canvas.drawPath(path, line)
        }
        if (pts.size <= 120) pts.forEach { (t, v) -> canvas.drawCircle(xFor(t), yFor(v), 2f, if (v < Glucose.LOW) lowDot else dot) }
        canvas.drawText("Green band = target range. Lines at ${Glucose.format(Glucose.LOW, mmol)}, ${Glucose.format(cfg.high.toDouble(), mmol)} and ${Glucose.format(Glucose.VERY_HIGH, mmol)} ${Glucose.unitLabel(mmol)}.", left, top + h + 12f, label)
    }
}
