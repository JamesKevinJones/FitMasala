package com.kevinjones.fitmasala.data.local.converter

import androidx.room.TypeConverter
import com.kevinjones.fitmasala.data.local.entity.CookingMethod
import com.kevinjones.fitmasala.data.local.entity.Equipment
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.MuscleGroup
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.local.entity.RoutineType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Enums go in as their `name`, never their ordinal — reordering an enum would
 * otherwise silently rewrite every existing row.
 *
 * Unknown values decode to a defined fallback rather than throwing. A row
 * written by a newer build must not be able to crash an older one on read; that
 * turns a cosmetic problem into an unopenable database.
 */
object Converters {

    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    // --- String lists (recipe steps, technique notes) ---

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        json.encodeToString(stringListSerializer, value.orEmpty())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(stringListSerializer, value) }
            .getOrDefault(emptyList())
    }

    // --- Enums ---

    @TypeConverter fun fromMealType(v: MealType): String = v.name
    @TypeConverter fun toMealType(v: String): MealType =
        enumValueOrDefault(v, MealType.SNACK)

    @TypeConverter fun fromMealSource(v: MealSource): String = v.name
    @TypeConverter fun toMealSource(v: String): MealSource =
        enumValueOrDefault(v, MealSource.MANUAL)

    @TypeConverter fun fromRegion(v: Region): String = v.name
    @TypeConverter fun toRegion(v: String): Region =
        enumValueOrDefault(v, Region.OTHER)

    @TypeConverter fun fromCookingMethod(v: CookingMethod): String = v.name
    @TypeConverter fun toCookingMethod(v: String): CookingMethod =
        enumValueOrDefault(v, CookingMethod.OTHER)

    @TypeConverter fun fromPortionUnit(v: PortionUnit): String = v.name
    @TypeConverter fun toPortionUnit(v: String): PortionUnit =
        enumValueOrDefault(v, PortionUnit.SERVING)

    @TypeConverter fun fromRoutineType(v: RoutineType): String = v.name
    @TypeConverter fun toRoutineType(v: String): RoutineType =
        enumValueOrDefault(v, RoutineType.CUSTOM)

    @TypeConverter fun fromMuscleGroup(v: MuscleGroup): String = v.name
    @TypeConverter fun toMuscleGroup(v: String): MuscleGroup =
        enumValueOrDefault(v, MuscleGroup.FULL_BODY)

    @TypeConverter fun fromEquipment(v: Equipment): String = v.name
    @TypeConverter fun toEquipment(v: String): Equipment =
        enumValueOrDefault(v, Equipment.OTHER)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
