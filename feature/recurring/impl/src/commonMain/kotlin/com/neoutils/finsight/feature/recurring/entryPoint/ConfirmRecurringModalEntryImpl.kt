package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.ConfirmRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringModal
import kotlinx.datetime.LocalDate

class ConfirmRecurringModalEntryImpl : ConfirmRecurringModalEntry {
    override fun create(recurringId: Long, targetDate: LocalDate): ModalBottomSheet =
        ConfirmRecurringModal(recurringId = recurringId, targetDate = targetDate)
}
