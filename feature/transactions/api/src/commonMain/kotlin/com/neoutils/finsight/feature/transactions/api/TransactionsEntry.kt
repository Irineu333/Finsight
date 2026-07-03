package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationUi

interface TransactionsEntry {
    fun viewOperationModal(operationUi: OperationUi): Modal
    fun viewAdjustmentModal(operation: Operation): Modal
}
