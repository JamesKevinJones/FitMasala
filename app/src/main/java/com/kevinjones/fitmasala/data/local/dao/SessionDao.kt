package com.kevinjones.fitmasala.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kevinjones.fitmasala.data.local.entity.ExerciseSetEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutSessionEntity
import com.kevinjones.fitmasala.data.local.relation.ExercisePersonalBest
import com.kevinjones.fitmasala.data.local.relation.SessionSummary
import com.kevinjones.fitmasala.data.local.relation.SessionWithSets
import com.kevinjones.fitmasala.data.local.relation.SetWithExercise
import kotlinx.coroutines.flow.Flow

/** Live sessions, logged sets, and the progressive-overload lookups. */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun finishSession(id: Long, finishedAt: Long)

    /**
     * The crash-recovery query. A session with no `finishedAt` is still running,
     * which is how the app picks a workout back up after being killed in a
     * pocket between sets. LIMIT 1 because only one can be active.
     */
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSessionWithSets(id: Long): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    fun observeSetsWithExercise(sessionId: Long): Flow<List<SetWithExercise>>

    // --- Sets ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: ExerciseSetEntity): Long

    @Update
    suspend fun updateSet(set: ExerciseSetEntity)

    @Delete
    suspend fun deleteSet(set: ExerciseSetEntity)

    @Query("SELECT COALESCE(MAX(setIndex), 0) FROM exercise_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun lastSetIndex(sessionId: Long, exerciseId: Long): Int

    // --- Progressive overload ---

    /**
     * "What did I do last time?" — the sets from the most recent OTHER session
     * that touched this exercise. Excluding the current session matters: without
     * it, the first set you log today becomes the target you are trying to beat.
     *
     * Warm-ups are excluded; comparing today's working set against last week's
     * empty-bar warm-up would report progress that did not happen.
     */
    @Query(
        """
        SELECT * FROM exercise_sets
        WHERE exerciseId = :exerciseId
          AND sessionId != :excludeSessionId
          AND isWarmup = 0
          AND sessionId = (
              SELECT sessionId FROM exercise_sets
              WHERE exerciseId = :exerciseId
                AND sessionId != :excludeSessionId
                AND isWarmup = 0
              ORDER BY completedAt DESC
              LIMIT 1
          )
        ORDER BY setIndex ASC
        """,
    )
    suspend fun previousPerformance(exerciseId: Long, excludeSessionId: Long): List<ExerciseSetEntity>

    /**
     * Ranked by estimated 1RM rather than raw weight, computed inline with the
     * Epley formula so 100kg x 3 correctly outranks 80kg x 8.
     *
     * Every column is COALESCE'd and the id is passed through as a bound
     * parameter: an aggregate over zero rows returns one row of NULLs, which
     * Room cannot write into non-null Kotlin fields. A never-lifted exercise
     * returns a zeroed best, not a crash.
     */
    @Query(
        """
        SELECT
            :exerciseId AS exerciseId,
            COALESCE(MAX(weightKg), 0.0) AS bestWeightKg,
            COALESCE(MAX(weightKg * (1 + reps / 30.0)), 0.0) AS bestOneRepMax,
            COALESCE(MAX(completedAt), 0) AS lastPerformedAt,
            COUNT(*) AS totalSets
        FROM exercise_sets
        WHERE exerciseId = :exerciseId AND isWarmup = 0
        """,
    )
    fun observePersonalBest(exerciseId: Long): Flow<ExercisePersonalBest>

    @Query(
        """
        SELECT
            s.id AS sessionId,
            s.name AS name,
            s.startedAt AS startedAt,
            s.finishedAt AS finishedAt,
            s.dayEpoch AS dayEpoch,
            COUNT(e.id) AS setCount,
            COALESCE(SUM(e.weightKg * e.reps), 0.0) AS totalVolumeKg
        FROM workout_sessions s
        LEFT JOIN exercise_sets e ON e.sessionId = s.id AND e.isWarmup = 0
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentSessions(limit: Int = 30): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT
            s.id AS sessionId,
            s.name AS name,
            s.startedAt AS startedAt,
            s.finishedAt AS finishedAt,
            s.dayEpoch AS dayEpoch,
            COUNT(e.id) AS setCount,
            COALESCE(SUM(e.weightKg * e.reps), 0.0) AS totalVolumeKg
        FROM workout_sessions s
        LEFT JOIN exercise_sets e ON e.sessionId = s.id AND e.isWarmup = 0
        WHERE s.dayEpoch = :dayEpoch
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeSessionsForDay(dayEpoch: Long): Flow<List<SessionSummary>>
}
