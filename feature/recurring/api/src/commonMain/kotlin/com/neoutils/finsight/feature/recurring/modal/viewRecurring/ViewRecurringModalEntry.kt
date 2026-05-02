package com.neoutils.finsight.feature.recurring.modal.viewRecurring

import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewRecurringModalEntry {
    fun create(recurring: Recurring): ModalBottomSheet
}
