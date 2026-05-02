package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class ViewAdjustmentModalEntryImpl : ViewAdjustmentModalEntry {
    override fun create(operation: Operation): ModalBottomSheet =
        ViewAdjustmentModal(operation = operation)
}
