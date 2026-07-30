package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon

data class Budget(
    val id: Long = 0,
    val title: String,
    val categories: List<Category>,
    val iconKey: String,
    val amount: Double,
    /**
     * The currency [amount] is stated in — chosen once, when the budget is created, and never
     * the base. The base answers *in what currency this user reads totals*; it has nothing to
     * say about a number the user typed, and a limit that changed meaning the day a second
     * currency appeared would be exactly the silent restatement design D12 and D17 refuse.
     * Changing it is creating another budget.
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
