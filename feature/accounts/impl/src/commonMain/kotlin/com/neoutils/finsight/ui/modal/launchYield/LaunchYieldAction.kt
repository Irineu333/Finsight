package com.neoutils.finsight.ui.modal.launchYield

import kotlinx.datetime.LocalDate

sealed class LaunchYieldAction {
    data class DateChanged(val date: LocalDate) : LaunchYieldAction()
    data class Submit(val amount: Double) : LaunchYieldAction()
}
