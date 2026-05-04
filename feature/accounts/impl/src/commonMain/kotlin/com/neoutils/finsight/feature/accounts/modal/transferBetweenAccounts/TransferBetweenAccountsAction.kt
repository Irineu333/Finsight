package com.neoutils.finsight.feature.accounts.modal.transferBetweenAccounts

import com.neoutils.finsight.feature.accounts.model.Account
import kotlinx.datetime.LocalDate

sealed class TransferBetweenAccountsAction {
    data class SelectSourceAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class SelectDestinationAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class Submit(
        val amount: Double,
        val date: LocalDate,
    ) : TransferBetweenAccountsAction()
}
