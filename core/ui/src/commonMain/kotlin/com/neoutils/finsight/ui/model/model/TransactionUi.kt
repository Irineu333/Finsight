package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate

/**
 * A flat, display-ready view of an transaction for a list item. Carries no domain
 * graph — only resolved presentation values and the transaction id. Both display
 * axes are derived by the mapper (see `Transaction.toTransactionUi`): [label] is the
 * transaction's nature (color/title/icon), [direction] is the leg's direction under
 * the current perspective (the type text and the list filter).
 */
data class TransactionUi(
    val id: Long,
    val label: TransactionLabel,
    val direction: TransactionType,
    val title: String,
    val amount: Double,
    val date: LocalDate,
    val categoryId: Long?,
    val categoryIcon: CategoryLazyIcon?,
    val isCardTarget: Boolean,
    val isRecurring: Boolean,
    val installmentLabel: String?,
)
