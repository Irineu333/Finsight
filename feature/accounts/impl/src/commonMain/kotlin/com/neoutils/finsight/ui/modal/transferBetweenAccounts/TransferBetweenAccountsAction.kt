package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class TransferBetweenAccountsAction {
    data class SelectSourceAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class SelectDestinationAccount(val account: Account?) : TransferBetweenAccountsAction()

    /**
     * What is in the first field and in the date field, as they are typed. They reach the
     * ViewModel before submission because the suggested second amount depends on both, and
     * deciding which quote governs an operation is not the form's to make.
     */
    data class SourceAmountChanged(val amount: Double) : TransferBetweenAccountsAction()
    data class DateChanged(val date: LocalDate) : TransferBetweenAccountsAction()

    data class Submit(
        val sourceAmount: Double,
        // No default: the amount that arrives is a second decision, and a transfer that
        // crosses currencies must not be writable by omitting it.
        val destinationAmount: Double,
        val date: LocalDate,
    ) : TransferBetweenAccountsAction()
}
