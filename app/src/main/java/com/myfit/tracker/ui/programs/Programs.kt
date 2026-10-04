package com.myfit.tracker.ui.programs

import android.content.Context
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.ui.arena.Mascot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/* Programs: multi-week plans bundled in assets/programs.json (built by tools/programs/gen.py).
 * Following one turns each training day into a normal template; before every session the template's targets
 * are rewritten for the current week's phase, so Gym Mode, history and PRs all work unchanged. */

data class PItem(val k: String, val sets: Int, val reps: String, val rest: Int, val main: Boolean)
data class PDay(val name: String, val focus: String, val items: List<PItem>)
data class Phase(val from: Int, val to: Int, val label: String, val note: String, val sets: Int, val reps: String?, val rest: Double)

data class Program(
    val id: String, val name: String, val tag: String, val desc: String, val level: String, val gender: String, val equip: String,
    val goals: List<String>, val dpw: Int, val weeks: Int, val mins: Int, val mascot: String, val phases: List<Phase>, val days: List<PDay>,
) {
    fun phase(week: Int): Phase = phases.firstOrNull { week in it.from..it.to } ?: phases.last()
    val coach: Mascot get() = Mascot.entries.firstOrNull { it.id == mascot } ?: Mascot.PIP
    val keys: List<String> get() = days.flatMap { d -> d.items.map { it.k } }.distinct()
    val sessions: Int get() = dpw * weeks
}

object ProgramLib {
    @Volatile private var cache: List<Program>? = null

    fun all(c: Context): List<Program> = cache ?: runCatching {
        val a = JSONArray(c.assets.open("programs.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { parse(a.getJSONObject(it)) }
    }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) cache = it }

    fun byId(c: Context, id: String?): Program? = id?.let { i -> all(c).firstOrNull { it.id == i } }

    private fun strs(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
    private fun parse(o: JSONObject): Program {
        val ph = o.getJSONArray("phases").let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i); val w = p.getJSONArray("w")
                Phase(w.getInt(0), w.getInt(1), p.getString("label"), p.optString("note"), p.optInt("sets", 0),
                    p.optString("reps").takeIf { it.isNotEmpty() }, p.optDouble("rest", 1.0))
            }
        }
        val days = o.getJSONArray("days").let { a ->
            (0 until a.length()).map { i ->
                val d = a.getJSONObject(i); val it = d.getJSONArray("items")
                PDay(d.getString("name"), d.optString("focus"), (0 until it.length()).map { j ->
                    val x = it.getJSONObject(j)
                    PItem(x.getString("k"), x.getInt("s"), x.getString("r"), x.getInt("rest"), x.optInt("m") == 1)
                })
            }
        }
        return Program(o.getString("id"), o.getString("name"), o.getString("tag"), o.getString("desc"), o.getString("level"),
            o.getString("gender"), o.getString("equip"), strs(o.optJSONArray("goals")), o.getInt("dpw"), o.getInt("weeks"),
            o.getInt("mins"), o.getString("mascot"), ph, days)
    }
}

/** One prescription, resolved for a phase. */
data class Target(val sets: Int, val lo: Int?, val hi: Int?, val durSec: Int?, val rest: Int) {
    val text: String get() = "$sets × " + when {
        durSec != null -> if (durSec >= 120 && durSec % 60 == 0) "${durSec / 60} min" else "$durSec s"
        lo == null -> "max reps"
        hi != null && hi != lo -> "$lo–$hi"
        else -> "$lo"
    }
}

fun PItem.target(ph: Phase?): Target {
    val r = if (main && ph?.reps != null) ph.reps else reps
    val s = (sets + if (sets > 1) (ph?.sets ?: 0) else 0).coerceAtLeast(1)
    val rest = (rest * (ph?.rest ?: 1.0)).toInt()
    return when {
        r == "max" -> Target(s, null, null, null, rest)
        r.endsWith("s") -> Target(s, null, null, r.dropLast(1).toIntOrNull(), rest)
        r.endsWith("m") -> Target(s, null, null, (r.dropLast(1).toIntOrNull() ?: 0) * 60, rest)
        '-' in r -> r.split('-').let { Target(s, it[0].toIntOrNull(), it[1].toIntOrNull(), null, rest) }
        else -> Target(s, r.toIntOrNull(), r.toIntOrNull(), null, rest)
    }
}

/** The program being followed: which templates belong to it and when it started. */
data class Follow(val id: String, val start: String, val tpl: List<Long>)

/** Where the follower is: session index (0-based), week and day. */
data class Position(val done: Int, val week: Int, val day: Int, val finished: Boolean)

object ProgramEngine {
    private const val P = "programs"
    private val _follow = MutableStateFlow<Follow?>(null)
    val follow: StateFlow<Follow?> = _follow

    fun init(c: Context) {
        val sp = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        val id = sp.getString("id", null) ?: return
        _follow.value = Follow(id, sp.getString("start", null) ?: LocalDate.now().toString(),
            sp.getString("tpl", "")!!.split(',').mapNotNull { it.toLongOrNull() })
    }

