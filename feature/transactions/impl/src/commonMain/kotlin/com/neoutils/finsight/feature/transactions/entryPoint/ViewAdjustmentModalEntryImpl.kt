package com.neoutils.finsight.feature.transactions.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.transactions.modal.ViewAdjustmentModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.feature.transactions.model.Operation

class ViewAdjustmentModalEntryImpl : ViewAdjustmentModalEntry {
    override fun create(operation: Operation): ModalBottomSheet =
        ViewAdjustmentModal(operation = operation)
}