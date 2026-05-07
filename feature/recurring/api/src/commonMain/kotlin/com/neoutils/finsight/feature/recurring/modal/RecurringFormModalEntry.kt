package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface RecurringFormModalEntry {
    fun create(recurringId: Long? = null): ModalBottomSheet
}
