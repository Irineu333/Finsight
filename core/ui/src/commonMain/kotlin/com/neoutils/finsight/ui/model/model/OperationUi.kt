package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.OperationLabel
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate

/**
 * A flat, display-ready view of an operation for a list item. Carries no domain
 * graph — only resolved presentation values and the operation id. Both display
 * axes are derived by the mapper (see `Operation.toOperationUi`): [label] is the
 * operation's nature (color/title/icon), [direction] is the leg's direction under
 * the current perspective (the type text and the list filter).
 */
data class OperationUi(
    val id: Long,
    val label: OperationLabel,
    val direction: Transaction.Type,
    val title: String,
    val amount: Double,
    val date: LocalDate,
    val categoryId: Long?,
    val categoryIcon: CategoryLazyIcon?,
    val isCardTarget: Boolean,
    val isRecurring: Boolean,
    val installmentLabel: String?,
)
