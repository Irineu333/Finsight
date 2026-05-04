package com.neoutils.finsight.feature.recurring.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.recurring.model.Recurring
import kotlinx.datetime.LocalDate

interface ConfirmRecurringModalEntry {
    fun create(recurring: Recurring, targetDate: LocalDate): ModalBottomSheet
}