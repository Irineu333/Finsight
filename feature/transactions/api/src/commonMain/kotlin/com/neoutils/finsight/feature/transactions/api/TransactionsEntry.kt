package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationPerspective

interface TransactionsEntry {
    fun addTransactionModal(): Modal
    fun viewOperationModal(operationId: Long, perspective: OperationPerspective? = null): AdaptiveModal
    fun viewAdjustmentModal(operationId: Long): AdaptiveModal
}
