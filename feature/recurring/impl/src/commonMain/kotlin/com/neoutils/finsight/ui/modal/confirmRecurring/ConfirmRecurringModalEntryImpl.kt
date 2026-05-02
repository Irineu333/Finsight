package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

class ConfirmRecurringModalEntryImpl : ConfirmRecurringModalEntry {
    override fun create(recurring: Recurring, targetDate: LocalDate): ModalBottomSheet =
        ConfirmRecurringModal(recurring = recurring, targetDate = targetDate)
}
