package com.neoutils.finsight.feature.transactions.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.domain.model.Operation

interface ViewAdjustmentModalEntry {
    fun create(operation: Operation): ModalBottomSheet
}