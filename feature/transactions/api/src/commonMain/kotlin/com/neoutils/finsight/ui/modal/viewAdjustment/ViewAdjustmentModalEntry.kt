package com.neoutils.finsight.ui.modal.viewAdjustment

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewAdjustmentModalEntry {
    fun create(operation: Operation): ModalBottomSheet
}
