package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.model.OperationPerspective

interface ViewOperationModalEntry {
    fun create(
        operation: Operation,
        perspective: OperationPerspective? = null,
    ): ModalBottomSheet
}
