package com.neoutils.finsight.feature.transactions.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.transactions.modal.ViewAdjustmentModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewAdjustment.ViewAdjustmentModal

class ViewAdjustmentModalEntryImpl : ViewAdjustmentModalEntry {
    override fun create(operationId: Long): ModalBottomSheet =
        ViewAdjustmentModal(operationId = operationId)
}
