package com.kevinjones.fitmasala.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinjones.fitmasala.data.local.FitMasalaDatabase
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.entity.Region
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealDaoTest {

    private lateinit var db: FitMasalaDatabase
    private lateinit var dao: MealDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FitMasalaDatabase::class.java,
        ).build()
        dao = db.mealDao()
    }

    @After
    fun tearDown() = db.close()

    private fun meal(
        name: String,
        day: Long,
        macros: Macros,
        type: MealType = MealType.LUNCH,
    ) = LoggedMealEntity(
        name = name,
        region = Region.PUNJABI,
        mealType = type,
        eatenAt = day * 86_400_000L,
        dayEpoch = day,
        portionQuantity = 2.0,
        portionUnit = PortionUnit.KATORI,
        macros = macros,
    )

    @Test
    fun insertAndReadRoundTripsEveryField() = runTest {
        val macros = Macros(calories = 420.0, proteinG = 18.5, carbsG = 44.0, fatG = 19.0, fiberG = 6.0)
        val id = dao.insert(meal("Rajma Chawal", day = 20_000, macros = macros))

        val loaded = dao.byId(id)

        assertEquals("Rajma Chawal", loaded?.name)
        assertEquals(MealType.LUNCH, loaded?.mealType)
        assertEquals(Region.PUNJABI, loaded?.region)
        assertEquals(PortionUnit.KATORI, loaded?.portionUnit)
        // The embedded Macros must survive without a column prefix.
        assertEquals(420.0, loaded?.macros?.calories ?: 0.0, 0.001)
        assertEquals(18.5, loaded?.macros?.proteinG ?: 0.0, 0.001)
    }

    @Test
    fun dayTotalsSumOnlyTheRequestedDay() = runTest {
        dao.insert(meal("Poha", 20_000, Macros(calories = 300.0, proteinG = 7.0, carbsG = 50.0, fatG = 8.0)))
        dao.insert(meal("Dal Tadka", 20_000, Macros(calories = 240.0, proteinG = 14.0, carbsG = 30.0, fatG = 8.0)))
        dao.insert(meal("Next day biryani", 20_001, Macros(calories = 900.0)))

        val totals = dao.observeDayTotals(20_000).first()

        assertEquals(540.0, totals.calories, 0.001)
        assertEquals(21.0, totals.proteinG, 0.001)
        assertEquals(2, totals.mealCount)
    }

    /**
     * The empty-day case is the one that actually bites: without COALESCE this
     * returns a row of NULLs and Room throws trying to write them into non-null
     * Doubles.
     */
    @Test
    fun emptyDayReturnsZeroesNotNulls() = runTest {
        val totals = dao.observeDayTotals(19_999).first()

        assertEquals(0.0, totals.calories, 0.001)
        assertEquals(0, totals.mealCount)
    }

    @Test
    fun calorieTrendIsGroupedByDayAndOrdered() = runTest {
        dao.insert(meal("A", 20_000, Macros(calories = 100.0)))
        dao.insert(meal("B", 20_002, Macros(calories = 200.0)))
        dao.insert(meal("C", 20_002, Macros(calories = 50.0)))

        val trend = dao.observeCalorieTrend(19_998, 20_003).first()

        assertEquals(2, trend.size)
        assertEquals(20_000L, trend[0].dayEpoch)
        assertEquals(100.0, trend[0].calories, 0.001)
        assertEquals(250.0, trend[1].calories, 0.001)
        assertTrue(trend[0].dayEpoch < trend[1].dayEpoch)
    }
}
