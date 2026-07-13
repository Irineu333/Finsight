package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.model.OperationUi

interface TransactionsEntry {
    fun addTransactionModal(): Modal
    fun viewOperationModal(operationUi: OperationUi): Modal
    fun viewOperationModal(operation: Operation, perspective: OperationPerspective? = null): Modal
    fun viewAdjustmentModal(operation: Operation): Modal
}
