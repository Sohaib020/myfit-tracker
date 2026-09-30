package com.myfit.tracker.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.Workout
import com.myfit.tracker.data.db.WorkoutExercise
import com.myfit.tracker.data.db.WorkoutSet
import com.myfit.tracker.data.db.WorkoutStatus
import com.myfit.tracker.data.db.WorkoutTemplate
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.WorkoutCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ExerciseRepository(private val db: AppDatabase, private val context: Context) {
    private val dao = db.exerciseDao()

    val active: Flow<List<Exercise>> = dao.observeActive()
    val all: Flow<List<Exercise>> = dao.observeAll()
    fun observe(id: Long) = dao.observe(id)
    suspend fun get(id: Long) = dao.get(id)

    /**
     * Seeds the bundled public-domain catalogue (free-exercise-db). Idempotent: rows are keyed by a
     * stable uuid ("fedb:<id>") with INSERT OR IGNORE, so re-running never duplicates or overwrites
     * anything — including personal notes the user has added to a built-in exercise.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val json = context.assets.open("exercise_catalog.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        if (dao.builtInCount() >= arr.length()) return@withContext
        val now = Clock.now()
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val instr = o.getJSONArray("i").let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } }
            val sec = o.getJSONArray("s").let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } }
            Exercise(
                uuid = "fedb:" + o.getString("k"),
                name = o.getString("n"),
                primaryMuscle = o.getString("g"),
                secondaryMuscles = listOf(o.getString("pm")).plus(if (sec.isEmpty()) emptyList() else sec.split(","))
                    .distinct().joinToString(","),
                equipment = o.getString("e"),
                measurementType = o.getString("m"),
                instructions = instr,
                imageKey = o.getString("k"),
                imageFrames = o.getInt("fr"),
                category = o.getString("c"),
                level = o.getString("l"),
                mechanic = o.getString("mc"),
                forceType = o.getString("f"),
                isCustom = false,
                createdAt = now, updatedAt = now,
            )
        }
        list.chunked(200).forEach { dao.insertAll(it) }
    }

    suspend fun createCustom(e: Exercise): Long = dao.insert(e.copy(id = 0, isCustom = true, createdAt = Clock.now(), updatedAt = Clock.now()))

    /** Measurement type can only change while no sets reference the exercise (it would reinterpret history). */
    suspend fun update(e: Exercise): Result<Unit> {
        val old = dao.get(e.id) ?: return Result.failure(IllegalStateException("Exercise not found"))
        if (old.measurementType != e.measurementType && dao.usageCount(e.id) > 0)
            return Result.failure(IllegalStateException("This exercise already has logged sets, so its measurement type can't change. Create a new exercise instead."))
        dao.update(e.copy(updatedAt = Clock.now()))
        return Result.success(Unit)
    }

    suspend fun setNotes(id: Long, notes: String) {
        val e = dao.get(id) ?: return
        dao.update(e.copy(personalNotes = notes, updatedAt = Clock.now()))
    }

    /** Never deletes — archived exercises disappear from pickers but keep all their history. */
    suspend fun archive(id: Long) = dao.archive(id, Clock.now())
    suspend fun unarchive(id: Long) = dao.unarchive(id, Clock.now())
    suspend fun usageCount(id: Long) = dao.usageCount(id)
}

/** A workout's exercise with its sets, ready for display. */
data class WorkoutExerciseView(
    val we: WorkoutExercise,
    val exercise: Exercise,
    val sets: List<SetRow>,
)

data class WorkoutView(
    val workout: Workout,
    val exercises: List<WorkoutExerciseView>,
) {
    val setData: List<WorkoutCalc.SetData>
        get() = exercises.flatMap { ev ->
            ev.sets.map { WorkoutCalc.SetData(ev.exercise.id, ev.exercise.measurementType, it.setType, it.weightKg, it.reps, it.durationSec, it.distanceM, it.restSec) }
        }
    val totals get() = WorkoutCalc.totals(setData)
}

