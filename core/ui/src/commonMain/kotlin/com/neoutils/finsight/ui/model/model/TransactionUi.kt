package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate

/**
 * A flat, display-ready view of a transaction for a list item. Carries no domain
 * graph — only resolved presentation values and the transaction id. Both display
 * axes are derived by the mapper (see `Transaction.toTransactionUi`): [label] is the
 * transaction's nature (color/title/icon), [direction] is the leg's direction under
 * the current perspective (the type text and the list filter).
 *
 * [amount] arrives with its sign policy already resolved (see [itemDisplayAmount]), so
 * a component renders it without branching on label, nature or direction to decide
 * what to show.
 */
data class TransactionUi(
    val id: Long,
    val label: TransactionLabel,
    val direction: TransactionType,
    val title: String,
    val amount: DisplayAmount,
    val date: LocalDate,
    val categoryId: Long?,
    /**
     * Whether this item is *unclassified* — the answer the domain gave, not a fact to be
     * rebuilt from [categoryId].
     *
     * A null [categoryId] cannot stand in for it: it is also null for a transfer, an
     * invoice payment and an adjustment, which have no analytic axis at all, and for a
     * dimension that resolves to no category, which is an integrity failure rather than an
     * absence of classification. Carried flat for the same reason as [isCardTarget] — the
     * DTO has no domain to ask.
     */
    val isUncategorized: Boolean = false,
    val categoryIcon: CategoryLazyIcon?,
    // An archived category still labels its history, but its icon reads muted — the
    // same rule as `Category.displayColor`. Carried flat because the DTO has no domain.
    val isCategoryArchived: Boolean = false,
    val isCardTarget: Boolean,
    val isRecurring: Boolean,
    val installmentLabel: String?,
)
