package com.neoutils.finsight.feature.transactions.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface ViewAdjustmentModalEntry {
    fun create(operationId: Long): ModalBottomSheet
}
