package com.neoutils.finsight.domain.model

import kotlinx.serialization.Serializable

/**
 * The direction the user picks when entering a transaction, and the direction a
 * leg reads as once derived from the ledger.
 *
 * Retained as **input vocabulary** (design D4): it is a choice the user makes,
 * and `Recurring` persists it. It is no longer a property of a ledger leg — the
 * entry signs carry that.
 *
 * ⚠️ The constant names are published wire format: analytics emits
 * `type.name.lowercase()` to Firebase and the navigation `NavType` serializes by
 * `name`/`valueOf`. Do not rename them.
 */
@Serializable
enum class TransactionType {
    EXPENSE,
    INCOME,
    ADJUSTMENT;

    val isExpense: Boolean get() = this == EXPENSE
    val isIncome: Boolean get() = this == INCOME
    val isAdjustment: Boolean get() = this == ADJUSTMENT
}
