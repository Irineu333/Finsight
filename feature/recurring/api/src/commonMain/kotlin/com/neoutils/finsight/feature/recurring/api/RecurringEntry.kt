package com.neoutils.finsight.feature.recurring.api

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import kotlinx.datetime.LocalDate

interface RecurringEntry {
    fun recurringFormModal(recurring: Recurring? = null): Modal
    fun viewRecurringModal(recurring: Recurring): AdaptiveModal
    fun confirmRecurringModal(recurring: Recurring, targetDate: LocalDate): Modal
}
