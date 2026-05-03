package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.domain.model.Recurring

interface ViewRecurringModalEntry {
    fun create(recurring: Recurring): ModalBottomSheet
}