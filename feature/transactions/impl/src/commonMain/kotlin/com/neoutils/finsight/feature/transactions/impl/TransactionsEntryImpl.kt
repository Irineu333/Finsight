package com.neoutils.finsight.feature.transactions.impl

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.model.OperationUi
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal

internal class TransactionsEntryImpl : TransactionsEntry {
    override fun viewOperationModal(operationUi: OperationUi): Modal = ViewOperationModal(operationUi)
    override fun viewOperationModal(operation: Operation, perspective: OperationPerspective?): Modal =
        ViewOperationModal(operation, perspective)
    override fun viewAdjustmentModal(operation: Operation): Modal = ViewAdjustmentModal(operation)
}
