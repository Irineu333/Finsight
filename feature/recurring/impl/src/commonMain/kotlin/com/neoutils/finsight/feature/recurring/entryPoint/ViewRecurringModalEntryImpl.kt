package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.ViewRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.feature.recurring.model.Recurring

class ViewRecurringModalEntryImpl : ViewRecurringModalEntry {
    override fun create(recurring: Recurring): ModalBottomSheet =
        ViewRecurringModal(recurring = recurring)
}