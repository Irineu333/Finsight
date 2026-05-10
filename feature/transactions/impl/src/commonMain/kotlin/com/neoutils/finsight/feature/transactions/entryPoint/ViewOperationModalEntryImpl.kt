package com.neoutils.finsight.feature.transactions.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.transactions.modal.ViewOperationModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.feature.transactions.model.OperationPerspective

class ViewOperationModalEntryImpl : ViewOperationModalEntry {
    override fun create(
        operationId: Long,
        perspective: OperationPerspective,
    ): ModalBottomSheet = ViewOperationModal(
        operationId = operationId,
        perspective = perspective,
    )
}
