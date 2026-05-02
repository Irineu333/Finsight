package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface RecurringFormModalEntry {
    fun create(recurring: Recurring? = null): ModalBottomSheet
}
