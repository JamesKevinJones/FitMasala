package com.kevinjones.fitmasala.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.ExerciseSetEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeIngredientEntity
import com.kevinjones.fitmasala.data.local.entity.RoutineExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutRoutineEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutSessionEntity

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredientEntity>,
)

/** A routine slot joined to the exercise it points at. */
data class RoutineExerciseWithExercise(
    @Embedded val slot: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class RoutineWithExercises(
    @Embedded val routine: WorkoutRoutineEntity,
    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val exercises: List<RoutineExerciseWithExercise>,
)

data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<ExerciseSetEntity>,
)

/** A set joined to its exercise — what the active-session list renders. */
data class SetWithExercise(
    @Embedded val set: ExerciseSetEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)
