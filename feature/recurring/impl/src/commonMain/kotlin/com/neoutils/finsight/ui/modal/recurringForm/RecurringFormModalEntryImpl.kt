package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.component.ModalBottomSheet

class RecurringFormModalEntryImpl : RecurringFormModalEntry {
    override fun create(recurring: Recurring?): ModalBottomSheet =
        RecurringFormModal(recurring = recurring)
}
