package com.kevinjones.fitmasala.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinjones.fitmasala.data.local.FitMasalaDatabase
import com.kevinjones.fitmasala.data.local.Migrations
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Meal history must survive every schema step. Each test builds a database at
 * the old version from its exported schema, fills it with real-looking rows,
 * migrates it, and reads it back through the app's own DAO.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FitMasalaDatabase::class.java,
    )

    @Test
    fun v1MealHistorySurvivesTheEstimateModelColumn() = runTest {
        helper.createDatabase(dbName, 1).apply {
            // A photo Dish and a manual Dish on the same day, exactly as v1 wrote them.
            execSQL(
                """
                INSERT INTO logged_meals (id, name, region, mealType, cookingMethod, eatenAt, dayEpoch,
                    portionQuantity, portionUnit, portionNote, isAiEstimate, estimateConfidence, source,
                    photoPath, createdAt, calories, proteinG, carbsG, fatG, fiberG)
                VALUES (1, 'Dal tadka', 'PUNJABI', 'LUNCH', 'TADKA', 1728000000000, 20000,
                    1.5, 'KATORI', '1.5 katori', 1, 0.6, 'PHOTO',
                    '/data/files/meal-photos/meal-1.jpg', 1728000000000, 240.0, 14.0, 30.0, 8.0, 6.0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO logged_meals (id, name, region, mealType, cookingMethod, eatenAt, dayEpoch,
                    portionQuantity, portionUnit, isAiEstimate, source, createdAt,
                    calories, proteinG, carbsG, fatG, fiberG)
                VALUES (2, 'Poha', 'MAHARASHTRIAN', 'BREAKFAST', 'TADKA', 1727990000000, 20000,
                    1.0, 'PLATE', 0, 'MANUAL', 1727990000000, 300.0, 7.0, 50.0, 8.0, 3.0)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against v2 - column type, nullability, no default.
        helper.runMigrationsAndValidate(dbName, 2, true, *Migrations.ALL).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FitMasalaDatabase::class.java,
            dbName,
        ).addMigrations(*Migrations.ALL).build()
        try {
            val dao = db.mealDao()
            val dal = dao.byId(1)!!
            assertEquals("Dal tadka", dal.name)
            assertEquals(MealSource.PHOTO, dal.source)
            assertEquals(240.0, dal.macros.calories, 0.0)
            assertEquals(14.0, dal.macros.proteinG, 0.0)
            assertEquals(0.6, dal.estimateConfidence!!, 0.0)
            assertEquals("/data/files/meal-photos/meal-1.jpg", dal.photoPath)
            assertNull("v1 rows predate the column", dal.estimateModel)

            val poha = dao.byId(2)!!
            assertEquals(MealType.BREAKFAST, poha.mealType)
            assertNull(poha.estimateModel)

            // The day still counts as two Meals with the same totals.
            val day = dao.observeDayTotals(20_000).first()
            assertEquals(2, day.mealCount)
            assertEquals(540.0, day.calories, 0.001)

            // And a new Estimate records its model.
            val id = dao.insert(dal.copy(id = 0, estimateModel = "claude-opus-5"))
            assertEquals("claude-opus-5", dao.byId(id)!!.estimateModel)
        } finally {
            db.close()
        }
    }
}
