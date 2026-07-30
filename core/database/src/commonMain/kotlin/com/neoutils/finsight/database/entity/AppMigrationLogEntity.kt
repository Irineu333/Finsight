package com.neoutils.finsight.database.entity

import androidx.room.Entity

/**
 * The one-off app steps that have already run — steps the schema version cannot express,
 * because they depend on something outside the database: a device locale, a resolved
 * preference, a decision the user has made.
 *
 * It lives **in the database** rather than in the settings store, and that is the whole
 * reason it exists. A step that rewrites rows has to record that it ran in the same
 * transaction that rewrote them; a flag kept outside cannot be rolled back with them, so a
 * crash between the two would leave the work done and the app believing it never happened.
 *
 * [step] is the primary key, so a second run cannot insert a second row even if two
 * executions race: the write itself is the claim.
 */
@Entity(tableName = "app_migration_log", primaryKeys = ["step"])
data class AppMigrationLogEntity(
    val step: String,
    val migratedAt: Long,
)
