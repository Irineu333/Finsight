package com.neoutils.finsight.feature.recurring.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.modal.ConfirmRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringModal
import com.neoutils.finsight.core.domain.model.Recurring
import kotlinx.datetime.LocalDate

class ConfirmRecurringModalEntryImpl : ConfirmRecurringModalEntry {
    override fun create(recurring: Recurring, targetDate: LocalDate): ModalBottomSheet =
        ConfirmRecurringModal(recurring = recurring, targetDate = targetDate)
}