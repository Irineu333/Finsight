package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class RecurringFormModalEntryImpl : RecurringFormModalEntry {
    override fun create(recurring: Recurring?): ModalBottomSheet =
        RecurringFormModal(recurring = recurring)
}
