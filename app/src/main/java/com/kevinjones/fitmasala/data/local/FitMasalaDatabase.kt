package com.kevinjones.fitmasala.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kevinjones.fitmasala.data.local.converter.Converters
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.dao.PlanDao
import com.kevinjones.fitmasala.data.local.dao.RecipeDao
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.dao.WorkoutDao
import com.kevinjones.fitmasala.data.local.entity.BodyMetricEntity
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.ExerciseSetEntity
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.PlanGoalEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeIngredientEntity
import com.kevinjones.fitmasala.data.local.entity.RoutineExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutRoutineEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutSessionEntity

/**
 * `exportSchema = true` writes the schema JSON to `app/schemas/`, which is
 * committed. Every version bump gets a real Migration — there is deliberately no
 * `fallbackToDestructiveMigration()` anywhere in this project, because a
 * destructive migration during development silently deletes months of training
 * history and logged meals, which is the entire value of the app.
 */
@Database(
    entities = [
        LoggedMealEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ExerciseEntity::class,
        WorkoutRoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        ExerciseSetEntity::class,
        BodyMetricEntity::class,
        PlanGoalEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FitMasalaDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun planDao(): PlanDao

    companion object {
        const val NAME = "fitmasala.db"
    }
}
