@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// A budget's categories live in the `budget_categories` M2M table. `categoryId`
// used to duplicate the first of them, write-only, under a CASCADE — so deleting
// the category that happened to be listed first destroyed the whole budget, even
// with the others still alive.
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val iconCategoryId: Long,
    val iconKey: String,
    val title: String,
    val amount: Double,
    // The currency the limit is stated in — the budget's own, chosen when it is created
    // and never the base. A user with everything in dollars would otherwise be asked for a
    // limit in reais and shown a progress bar comparing reais with dollars — paying the price
    // of multi-currency through the *entry* rather than through the reading.
    val currency: String,
    val period: String,
    val limitType: String = "FIXED",
    val percentage: Double? = null,
    val recurringId: Long? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
)
