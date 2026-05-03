package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.RecurringFormModalEntry
import com.neoutils.finsight.feature.recurring.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.core.domain.model.Recurring

class RecurringFormModalEntryImpl : RecurringFormModalEntry {
    override fun create(recurring: Recurring?): ModalBottomSheet =
        RecurringFormModal(recurring = recurring)
}