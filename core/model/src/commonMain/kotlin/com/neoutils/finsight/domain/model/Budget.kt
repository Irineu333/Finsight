package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Budget(
    val id: Long = 0,
    val title: String,
    val categories: List<Category>,
    val iconKey: String,
    val amount: Double,
    /**
     * The currency [amount] is denominated in — no default, deliberately, so the
     * compiler makes somebody decide (design D13).
     *
     * A category is a dimension, not an account: it has no currency of its own, and its
     * entries may sit in several. So a budget's limit cannot derive its denomination
     * from what it measures; it is **declared**, once, from the accounts the user
     * actually transacts in, and never inherited from a preference that can move.
     */
    val currency: String,
    val limitType: LimitType = LimitType.FIXED,
    val percentage: Double? = null,
    val recurringId: Long? = null,
    val createdAt: Long,
) {
    val icon: CategoryLazyIcon
        get() = CategoryLazyIcon(iconKey)
}
