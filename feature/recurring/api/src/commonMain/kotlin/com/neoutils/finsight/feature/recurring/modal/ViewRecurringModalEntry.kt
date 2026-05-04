package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.model.Recurring

interface ViewRecurringModalEntry {
    fun create(recurring: Recurring): ModalBottomSheet
}