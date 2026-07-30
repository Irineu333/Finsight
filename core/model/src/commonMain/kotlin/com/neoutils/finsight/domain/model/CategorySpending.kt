package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.ConsolidatedAmount

/**
 * What a category cost in a period.
 *
 * [amount] is a **consolidated figure**, not a number: a category is a dimension and
 * not an account, so it has no currency of its own and its entries may sit in several
 * (design D13). Reducing them is the consolidation layer's job and nobody else's,
 * which is why what arrives here is already the figure a screen shows.
 */
data class CategorySpending(
    val category: Category,
    val amount: ConsolidatedAmount,
    val percentage: Double
)
