package com.kevinjones.fitmasala.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The exercise catalogue. Unique on name so [ExerciseSeed] can be idempotent.
 * RESTRICT on foreign keys ensures the history survives if an exercise is
 * mistakenly deleted.
 */
@Entity(
    tableName = "exercises",
    indices = [Index("name", unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment = Equipment.OTHER,
    val isArchived: Boolean = false
)

/**
 * A reusable sequence of exercises. Not tied to a specific day; a "Legs"
 * routine might be performed on Monday or Thursday.
 */
@Entity(tableName = "workout_routines")
data class WorkoutRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val lastPerformedAt: Long? = null
)

/**
 * Join table linking routines to exercises in a specific order.
 */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    primaryKeys = ["routineId", "exerciseId"],
    indices = [Index("exerciseId")]
)
data class RoutineExerciseEntity(
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int? = null,
    val targetReps: String? = null
)

/**
 * A single training session.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [Index("dayEpoch")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val dayEpoch: Long,
    val notes: String? = null
)

/**
 * A single set performed during a session.
 */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val isWarmup: Boolean = false,
    val completedAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)
