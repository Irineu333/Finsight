package com.neoutils.finsight.ui.modal.viewRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.component.ModalBottomSheet

class ViewRecurringModalEntryImpl : ViewRecurringModalEntry {
    override fun create(recurring: Recurring): ModalBottomSheet =
        ViewRecurringModal(recurring = recurring)
}
