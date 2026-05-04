package com.neoutils.finsight.feature.transactions.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.transactions.modal.ViewOperationModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.OperationPerspective

class ViewOperationModalEntryImpl : ViewOperationModalEntry {
    override fun create(
        operation: Operation,
        perspective: OperationPerspective?,
    ): ModalBottomSheet = ViewOperationModal(
        operation = operation,
        perspective = perspective,
    )
}