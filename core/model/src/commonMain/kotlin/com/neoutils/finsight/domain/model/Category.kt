package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: CategoryLazyIcon,
    val type: Type,
    val createdAt: Long,
    // The chart-of-accounts row (INCOME/EXPENSE) this category projects onto.
    // Assigned by the store on insert, exactly like [id]: a persisted category
    // always has its account.
    val accountId: Long = 0,
    // Mirrors the closure of its ledger account (D21); the category keeps no copy
    // of its own. Set only on the reads that render history.
    val isClosed: Boolean = false,
) {
    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
