package com.neoutils.finsight.ui.modal.launchYield

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class LaunchYieldAction {
    data class SelectAccount(val account: Account) : LaunchYieldAction()
    data class DateChanged(val date: LocalDate) : LaunchYieldAction()
    data class Submit(val amount: Double) : LaunchYieldAction()
}
