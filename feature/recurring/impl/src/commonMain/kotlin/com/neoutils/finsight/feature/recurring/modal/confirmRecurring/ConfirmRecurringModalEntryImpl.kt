package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

class ConfirmRecurringModalEntryImpl : ConfirmRecurringModalEntry {
    override fun create(recurring: Recurring, targetDate: LocalDate): ModalBottomSheet =
        ConfirmRecurringModal(recurring = recurring, targetDate = targetDate)
}