    private fun save(c: Context, f: Follow?) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().apply {
            if (f == null) clear() else putString("id", f.id).putString("start", f.start).putString("tpl", f.tpl.joinToString(","))
        }.apply()
        _follow.value = f
    }

    fun position(p: Program, done: Int) = Position(done, done / p.dpw + 1, done % p.dpw, done >= p.sessions)

    /** Completed sessions of the followed program (workouts started from its templates since it began). */
    fun sessionsDone(container: AppContainer, f: Follow?): Flow<Int> =
        if (f == null) flowOf(0) else container.workoutRepo.completedRange(f.start, "9999-12-31").map { ws -> ws.count { it.templateId in f.tpl } }

    private fun items(p: Program, day: PDay, week: Int, ex: Map<String, com.myfit.tracker.data.db.Exercise>): List<WorkoutTemplateExercise> {
        val ph = p.phase(week)
        return day.items.mapNotNull { it ->
            val e = ex[it.k] ?: return@mapNotNull null
            val t = it.target(ph)
            WorkoutTemplateExercise(templateId = 0, exerciseId = e.id, position = 0, targetSets = t.sets, targetRepsMin = t.lo,
                targetRepsMax = t.hi, targetWeightKg = null, targetDurationSec = t.durSec?.toLong(), restSeconds = t.rest)
        }
    }

    private fun tplName(p: Program, d: PDay) = "${p.name} · ${d.name}"
    private fun tplNotes(p: Program, week: Int) = p.phase(week).let { "${p.name} — week $week of ${p.weeks} (${it.label}). ${it.note}" }

    /** Start following [p]: one template per training day. Replaces any program already being followed. */
    suspend fun start(container: AppContainer, p: Program) {
        stop(container)
        val repo = container.workoutRepo
        val ex = repo.exercisesByKeys(p.keys)
        val ids = p.days.map { d -> repo.saveTemplate(null, tplName(p, d), tplNotes(p, 1), items(p, d, 1, ex)) }
        save(container.app, Follow(p.id, LocalDate.now().toString(), ids))
    }

    /** Stop following. The program's templates are removed from the list (they stay in Archive, history is kept). */
    suspend fun stop(container: AppContainer) {
        val f = _follow.value ?: return
        f.tpl.forEach { runCatching { container.workoutRepo.archiveTemplate(it) } }
        save(container.app, null)
    }

    /** Rewrite the next session's template for this week and start it. Returns the new workout id. */
    suspend fun startSession(container: AppContainer, p: Program, f: Follow, pos: Position, dayIndex: Int = pos.day): Long {
        val repo = container.workoutRepo
        val day = p.days[dayIndex]
        val week = pos.week.coerceAtMost(p.weeks)
        val ex = repo.exercisesByKeys(day.items.map { it.k })
        var tid = f.tpl.getOrNull(dayIndex)
        if (tid == null || !repo.templateAlive(tid)) {
            tid = repo.saveTemplate(null, tplName(p, day), tplNotes(p, week), items(p, day, week, ex))
            val list = f.tpl.toMutableList()
            while (list.size <= dayIndex) list.add(-1)
            list[dayIndex] = tid
            save(container.app, f.copy(tpl = list))
        } else repo.saveTemplate(tid, tplName(p, day), tplNotes(p, week), items(p, day, week, ex))
        return repo.startFromTemplate(tid)
    }
}

// ------------------------------------------------------------------ filters
val LEVELS = listOf("beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced")
val GENDERS = listOf("men" to "Men", "women" to "Women")
val EQUIP = listOf("gym" to "Full gym", "dumbbells" to "Dumbbells", "home" to "Home", "bodyweight" to "Bodyweight", "kettlebell" to "Kettlebell")
val GOALS = listOf("muscle" to "Build muscle", "strength" to "Get stronger", "fatloss" to "Lose fat", "tone" to "Tone & shape",
    "endurance" to "Endurance", "athletic" to "Sport & athletic", "mobility" to "Mobility", "health" to "General health")
val DAYS = listOf(2, 3, 4, 5, 6)

fun label(list: List<Pair<String, String>>, k: String) = list.firstOrNull { it.first == k }?.second ?: k.replaceFirstChar { it.uppercase() }

data class ProgramFilter(
    val gender: Set<String> = emptySet(), val level: Set<String> = emptySet(), val equip: Set<String> = emptySet(),
    val goal: Set<String> = emptySet(), val days: Set<Int> = emptySet(),
) {
    val count get() = gender.size + level.size + equip.size + goal.size + days.size
    fun matches(p: Program) =
        (gender.isEmpty() || p.gender == "all" || p.gender in gender) &&
            (level.isEmpty() || p.level in level) && (equip.isEmpty() || p.equip in equip) &&
            (goal.isEmpty() || p.goals.any { it in goal }) && (days.isEmpty() || p.dpw.coerceIn(2, 6) in days)
}

fun Program.matchesQuery(q: String): Boolean {
    if (q.isBlank()) return true
    val hay = (name + " " + tag + " " + desc + " " + goals.joinToString(" ") { label(GOALS, it) } + " " + label(EQUIP, equip) + " " + level + " " +
        days.joinToString(" ") { it.name + " " + it.focus }).lowercase()
    return q.lowercase().split(' ').filter { it.isNotBlank() }.all { it in hay }
}
