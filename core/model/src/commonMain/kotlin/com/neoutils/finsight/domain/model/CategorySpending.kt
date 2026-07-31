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
    /**
     * The share of the period's total, or `null` when there is no answer.
     *
     * Two ways there is none, and the second is the one that surprises: this category
     * could not be placed on the family's common scale, **or the whole itself is not
     * known**, because some *other* category of the period sits in a currency no rate
     * reaches. A share needs a denominator, and a denominator built from only the
     * measurable ones is not the total — the shares over it would add to 100% with a whole
     * category outside them.
     *
     * `null` rather than `0.0`, and the difference is the whole point — zero is an
     * assertion about the share, and a missing rate is the absence of one. A surface shows
     * a dash, or no bar, and says why (`MissingShareBadge`).
     */
    val percentage: Double?,
)
