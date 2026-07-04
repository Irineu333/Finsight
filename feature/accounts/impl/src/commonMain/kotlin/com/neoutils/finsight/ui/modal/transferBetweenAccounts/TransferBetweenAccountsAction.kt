package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class TransferBetweenAccountsAction {
    data class SelectSourceAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class SelectDestinationAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class Submit(
        val amount: Double,
        val date: LocalDate,
    ) : TransferBetweenAccountsAction()
}
