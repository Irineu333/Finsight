package com.neoutils.finsight.feature.accounts.modal.editAccountBalance

import com.neoutils.finsight.feature.accounts.model.Account

sealed class EditAccountBalanceAction {
    data class SelectAccount(val account: Account) : EditAccountBalanceAction()
    data class Submit(val targetBalance: Double) : EditAccountBalanceAction()
}
