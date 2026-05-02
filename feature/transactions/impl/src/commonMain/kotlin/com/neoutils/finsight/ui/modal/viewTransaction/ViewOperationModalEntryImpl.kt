package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.model.OperationPerspective

class ViewOperationModalEntryImpl : ViewOperationModalEntry {
    override fun create(
        operation: Operation,
        perspective: OperationPerspective?,
    ): ModalBottomSheet = ViewOperationModal(
        operation = operation,
        perspective = perspective,
    )
}
