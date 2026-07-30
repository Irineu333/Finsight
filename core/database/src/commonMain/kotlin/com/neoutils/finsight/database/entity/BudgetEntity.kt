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
    // What [amount] is denominated in, chosen once when the budget is created and never
    // rewritten (design D13). It is not the base currency: the base answers *in which
    // currency the user reads totals*, not *in which one he spends*, and it can change
    // — reinterpreting a stored limit would silently rewrite the meaning of a number
    // the user typed.
    val currency: String,
    val period: String,
    val limitType: String = "FIXED",
    val percentage: Double? = null,
    val recurringId: Long? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
)
