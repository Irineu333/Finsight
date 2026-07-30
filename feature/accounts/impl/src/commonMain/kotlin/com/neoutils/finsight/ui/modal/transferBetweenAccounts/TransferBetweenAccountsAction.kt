package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

sealed class TransferBetweenAccountsAction {
    data class SelectSourceAccount(val account: Account?) : TransferBetweenAccountsAction()
    data class SelectDestinationAccount(val account: Account?) : TransferBetweenAccountsAction()

    /**
     * What is leaving, and the day it leaves — the two facts the rate archive needs in
     * order to say what arrives. The arithmetic never happens on the screen: the form
     * states, the consolidation layer answers.
     */
    data class ChangeAmount(val amount: Double) : TransferBetweenAccountsAction()
    data class ChangeDate(val date: LocalDate) : TransferBetweenAccountsAction()

    data class Submit(
        val amount: Double,
        /**
         * What arrives, when the two ends are denominated differently. A same-currency
         * transfer cannot state two numbers, and the view model drops it there.
         */
        val destinationAmount: Double,
        val date: LocalDate,
    ) : TransferBetweenAccountsAction()
}
