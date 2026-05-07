package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface ViewRecurringModalEntry {
    fun create(recurringId: Long): ModalBottomSheet
}
