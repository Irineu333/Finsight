package com.neoutils.finsight.feature.recurring.impl

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringModal
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import kotlinx.datetime.LocalDate

internal class RecurringEntryImpl : RecurringEntry {
    override fun recurringFormModal(recurring: Recurring?): Modal = RecurringFormModal(recurring)
    override fun viewRecurringModal(recurringId: Long): AdaptiveModal = ViewRecurringModal(recurringId)
    override fun confirmRecurringModal(recurring: Recurring, targetDate: LocalDate): Modal =
        ConfirmRecurringModal(recurring, targetDate)
}