data class TemplateView(val template: WorkoutTemplate, val items: List<Pair<WorkoutTemplateExercise, Exercise>>)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()
    private val tdao = db.templateDao()
    private val edao = db.exerciseDao()

    val inProgress: Flow<Workout?> = dao.observeInProgress()

    // ------------------------------------------------------------ views
    fun workoutView(id: Long): Flow<WorkoutView?> = combine(
        dao.observeWorkout(id), dao.observeWorkoutExercises(id), dao.observeSetRows(id), edao.observeAll(),
    ) { w, wes, sets, exs ->
        if (w == null) null else {
            val exMap = exs.associateBy { it.id }
            val byWe = sets.groupBy { it.workoutExerciseId }
            WorkoutView(w, wes.mapNotNull { we -> exMap[we.exerciseId]?.let { WorkoutExerciseView(we, it, byWe[we.id].orEmpty()) } })
        }
    }

    /** Recent completed workouts with their sets (for history and the dashboard). */
    fun recentViews(limit: Int): Flow<List<WorkoutView>> = dao.observeCompleted(limit).flatMapLatest { ws -> viewsFor(ws) }

    fun dayViews(date: String): Flow<List<WorkoutView>> = dao.observeDay(date).flatMapLatest { ws -> viewsFor(ws) }

    fun completedRange(from: String, to: String): Flow<List<Workout>> = dao.observeCompletedRange(from, to)

    private fun viewsFor(ws: List<Workout>): Flow<List<WorkoutView>> {
        if (ws.isEmpty()) return flowOf(emptyList())
        val ids = ws.map { it.id }
        return combine(dao.observeWorkoutExercisesFor(ids), dao.observeSetRowsFor(ids), edao.observeAll()) { wes, sets, exs ->
            val exMap = exs.associateBy { it.id }
            val setsByWe = sets.groupBy { it.workoutExerciseId }
            val wesByW = wes.groupBy { it.workoutId }
            ws.map { w ->
                WorkoutView(w, wesByW[w.id].orEmpty().mapNotNull { we -> exMap[we.exerciseId]?.let { WorkoutExerciseView(we, it, setsByWe[we.id].orEmpty()) } })
            }
        }
    }

    fun exerciseHistory(exerciseId: Long): Flow<List<SetRow>> = dao.observeExerciseHistory(exerciseId)
    suspend fun exerciseHistoryExcluding(exerciseId: Long, workoutId: Long) = dao.exerciseHistoryExcluding(exerciseId, workoutId)
    val usedExerciseIds: Flow<Set<Long>> = dao.observeUsedExerciseIds().map { it.toSet() }

    // ------------------------------------------------------------ start
    suspend fun startEmpty(name: String = "Workout"): Long = startWith(name, null, emptyList())

    suspend fun startFromTemplate(templateId: Long): Long {
        val t = tdao.getTemplate(templateId) ?: return startEmpty()
        val items = tdao.getItems(templateId)
        return startWith(t.name, templateId, items.map { it.exerciseId to it.supersetGroup })
    }

    /** "Repeat previous workout": same exercises in the same order; prefill comes from its sets. */
    suspend fun repeatWorkout(workoutId: Long): Long {
        val w = dao.getWorkout(workoutId) ?: return startEmpty()
        val wes = dao.getWorkoutExercises(workoutId)
        return startWith(w.name, w.templateId, wes.map { it.exerciseId to it.supersetGroup })
    }

    private suspend fun startWith(name: String, templateId: Long?, exercises: List<Pair<Long, Int?>>): Long {
        val s = Stamp.now()
        return db.withTransaction {
            val id = dao.insertWorkout(
                Workout(templateId = templateId, name = name, startedAt = s.at, endedAt = null, zoneId = s.zoneId,
                    localDate = s.localDate, status = WorkoutStatus.IN_PROGRESS, createdAt = s.at, updatedAt = s.at)
            )
            exercises.forEachIndexed { i, (ex, ss) -> dao.insertWorkoutExercise(WorkoutExercise(workoutId = id, exerciseId = ex, position = i, supersetGroup = ss)) }
            id
        }
    }

    // ------------------------------------------------------------ during
    suspend fun addExercise(workoutId: Long, exerciseId: Long): Long {
        val pos = dao.getWorkoutExercises(workoutId).maxOfOrNull { it.position }?.plus(1) ?: 0
        return dao.insertWorkoutExercise(WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = pos))
    }

    /** Only removes an exercise that has no logged sets — logged sets are deleted individually. */
    suspend fun removeExercise(weId: Long): Boolean = db.withTransaction {
        if (dao.getSetsFor(weId).isNotEmpty()) return@withTransaction false
        dao.deleteWorkoutExercise(weId); true
    }

    suspend fun moveExercise(workoutId: Long, weId: Long, delta: Int) = db.withTransaction {
        val list = dao.getWorkoutExercises(workoutId).toMutableList()
        val i = list.indexOfFirst { it.id == weId }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@withTransaction
        val a = list[i]; list[i] = list[j]; list[j] = a
        dao.updateWorkoutExercises(list.mapIndexed { idx, e -> e.copy(position = idx) })
    }

    suspend fun setSuperset(we: WorkoutExercise, group: Int?) = dao.updateWorkoutExercise(we.copy(supersetGroup = group))
    suspend fun setExerciseNote(we: WorkoutExercise, note: String) = dao.updateWorkoutExercise(we.copy(notes = note))

    suspend fun completeSet(
        weId: Long, setType: String, weightKg: Double?, reps: Int?, durationSec: Long?, distanceM: Double?, rpe: Double?, notes: String,
    ): Long = db.withTransaction {
        val existing = dao.getSetsFor(weId)
        val s = Stamp.now()
        dao.insertSet(
            WorkoutSet(workoutExerciseId = weId, setNumber = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1, setType = setType,
                weightKg = weightKg, reps = reps, durationSec = durationSec, distanceM = distanceM, rpe = rpe, restSec = null,
                completedAt = s.at, zoneId = s.zoneId, notes = notes, createdAt = s.at, updatedAt = s.at)
        )
    }

    suspend fun updateSet(id: Long, setType: String, weightKg: Double?, reps: Int?, durationSec: Long?, distanceM: Double?, rpe: Double?, notes: String) {
        val old = dao.getSet(id) ?: return
        dao.updateSet(old.copy(setType = setType, weightKg = weightKg, reps = reps, durationSec = durationSec, distanceM = distanceM, rpe = rpe, notes = notes, updatedAt = Clock.now()))
    }

    /** Deletes one set and renumbers the remaining sets of that exercise so numbering stays contiguous. */
    suspend fun deleteSet(id: Long) = db.withTransaction {
        val s = dao.getSet(id) ?: return@withTransaction
        dao.deleteSet(id)
        dao.getSetsFor(s.workoutExerciseId).sortedBy { it.setNumber }.forEachIndexed { i, x ->
            if (x.setNumber != i + 1) dao.updateSet(x.copy(setNumber = i + 1, updatedAt = Clock.now()))
        }
    }

    suspend fun recordRest(setId: Long, seconds: Long) = dao.setRest(setId, seconds, Clock.now())

    // ------------------------------------------------------------ finish
    /** Finishing drops exercises that were planned but never performed (no sets). */
    suspend fun finish(workoutId: Long, name: String, notes: String) = db.withTransaction {
        val w = dao.getWorkout(workoutId) ?: return@withTransaction
        dao.getWorkoutExercises(workoutId).forEach { if (dao.getSetsFor(it.id).isEmpty()) dao.deleteWorkoutExercise(it.id) }
        val remaining = dao.getWorkoutExercises(workoutId)
        remaining.forEachIndexed { i, e -> if (e.position != i) dao.updateWorkoutExercise(e.copy(position = i)) }
        dao.updateWorkout(w.copy(name = name.ifBlank { w.name }, notes = notes, endedAt = Clock.now(), status = WorkoutStatus.COMPLETED, updatedAt = Clock.now()))
    }

    suspend fun updateWorkoutMeta(workoutId: Long, name: String, notes: String, startedAt: Long, endedAt: Long?) {
        val w = dao.getWorkout(workoutId) ?: return
        val s = Stamp.of(startedAt)
        dao.updateWorkout(w.copy(name = name, notes = notes, startedAt = startedAt, endedAt = endedAt, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }

    suspend fun discard(workoutId: Long) = dao.softDeleteWorkout(workoutId, Clock.now())
    suspend fun deleteWorkout(workoutId: Long) = dao.softDeleteWorkout(workoutId, Clock.now())

    // ------------------------------------------------------------ templates
    val templates: Flow<List<TemplateView>> = combine(tdao.observeTemplates(), tdao.observeAllItems(), edao.observeAll()) { ts, items, exs ->
        val exMap = exs.associateBy { it.id }
        val byT = items.groupBy { it.templateId }
        ts.map { t -> TemplateView(t, byT[t.id].orEmpty().sortedBy { it.position }.mapNotNull { i -> exMap[i.exerciseId]?.let { i to it } }) }
    }

    suspend fun getTemplate(id: Long) = tdao.getTemplate(id)
    suspend fun getTemplateItems(id: Long) = tdao.getItems(id)

    suspend fun saveTemplate(id: Long?, name: String, notes: String, items: List<WorkoutTemplateExercise>): Long = db.withTransaction {
        val now = Clock.now()
        val tid = if (id == null) {
            tdao.insertTemplate(WorkoutTemplate(name = name, notes = notes, sortOrder = (System.currentTimeMillis() / 1000).toInt(), createdAt = now, updatedAt = now))
        } else {
            val t = tdao.getTemplate(id)!!
            tdao.updateTemplate(t.copy(name = name, notes = notes, updatedAt = now)); id
        }
        tdao.replaceItems(tid, items.mapIndexed { i, it -> it.copy(position = i) })
        tid
    }

    suspend fun duplicateTemplate(id: Long): Long? {
        val t = tdao.getTemplate(id) ?: return null
        return saveTemplate(null, "${t.name} (copy)", t.notes, tdao.getItems(id))
    }

    suspend fun archiveTemplate(id: Long) = tdao.archive(id, Clock.now())

    /**
     * Optional starter split (user-initiated). Creates templates only — no workouts, sets or targets
     * are invented: target weights are left empty so pre-fill comes from your own history.
     */
    suspend fun createStarterTemplates(): Int {
        data class P(val k: String, val sets: Int, val lo: Int?, val hi: Int?, val rest: Int)
        val plans = linkedMapOf(
            "Push" to listOf(P("Barbell_Bench_Press_-_Medium_Grip", 3, 6, 10, 150), P("Incline_Dumbbell_Press", 3, 8, 12, 120),
                P("Standing_Military_Press", 3, 6, 10, 120), P("Side_Lateral_Raise", 3, 12, 15, 60), P("Triceps_Pushdown", 3, 10, 15, 60)),
            "Pull" to listOf(P("Pullups", 3, 5, 10, 120), P("Bent_Over_Barbell_Row", 3, 6, 10, 120), P("Wide-Grip_Lat_Pulldown", 3, 8, 12, 90),
                P("Face_Pull", 3, 12, 15, 60), P("Barbell_Curl", 3, 8, 12, 60), P("Hammer_Curls", 2, 10, 12, 60)),
            "Legs" to listOf(P("Barbell_Squat", 3, 5, 8, 180), P("Romanian_Deadlift", 3, 8, 10, 150), P("Leg_Press", 3, 10, 12, 120),
                P("Lying_Leg_Curls", 3, 10, 12, 90), P("Standing_Calf_Raises", 4, 10, 15, 60)),
        )
        val ex = edao.byUuids(plans.values.flatten().map { "fedb:" + it.k }).associateBy { it.uuid.removePrefix("fedb:") }
        var n = 0
        plans.forEach { (name, ps) ->
            val items = ps.mapNotNull { p ->
                ex[p.k]?.let { WorkoutTemplateExercise(templateId = 0, exerciseId = it.id, position = 0, targetSets = p.sets,
                    targetRepsMin = p.lo, targetRepsMax = p.hi, targetWeightKg = null, restSeconds = p.rest) }
            }
            if (items.isNotEmpty()) { saveTemplate(null, name, "Starter template — edit freely", items); n++ }
        }
        return n
    }

    /** Save a finished workout's structure (exercises + set counts + what you actually did) as a template. */
    suspend fun templateFromWorkout(workoutId: Long, name: String): Long {
        val wes = dao.getWorkoutExercises(workoutId)
        val items = wes.map { we ->
            val sets = dao.getSetsFor(we.id)
            val working = sets.filter { it.setType != "WARMUP" }
            WorkoutTemplateExercise(
                templateId = 0, exerciseId = we.exerciseId, position = we.position, targetSets = working.size.coerceAtLeast(1),
                targetRepsMin = working.mapNotNull { it.reps }.minOrNull(), targetRepsMax = working.mapNotNull { it.reps }.maxOrNull(),
                targetWeightKg = null, restSeconds = 90, supersetGroup = we.supersetGroup,
            )
        }
        return saveTemplate(null, name, "", items)
    }
}
