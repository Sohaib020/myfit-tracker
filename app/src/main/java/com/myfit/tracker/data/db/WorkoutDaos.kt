package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A set together with the context needed to interpret it (which exercise, which workout, when). */
data class SetRow(
    val setId: Long,
    val workoutExerciseId: Long,
    val workoutId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val setType: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Long?,
    val distanceM: Double?,
    val rpe: Double?,
    val restSec: Long?,
    val completedAt: Long,
    val zoneId: String,
    val notes: String,
    val workoutStartedAt: Long,
    val workoutLocalDate: String,
    val workoutStatus: String,
)

private const val SET_ROW_SELECT = """
    SELECT ws.id AS setId, ws.workoutExerciseId, we.workoutId, we.exerciseId, ws.setNumber, ws.setType,
           ws.weightKg, ws.reps, ws.durationSec, ws.distanceM, ws.rpe, ws.restSec, ws.completedAt, ws.zoneId,
           ws.notes, w.startedAt AS workoutStartedAt, w.localDate AS workoutLocalDate, w.status AS workoutStatus
    FROM workout_set ws
    JOIN workout_exercise we ON ws.workoutExerciseId = we.id
    JOIN workout w ON we.workoutId = w.id
"""

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Exercise>): List<Long>

    @Insert
    suspend fun insert(e: Exercise): Long

    @Update
    suspend fun update(e: Exercise)

    @Query("SELECT COUNT(*) FROM exercise WHERE isCustom = 0")
    suspend fun builtInCount(): Int

    @Query("SELECT * FROM exercise WHERE archivedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observe(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercise WHERE uuid IN (:uuids)")
    suspend fun byUuids(uuids: List<String>): List<Exercise>

    @Query("SELECT COUNT(*) FROM workout_exercise WHERE exerciseId = :id")
    suspend fun usageCount(id: Long): Int

    @Query("UPDATE exercise SET archivedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Query("UPDATE exercise SET archivedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun unarchive(id: Long, now: Long)
}

@Dao
interface WorkoutDao {
    // ---- workouts
    @Insert suspend fun insertWorkout(w: Workout): Long
    @Update suspend fun updateWorkout(w: Workout)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: Long): Workout?

    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkout(id: Long): Flow<Workout?>

    @Query("SELECT * FROM workout WHERE status = 'IN_PROGRESS' AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<Workout?>

    @Query("SELECT * FROM workout WHERE status = 'COMPLETED' AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<Workout>>

    @Query("SELECT * FROM workout WHERE deletedAt IS NULL AND localDate = :date ORDER BY startedAt")
    fun observeDay(date: String): Flow<List<Workout>>

    @Query("SELECT * FROM workout WHERE deletedAt IS NULL AND status = 'COMPLETED' AND localDate BETWEEN :from AND :to ORDER BY startedAt")
    fun observeCompletedRange(from: String, to: String): Flow<List<Workout>>

    @Query("UPDATE workout SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteWorkout(id: Long, now: Long)

    // ---- workout exercises
    @Insert suspend fun insertWorkoutExercise(e: WorkoutExercise): Long
    @Update suspend fun updateWorkoutExercise(e: WorkoutExercise)
    @Update suspend fun updateWorkoutExercises(list: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    fun observeWorkoutExercises(workoutId: Long): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExercise>

    @Query("SELECT * FROM workout_exercise WHERE workoutId IN (:ids) ORDER BY position")
    fun observeWorkoutExercisesFor(ids: List<Long>): Flow<List<WorkoutExercise>>

    // ---- sets (raw source of truth)
    @Insert suspend fun insertSet(s: WorkoutSet): Long
    @Update suspend fun updateSet(s: WorkoutSet)

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSet?

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :weId ORDER BY setNumber")
    suspend fun getSetsFor(weId: Long): List<WorkoutSet>

    @Query("UPDATE workout_set SET restSec = :rest, updatedAt = :now WHERE id = :id")
    suspend fun setRest(id: Long, rest: Long, now: Long)

    @Query("$SET_ROW_SELECT WHERE we.workoutId = :workoutId ORDER BY we.position, ws.setNumber")
    fun observeSetRows(workoutId: Long): Flow<List<SetRow>>

    @Query("$SET_ROW_SELECT WHERE we.workoutId IN (:ids) ORDER BY we.position, ws.setNumber")
    fun observeSetRowsFor(ids: List<Long>): Flow<List<SetRow>>

    /** Every completed set of one exercise, oldest first — history, graphs and bests derive from this. */
    @Query("$SET_ROW_SELECT WHERE we.exerciseId = :exerciseId AND w.deletedAt IS NULL AND w.status = 'COMPLETED' ORDER BY w.startedAt, we.position, ws.setNumber")
    fun observeExerciseHistory(exerciseId: Long): Flow<List<SetRow>>

    /** Every completed set of every exercise, oldest first — PR history across the whole log. */
    @Query("$SET_ROW_SELECT WHERE w.deletedAt IS NULL AND w.status = 'COMPLETED' ORDER BY w.startedAt, we.position, ws.setNumber")
    fun observeAllHistory(): Flow<List<SetRow>>

    @Query("$SET_ROW_SELECT WHERE we.exerciseId = :exerciseId AND w.deletedAt IS NULL AND w.status = 'COMPLETED' AND w.id != :excludeWorkoutId ORDER BY w.startedAt, we.position, ws.setNumber")
    suspend fun exerciseHistoryExcluding(exerciseId: Long, excludeWorkoutId: Long): List<SetRow>

    @Query("SELECT DISTINCT we.exerciseId FROM workout_exercise we JOIN workout w ON we.workoutId = w.id WHERE w.deletedAt IS NULL AND w.status = 'COMPLETED'")
    fun observeUsedExerciseIds(): Flow<List<Long>>
}

@Dao
interface TemplateDao {
    @Insert suspend fun insertTemplate(t: WorkoutTemplate): Long
    @Update suspend fun updateTemplate(t: WorkoutTemplate)

    @Insert suspend fun insertItems(items: List<WorkoutTemplateExercise>)

    @Query("DELETE FROM workout_template_exercise WHERE templateId = :templateId")
    suspend fun clearItems(templateId: Long)

    @Query("SELECT * FROM workout_template WHERE archivedAt IS NULL ORDER BY sortOrder, name")
    fun observeTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM workout_template_exercise ORDER BY templateId, position")
    fun observeAllItems(): Flow<List<WorkoutTemplateExercise>>

    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getTemplate(id: Long): WorkoutTemplate?

    @Query("SELECT * FROM workout_template_exercise WHERE templateId = :id ORDER BY position")
    suspend fun getItems(id: Long): List<WorkoutTemplateExercise>

    @Query("UPDATE workout_template SET archivedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Query("UPDATE workout_template SET archivedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun unarchive(id: Long, now: Long)

    @Query("SELECT * FROM workout_template WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchivedTemplates(): Flow<List<WorkoutTemplate>>

    @Transaction
    suspend fun replaceItems(templateId: Long, items: List<WorkoutTemplateExercise>) {
        clearItems(templateId)
        insertItems(items.map { it.copy(id = 0, templateId = templateId) })
    }
}
