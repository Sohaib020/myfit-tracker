package com.myfit.tracker.ui.gym

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.data.repo.WorkoutExerciseView
import com.myfit.tracker.data.repo.WorkoutView
import com.myfit.tracker.domain.Prefill
import com.myfit.tracker.ui.exercises.bestSet
import com.myfit.tracker.ui.exercises.sessionsOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What's in the input fields for the next set of one exercise. Weight is canonical kg. */
data class Draft(
    val setType: String = SetType.WORKING,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Long? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val source: String = "",            // where the pre-fill came from, shown to the user
)

/** History of one exercise before this workout. */
data class ExerciseHistory(val lastSession: List<SetRow>, val lastDate: String?, val best: SetRow?, val sessions: Int)

class GymViewModel(private val c: AppContainer, val workoutId: Long) : ViewModel() {

    val view: StateFlow<WorkoutView?> = c.workoutRepo.workoutView(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var currentWeId by mutableStateOf<Long?>(null)
    val drafts = mutableStateMapOf<Long, Draft>()                 // keyed by workoutExerciseId
    val history = mutableStateMapOf<Long, ExerciseHistory>()       // keyed by exerciseId
    val targets = mutableStateMapOf<Long, WorkoutTemplateExercise>() // keyed by exerciseId
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val templateId = c.db.workoutDao().getWorkout(workoutId)?.templateId
            if (templateId != null) c.workoutRepo.getTemplateItems(templateId).forEach { targets[it.exerciseId] = it }
        }
    }

    fun select(weId: Long) { currentWeId = weId; opened.putIfAbsent(weId, System.currentTimeMillis()) }

    /** When you opened each exercise this session (for its "time on this exercise" clock). */
    private val opened = HashMap<Long, Long>()
    /** Running set timers (timed exercises), keyed by workoutExerciseId. */
    val setTimerStart = mutableStateMapOf<Long, Long>()

    /** Start of the time spent on [ev]: its first logged set minus a typical set, or when it was first opened. */
    fun exerciseStart(ev: WorkoutExerciseView): Long? {
        val first = ev.sets.minOfOrNull { it.completedAt }?.let { it - 45_000 }
        val o = opened[ev.we.id]
        return listOfNotNull(first, o).minOrNull()
    }

    /** Loads history once per exercise, then builds the draft if none exists yet. */
    fun ensureLoaded(ev: WorkoutExerciseView) {
        val exId = ev.exercise.id
        if (history.containsKey(exId)) { ensureDraft(ev); return }
        viewModelScope.launch {
            val rows = c.workoutRepo.exerciseHistoryExcluding(exId, workoutId)
            val sessions = sessionsOf(rows)
            val last = sessions.lastOrNull()
            history[exId] = ExerciseHistory(last?.sets.orEmpty(), last?.date, bestSet(ev.exercise.measurementType, rows), sessions.size)
            ensureDraft(ev)
        }
    }

    private fun ensureDraft(ev: WorkoutExerciseView) {
        if (drafts.containsKey(ev.we.id)) return
        drafts[ev.we.id] = prefillFor(ev)
    }

    private fun prefillFor(ev: WorkoutExerciseView): Draft {
        fun v(s: SetRow) = Prefill.Values(s.weightKg, s.reps, s.durationSec, s.distanceM)
        val today = ev.sets.filter { it.setType != SetType.WARMUP }.map(::v)
        val prev = history[ev.exercise.id]?.lastSession.orEmpty().filter { it.setType != SetType.WARMUP }.map(::v)
        val t = targets[ev.exercise.id]?.let { Prefill.Values(it.targetWeightKg, it.targetRepsMax ?: it.targetRepsMin, it.targetDurationSec, null) }
        val p = Prefill.next(today, prev, t)
        val src = when {
            today.isNotEmpty() -> "Same as your last set"
            prev.isNotEmpty() -> "From your last session"
            t != null -> "From your template"
            else -> ""
        }
        return Draft(weightKg = p.weightKg, reps = p.reps, durationSec = p.durationSec, distanceM = p.distanceM, source = src)
    }

    fun update(weId: Long, f: (Draft) -> Draft) { drafts[weId] = f(drafts[weId] ?: Draft()) }

    fun validate(m: String, d: Draft): String? = when (m) {
        MeasurementType.WEIGHT_REPS -> when {
            d.weightKg == null || d.weightKg < 0 -> "Enter the weight"
            d.reps == null || d.reps <= 0 -> "Enter the reps"
            else -> null
        }
        MeasurementType.BODYWEIGHT_REPS, MeasurementType.ASSISTED_REPS, MeasurementType.REPS_ONLY ->
            if (d.reps == null || d.reps <= 0) "Enter the reps" else null
        MeasurementType.DURATION -> if (d.durationSec == null || d.durationSec <= 0) "Enter the duration" else null
        MeasurementType.WEIGHT_DURATION -> if (d.durationSec == null || d.durationSec <= 0) "Enter the duration" else null
        MeasurementType.DISTANCE_DURATION -> if ((d.distanceM ?: 0.0) <= 0 && (d.durationSec ?: 0) <= 0) "Enter distance or duration" else null
        else -> null
    }

    data class CompleteResult(val error: String? = null, val beatBest: String? = null)

    /**
     * Saves the set exactly as entered, records the rest actually taken before it, moves superset
     * partners along, and (optionally) starts the rest timer.
     */
    fun complete(ev: WorkoutExerciseView, all: List<WorkoutExerciseView>, s: AppSettings, onDone: (CompleteResult) -> Unit) {
        val d = drafts[ev.we.id] ?: Draft()
        val m = ev.exercise.measurementType
        validate(m, d)?.let { onDone(CompleteResult(error = it)); return }
        if (busy) return
        busy = true
        val stopped = c.restTimer.stop()
        viewModelScope.launch {
            try {
                stopped?.let { (setId, sec) -> c.workoutRepo.recordRest(setId, sec) }
                val keepWeight = m in listOf(MeasurementType.WEIGHT_REPS, MeasurementType.BODYWEIGHT_REPS, MeasurementType.ASSISTED_REPS, MeasurementType.WEIGHT_DURATION)
                val keepReps = m in listOf(MeasurementType.WEIGHT_REPS, MeasurementType.BODYWEIGHT_REPS, MeasurementType.ASSISTED_REPS, MeasurementType.REPS_ONLY)
                val keepDur = m in listOf(MeasurementType.DURATION, MeasurementType.WEIGHT_DURATION, MeasurementType.DISTANCE_DURATION)
                val newId = c.workoutRepo.completeSet(
                    ev.we.id, d.setType,
                    if (keepWeight) d.weightKg else null, if (keepReps) d.reps else null,
                    if (keepDur) d.durationSec else null, if (m == MeasurementType.DISTANCE_DURATION) d.distanceM else null,
                    d.rpe, "",
                )
                // live comparison against recorded history (not a stored PR — PR detection runs on raw sets)
                val best = history[ev.exercise.id]?.best
                val beat = when {
                    d.setType == SetType.WARMUP || best == null -> null
                    m == MeasurementType.WEIGHT_REPS && (d.weightKg ?: 0.0) > (best.weightKg ?: 0.0) -> "Heaviest you've recorded for this exercise"
                    m == MeasurementType.WEIGHT_REPS && d.weightKg == best.weightKg && (d.reps ?: 0) > (best.reps ?: 0) -> "Most reps you've recorded at this weight"
                    m in listOf(MeasurementType.BODYWEIGHT_REPS, MeasurementType.REPS_ONLY) && (d.reps ?: 0) > (best.reps ?: 0) -> "Most reps you've recorded for this exercise"
                    else -> null
                }
                // keep type sticky only for warm-ups → working; drop/failure revert to working
                drafts[ev.we.id] = d.copy(setType = SetType.WORKING, rpe = null, source = "Same as your last set")

                // supersets: move to the next exercise in the group; rest only after the last one
                val group = ev.we.supersetGroup
                val members = if (group == null) emptyList() else all.filter { it.we.supersetGroup == group }
                val idx = members.indexOfFirst { it.we.id == ev.we.id }
                val restNow = if (members.size > 1 && idx >= 0 && idx < members.lastIndex) {
                    currentWeId = members[idx + 1].we.id; false
                } else {
                    if (members.size > 1) currentWeId = members.first().we.id
                    true
                }
                if (restNow && s.restAutoStart) {
                    val sec = targets[ev.exercise.id]?.restSeconds ?: s.restDefaultSec
                    c.restTimer.start(newId, sec, nextLabel(ev, all), s.restSound, s.restVibrate)
                }
                onDone(CompleteResult(beatBest = beat))
            } finally { busy = false }
        }
    }

    private fun nextLabel(ev: WorkoutExerciseView, all: List<WorkoutExerciseView>) = "${ev.exercise.name}, set ${ev.sets.size + 2}"

    fun startRest(ev: WorkoutExerciseView, s: AppSettings) {
        val last = ev.sets.lastOrNull() ?: return
        c.restTimer.start(last.setId, targets[ev.exercise.id]?.restSeconds ?: s.restDefaultSec, ev.exercise.name, s.restSound, s.restVibrate)
    }

    fun skipRest() {
        val stopped = c.restTimer.stop() ?: return
        c.write { c.workoutRepo.recordRest(stopped.first, stopped.second) }
    }

    fun editSet(setId: Long, d: Draft) = c.write {
        c.workoutRepo.updateSet(setId, d.setType, d.weightKg, d.reps, d.durationSec, d.distanceM, d.rpe, "")
    }

    fun deleteSet(setId: Long) = c.write { c.workoutRepo.deleteSet(setId) }

    fun addExercises(ids: List<Long>) = viewModelScope.launch {
        var first: Long? = null
        ids.forEach { val weId = c.workoutRepo.addExercise(workoutId, it); if (first == null) first = weId }
        first?.let { currentWeId = it }
    }

    fun move(weId: Long, delta: Int) = c.write { c.workoutRepo.moveExercise(workoutId, weId, delta) }

    fun remove(weId: Long, onResult: (Boolean) -> Unit, withSets: Boolean = false) = viewModelScope.launch {
        val ok = c.workoutRepo.removeExercise(weId, withSets)
        if (ok && currentWeId == weId) currentWeId = null
        onResult(ok)
    }

    /** Links this exercise and the next one as a superset (or unlinks it). */
    fun toggleSuperset(ev: WorkoutExerciseView, all: List<WorkoutExerciseView>) = c.write {
        if (ev.we.supersetGroup != null) { c.workoutRepo.setSuperset(ev.we, null); return@write }
        val i = all.indexOfFirst { it.we.id == ev.we.id }
        val next = all.getOrNull(i + 1) ?: return@write
        val g = next.we.supersetGroup ?: ((all.mapNotNull { it.we.supersetGroup }.maxOrNull() ?: 0) + 1)
        c.workoutRepo.setSuperset(ev.we, g)
        c.workoutRepo.setSuperset(next.we, g)
    }

    class Factory(private val c: AppContainer, private val id: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GymViewModel(c, id) as T
    }
}
