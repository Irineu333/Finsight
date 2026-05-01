package com.neoutils.finsight.ui.modal.editAccountBalance

import com.neoutils.finsight.domain.model.Account

sealed class EditAccountBalanceAction {
    data class SelectAccount(val account: Account) : EditAccountBalanceAction()
    data class Submit(val targetBalance: Double) : EditAccountBalanceAction()
}
