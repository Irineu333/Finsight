package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

interface ConfirmRecurringModalEntry {
    fun create(recurring: Recurring, targetDate: LocalDate): ModalBottomSheet
}
