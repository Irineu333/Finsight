@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = DimensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["dimensionId"],
            onDelete = ForeignKey.NO_ACTION
        ),
    ],
    indices = [
        Index(value = ["dimensionId"]),
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val type: Type,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // The category's own dimension, emitted with the category itself, so it always
    // exists: a facade with no dimension could not be spent on. It replaces the
    // chart-of-accounts row a category used to own — a category is an analytic axis
    // of the ledger, not a line of it (design D4).
    val dimensionId: Long = 0,
    // Closure of a category lives here and nowhere else. Every other facade reads it
    // from its account; a category has none, so there is no copy to diverge from —
    // the facade is the single owner (spec `account-lifecycle`).
    val isArchived: Boolean = false,
    // The key under which the app finds a category it provides — never its name.
    // That is the whole point: the user may rename it to "CDI" and change its icon,
    // and the lookup, the classification and the reads keep working (design D3).
    // `null` for every category the user created, which is almost all of them.
    val systemKey: String? = null,
) {
    enum class Type {
        INCOME,
        EXPENSE
    }
}
