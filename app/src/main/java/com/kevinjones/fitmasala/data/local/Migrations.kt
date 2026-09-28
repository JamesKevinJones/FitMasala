package com.kevinjones.fitmasala.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step, in order. Each one keeps every row: the meal and workout
 * history is the point of the app, so there is no destructive fallback.
 */
object Migrations {

    /**
     * v2: `logged_meals.estimateModel`, the model that produced an Estimate.
     * Existing rows get NULL - "logged before this was recorded".
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `logged_meals` ADD COLUMN `estimateModel` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
