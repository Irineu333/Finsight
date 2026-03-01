package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring

sealed class RecurringAction {
    data object Add : RecurringAction()
    data class Edit(val recurring: Recurring) : RecurringAction()
}
