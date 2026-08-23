package com.kevinjones.fitmasala.data.local.entity

/**
 * Stored as their `name` string via Converters, not as ordinals. Ordinals are a
 * trap: reordering an enum silently rewrites the meaning of every existing row.
 */

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Culinary regions, not "Indian". The whole point of the AI chef is that a
 * Bengali shorshe ilish and a Chettinad kozhi are not the same food, and a
 * single "curry" bucket is exactly the Westernisation the app exists to avoid.
 */
enum class Region {
    PUNJABI,
    BENGALI,
    GUJARATI,
    MAHARASHTRIAN,
    RAJASTHANI,
    SOUTH_INDIAN,
    CHETTINAD,
    HYDERABADI,
    AWADHI,
    GOAN,
    KASHMIRI,
    ODIA,
    ASSAMESE,
    SINDHI,
    OTHER,
}

/** How the dish was cooked — drives the oil/ghee absorption correction. */
enum class CookingMethod {
    DEEP_FRIED,
    SHALLOW_FRIED,
    TADKA,
    DRY_ROASTED,
    STEAMED,
    BOILED,
    PRESSURE_COOKED,
    TANDOOR,
    RAW,
    OTHER,
}

/** Indian portion units. Grams exist too, but nobody weighs a katori of dal. */
enum class PortionUnit {
    KATORI,
    ROTI,
    PIECE,
    PLATE,
    GLASS,
    TABLESPOON,
    GRAMS,
    MILLILITRES,
    SERVING,
}

enum class RoutineType { PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY, YOGA, HIIT, CARDIO, CUSTOM }

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS,
    QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY, MOBILITY,
}

enum class Equipment { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, KETTLEBELL, BAND, MAT, OTHER }

/**
 * How a meal's numbers were produced. Drives what the UI is allowed to claim:
 * a PHOTO estimate and a weighed MANUAL entry must never render identically,
 * because the plan's credibility depends on knowing which is which.
 */
enum class MealSource { MANUAL, AI_CHAT, PHOTO, REPEATED }
