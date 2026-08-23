package com.kevinjones.fitmasala.data.local

import com.kevinjones.fitmasala.data.local.entity.Equipment
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.MuscleGroup

/**
 * Starter catalogue, inserted once on database creation.
 *
 * Seeded with IGNORE on a unique `name` index, so re-running it is a no-op and
 * can never renumber an id that logged sets already point at. Adding to this
 * list in a later version is safe for the same reason.
 */
object ExerciseSeed {

    val exercises: List<ExerciseEntity> = listOf(
        // Push
        seed("Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL),
        seed("Incline Dumbbell Press", MuscleGroup.CHEST, Equipment.DUMBBELL),
        seed("Cable Fly", MuscleGroup.CHEST, Equipment.CABLE),
        seed("Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL),
        seed("Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
        seed("Triceps Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE),
        seed("Overhead Triceps Extension", MuscleGroup.TRICEPS, Equipment.DUMBBELL),
        seed("Dips", MuscleGroup.TRICEPS, Equipment.BODYWEIGHT),

        // Pull
        seed("Deadlift", MuscleGroup.BACK, Equipment.BARBELL),
        seed("Barbell Row", MuscleGroup.BACK, Equipment.BARBELL),
        seed("Pull-Up", MuscleGroup.BACK, Equipment.BODYWEIGHT),
        seed("Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE),
        seed("Seated Cable Row", MuscleGroup.BACK, Equipment.CABLE),
        seed("Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE),
        seed("Barbell Curl", MuscleGroup.BICEPS, Equipment.BARBELL),
        seed("Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL),

        // Legs
        seed("Back Squat", MuscleGroup.QUADS, Equipment.BARBELL),
        seed("Front Squat", MuscleGroup.QUADS, Equipment.BARBELL),
        seed("Leg Press", MuscleGroup.QUADS, Equipment.MACHINE),
        seed("Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL),
        seed("Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE),
        seed("Hip Thrust", MuscleGroup.GLUTES, Equipment.BARBELL),
        seed("Walking Lunge", MuscleGroup.GLUTES, Equipment.DUMBBELL),
        seed("Standing Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE),

        // Core and conditioning
        seed("Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT),
        seed("Hanging Leg Raise", MuscleGroup.CORE, Equipment.BODYWEIGHT),
        seed("Cable Crunch", MuscleGroup.CORE, Equipment.CABLE),
        seed("Kettlebell Swing", MuscleGroup.FULL_BODY, Equipment.KETTLEBELL),
        seed("Burpee", MuscleGroup.FULL_BODY, Equipment.BODYWEIGHT),

        // Yoga / mobility — the module covers these too, and they need to be
        // loggable without weight for the set row to make sense.
        seed("Surya Namaskar", MuscleGroup.FULL_BODY, Equipment.MAT),
        seed("Adho Mukha Svanasana", MuscleGroup.MOBILITY, Equipment.MAT),
        seed("Bhujangasana", MuscleGroup.MOBILITY, Equipment.MAT),
        seed("Malasana Hold", MuscleGroup.MOBILITY, Equipment.MAT),
    )

    private fun seed(name: String, group: MuscleGroup, equipment: Equipment) =
        ExerciseEntity(
            name = name,
            muscleGroup = group,
            equipment = equipment,
        )
}
