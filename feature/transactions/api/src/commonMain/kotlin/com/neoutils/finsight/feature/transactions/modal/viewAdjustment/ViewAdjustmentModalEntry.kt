package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewAdjustmentModalEntry {
    fun create(operation: Operation): ModalBottomSheet
}
