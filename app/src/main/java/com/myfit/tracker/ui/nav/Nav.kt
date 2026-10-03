package com.myfit.tracker.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Full-screen views stacked above the tab bar. */
sealed interface Overlay {
    data class Gym(val workoutId: Long) : Overlay
    data class ExerciseDetail(val exerciseId: Long) : Overlay
    data class ExerciseEditor(val exerciseId: Long?) : Overlay
    data class TemplateEditor(val templateId: Long?) : Overlay
    data class WorkoutDetail(val workoutId: Long) : Overlay
    data class FinishWorkout(val workoutId: Long) : Overlay
    data object Activity : Overlay
    data object PipChat : Overlay
    data object Archive : Overlay
    data object Me : Overlay
    data object ArrangeDash : Overlay
    data object Records : Overlay
    data object Social : Overlay
    data object Body : Overlay
    data object Badges : Overlay
    data object Cycle : Overlay
    data object Glucose : Overlay
    data object Meds : Overlay
    data object Vitals : Overlay
    data object CameraHr : Overlay
    data object Devices : Overlay
    data object Mind : Overlay
    data object Reminders : Overlay
    data object Supplements : Overlay
    data object Fasting : Overlay
    data object DevSettings : Overlay
    data object HealthHub : Overlay
    data class DayLog(val date: String? = null) : Overlay
    /** Full history (calendar icon in the top bar of every tab). */
    data object History : Overlay
    data object Deen : Overlay
    /** Pick exercises to append to a template (templateId) or to a running workout (workoutId). */
    data class PickExercises(val templateId: Long? = null, val workoutId: Long? = null) : Overlay
    data class Food(val date: String? = null) : Overlay
    data class FoodAdd(val mealType: String, val date: String, val tab: Int) : Overlay
    data class FoodPhoto(val mealType: String, val date: String) : Overlay
}

@Stable
class Nav {
    val stack = mutableStateListOf<Overlay>()
    fun push(o: Overlay) { stack.add(o) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Replace the top overlay (e.g. Gym → Finish). */
    fun replace(o: Overlay) { pop(); push(o) }
    fun popTo(pred: (Overlay) -> Boolean) { while (stack.isNotEmpty() && !pred(stack.last())) pop() }
    fun clear() { stack.clear() }
}

val LocalNav = staticCompositionLocalOf { Nav() }

/** One-shot requests for a screen to jump straight to an action when it opens (set by Home card buttons). */
object Launch {
    @Volatile var mind: String? = null
    @Volatile var cycle: String? = null
    fun takeMind(): String? = mind.also { mind = null }
    fun takeCycle(): String? = cycle.also { cycle = null }
}
