package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
interface ViewOperationModalEntry {
    fun create(
        operation: Operation,
        perspective: OperationPerspective? = null,
    ): ModalBottomSheet
}
