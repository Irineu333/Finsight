package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.component.ModalBottomSheet

interface RecurringFormModalEntry {
    fun create(recurring: Recurring? = null): ModalBottomSheet
}
