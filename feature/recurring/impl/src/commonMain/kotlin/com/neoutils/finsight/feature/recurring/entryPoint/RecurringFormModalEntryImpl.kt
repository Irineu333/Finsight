package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.RecurringFormModalEntry
import com.neoutils.finsight.feature.recurring.modal.recurringForm.RecurringFormModal

class RecurringFormModalEntryImpl : RecurringFormModalEntry {
    override fun create(recurringId: Long?): ModalBottomSheet =
        RecurringFormModal(recurringId = recurringId)
}
