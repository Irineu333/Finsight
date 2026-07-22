package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: CategoryLazyIcon,
    // Primary state, not a derivation: "this is an expense category" is the user's
    // declaration at creation time, and nothing in the ledger produces it. It used
    // to be *encoded* as the type of the category's own chart account, which made it
    // look derived; with the account gone (design D4) the state simply moved home.
    val type: Type,
    val createdAt: Long,
    val isArchived: Boolean = false,
    // The ledger dimension this category classifies entries with. Assigned by the
    // store on insert, exactly like [id]: a persisted category always has its
    // dimension.
    val dimensionId: Long = 0,
) {
    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean get() = this == INCOME
        val isExpense: Boolean get() = this == EXPENSE
    }
}
