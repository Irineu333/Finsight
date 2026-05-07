package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.ViewRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.viewRecurring.ViewRecurringModal

class ViewRecurringModalEntryImpl : ViewRecurringModalEntry {
    override fun create(recurringId: Long): ModalBottomSheet =
        ViewRecurringModal(recurringId = recurringId)
}
