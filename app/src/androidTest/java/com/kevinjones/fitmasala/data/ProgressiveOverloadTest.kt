package com.kevinjones.fitmasala.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinjones.fitmasala.data.local.FitMasalaDatabase
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.dao.WorkoutDao
import com.kevinjones.fitmasala.data.local.entity.Equipment
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.ExerciseSetEntity
import com.kevinjones.fitmasala.data.local.entity.MuscleGroup
import com.kevinjones.fitmasala.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The queries the workout module's whole value rests on. */
@RunWith(AndroidJUnit4::class)
class ProgressiveOverloadTest {

    private lateinit var db: FitMasalaDatabase
    private lateinit var sessions: SessionDao
    private lateinit var workouts: WorkoutDao
    private var benchId: Long = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FitMasalaDatabase::class.java,
        ).build()
        sessions = db.sessionDao()
        workouts = db.workoutDao()
        benchId = workouts.insertExercise(
            ExerciseEntity(name = "Barbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(name: String, startedAt: Long): Long =
        sessions.insertSession(
            WorkoutSessionEntity(name = name, startedAt = startedAt, dayEpoch = startedAt / 86_400_000L),
        )

    private suspend fun logSet(
        sessionId: Long,
        index: Int,
        reps: Int,
        kg: Double,
        at: Long,
        warmup: Boolean = false,
    ) = sessions.insertSet(
        ExerciseSetEntity(
            sessionId = sessionId,
            exerciseId = benchId,
            setIndex = index,
            reps = reps,
            weightKg = kg,
            isWarmup = warmup,
            completedAt = at,
        ),
    )

    @Test
    fun previousPerformanceReturnsLastSessionNotThisOne() = runTest {
        val lastWeek = session("Push A", 1_000_000)
        logSet(lastWeek, 1, reps = 8, kg = 60.0, at = 1_000_100)
        logSet(lastWeek, 2, reps = 7, kg = 60.0, at = 1_000_200)

        val today = session("Push A", 2_000_000)
        logSet(today, 1, reps = 9, kg = 62.5, at = 2_000_100)

        val previous = sessions.previousPerformance(benchId, excludeSessionId = today)

        // Today's heavier set must not become the target it is being compared to.
        assertEquals(2, previous.size)
        assertTrue(previous.all { it.weightKg == 60.0 })
        assertEquals(1, previous[0].setIndex)
    }

    @Test
    fun previousPerformanceIgnoresWarmupSets() = runTest {
        val lastWeek = session("Push A", 1_000_000)
        logSet(lastWeek, 0, reps = 12, kg = 20.0, at = 1_000_050, warmup = true)
        logSet(lastWeek, 1, reps = 8, kg = 60.0, at = 1_000_100)

        val today = session("Push A", 2_000_000)
        val previous = sessions.previousPerformance(benchId, excludeSessionId = today)

        // An empty-bar warm-up must never be reported as last week's working set.
        assertEquals(1, previous.size)
        assertEquals(60.0, previous[0].weightKg, 0.001)
    }

    @Test
    fun personalBestRanksByOneRepMaxNotRawWeightAlone() = runTest {
        val a = session("Push A", 1_000_000)
        logSet(a, 1, reps = 8, kg = 80.0, at = 1_000_100)   // 1RM ~ 101.3
        val b = session("Push B", 2_000_000)
        logSet(b, 1, reps = 3, kg = 100.0, at = 2_000_100)  // 1RM ~ 110.0

        val best = sessions.observePersonalBest(benchId).first()

        assertEquals(100.0, best.bestWeightKg, 0.001)
        assertEquals(110.0, best.bestOneRepMax, 0.1)
        assertEquals(2, best.totalSets)
    }

    /** Zero rows through an aggregate is the classic Room null crash. */
    @Test
    fun personalBestForUntouchedExerciseIsZeroedNotACrash() = runTest {
        val squatId = workouts.insertExercise(
            ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )

        val best = sessions.observePersonalBest(squatId).first()

        assertEquals(squatId, best.exerciseId)
        assertEquals(0.0, best.bestOneRepMax, 0.001)
        assertEquals(0, best.totalSets)
    }

    @Test
    fun activeSessionIsTheOneWithoutAFinishTime() = runTest {
        val finished = session("Pull A", 1_000_000)
        sessions.finishSession(finished, finishedAt = 1_500_000)
        assertNull(sessions.observeActiveSession().first())

        val running = session("Push A", 2_000_000)
        val active = sessions.observeActiveSession().first()

        assertNotNull(active)
        assertEquals(running, active?.id)
    }

    @Test
    fun deletingASessionRemovesItsSetsButNotTheExercise() = runTest {
        val s = session("Push A", 1_000_000)
        logSet(s, 1, reps = 8, kg = 60.0, at = 1_000_100)

        val entity = sessions.observeSessionWithSets(s).first()
        assertEquals(1, entity?.sets?.size)

        sessions.deleteSession(entity!!.session)

        assertNull(sessions.observeSessionWithSets(s).first())
        // RESTRICT on exerciseId means the catalogue entry survives.
        assertNotNull(workouts.exerciseById(benchId))
    }
}
