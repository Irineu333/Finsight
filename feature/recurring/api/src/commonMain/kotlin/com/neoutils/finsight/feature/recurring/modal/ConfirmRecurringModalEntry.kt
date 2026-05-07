package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

interface ConfirmRecurringModalEntry {
    fun create(recurringId: Long, targetDate: LocalDate): ModalBottomSheet
}
