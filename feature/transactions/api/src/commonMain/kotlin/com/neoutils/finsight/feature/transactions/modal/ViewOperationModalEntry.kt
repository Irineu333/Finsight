package com.neoutils.finsight.feature.transactions.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.OperationPerspective

interface ViewOperationModalEntry {
    fun create(
        operation: Operation,
        perspective: OperationPerspective? = null,
    ): ModalBottomSheet
}