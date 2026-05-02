package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewRecurringModalEntry {
    fun create(recurring: Recurring): ModalBottomSheet
}
