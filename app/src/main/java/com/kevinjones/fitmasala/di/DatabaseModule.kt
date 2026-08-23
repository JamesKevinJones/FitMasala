package com.kevinjones.fitmasala.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevinjones.fitmasala.data.local.ExerciseSeed
import com.kevinjones.fitmasala.data.local.FitMasalaDatabase
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.dao.PlanDao
import com.kevinjones.fitmasala.data.local.dao.RecipeDao
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Seeding is done with raw SQL inside `onCreate` rather than through the
     * DAO. Going through the DAO would mean asking Dagger for a DAO that comes
     * from the database currently being built, and doing it off-thread to avoid
     * that means racing the first read. Raw inserts run inside Room's own
     * creation transaction: synchronous, atomic, exactly once.
     *
     * The column list is hand-written, so it must be updated if ExerciseEntity
     * gains a non-defaulted column. The androidTest migration suite is what
     * catches that.
     */
    private val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            ExerciseSeed.exercises.forEach { exercise ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercises
                        (name, muscleGroup, equipment, isArchived)
                    VALUES (?, ?, ?, 0)
                    """.trimIndent(),
                    arrayOf(exercise.name, exercise.muscleGroup.name, exercise.equipment.name),
                )
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // SQLite defaults foreign keys OFF. Room's generated open helper
            // already turns them on because entities here declare them, so this
            // is belt-and-braces: it keeps the guarantee explicit and local
            // rather than dependent on generated code staying that way.
            db.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitMasalaDatabase =
        Room.databaseBuilder(context, FitMasalaDatabase::class.java, FitMasalaDatabase.NAME)
            .addCallback(callback)
            // No fallbackToDestructiveMigration(), ever. See docs/DECISIONS.md.
            .build()

    @Provides fun provideMealDao(db: FitMasalaDatabase): MealDao = db.mealDao()
    @Provides fun provideRecipeDao(db: FitMasalaDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideWorkoutDao(db: FitMasalaDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideSessionDao(db: FitMasalaDatabase): SessionDao = db.sessionDao()
    @Provides fun providePlanDao(db: FitMasalaDatabase): PlanDao = db.planDao()
}
