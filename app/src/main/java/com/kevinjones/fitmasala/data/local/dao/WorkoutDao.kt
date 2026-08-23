package com.kevinjones.fitmasala.data.local.dao

import androidx.room.*
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.RoutineExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutRoutineEntity
import com.kevinjones.fitmasala.data.local.relation.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // --- Exercises ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name ASC")
    fun observeAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exerciseById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun exerciseByName(name: String): ExerciseEntity?

    // --- Routines ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: WorkoutRoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(links: List<RoutineExerciseEntity>)

    @Transaction
    suspend fun saveRoutine(routine: WorkoutRoutineEntity, exercises: List<RoutineExerciseEntity>) {
        val id = insertRoutine(routine)
        insertRoutineExercises(exercises.map { it.copy(routineId = id) })
    }

    @Query("SELECT * FROM workout_routines ORDER BY name ASC")
    fun observeAllRoutines(): Flow<List<WorkoutRoutineEntity>>

    @Transaction
    @Query("SELECT * FROM workout_routines WHERE id = :id")
    fun observeRoutineWithExercises(id: Long): Flow<RoutineWithExercises?>

    @Delete
    suspend fun deleteRoutine(routine: WorkoutRoutineEntity)
}
