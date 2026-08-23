package com.kevinjones.fitmasala.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One weigh-in / measurement. Every field except weight is optional, because the
 * daily ritual has to stay cheap — a form that demands a tape measure every
 * morning is a form nobody fills in, and the trend needs daily weights far more
 * than it needs weekly circumferences.
 */
@Entity(
    tableName = "body_metrics",
    indices = [Index(value = ["dayEpoch"], unique = true), Index("recordedAt")],
)
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Unique: one canonical reading per day. A re-weigh replaces, not appends. */
    val dayEpoch: Long,
    val recordedAt: Long,

    val weightKg: Double,

    // Tape measurements, cm. Present only on the days they were taken.
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,

    /** Resolved body fat, whatever the source. Null until derivable. */
    val bodyFatPercent: Double? = null,
    /** Stored as the enum name via Converters. */
    val bodyFatSource: String? = null,

    val notes: String? = null,
)

/**
 * The active cut. Only one row is current at a time; superseded goals stay for
 * history rather than being overwritten, so "what was I aiming at in March" has
 * an answer.
 */
@Entity(tableName = "plan_goals", indices = [Index("isActive"), Index("createdAt")])
data class PlanGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val goalBodyFatPercent: Double = 12.0,
    /** Stored as CutAggression.name. */
    val aggression: String,
    /** Stored as Sex.name and ActivityLevel.name. */
    val sex: String,
    val activityLevel: String,
    val heightCm: Double,
    val ageYears: Int,

    val startWeightKg: Double,
    val startBodyFatPercent: Double,
    val startedAtDayEpoch: Long,

    /**
     * The last computed target, cached.
     *
     * The engine is fast enough to recompute on every read, so this exists for a
     * different reason: it pins what today's number actually WAS. Without it,
     * opening yesterday's log after a weigh-in would silently redraw yesterday's
     * target, and a diary you can't trust to stay put is not a diary.
     */
    val cachedTargetCalories: Int? = null,
    val cachedProteinG: Int? = null,
    val cachedCarbsG: Int? = null,
    val cachedFatG: Int? = null,
    val cachedMaintenanceCalories: Int? = null,
    val cachedAtDayEpoch: Long? = null,

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
