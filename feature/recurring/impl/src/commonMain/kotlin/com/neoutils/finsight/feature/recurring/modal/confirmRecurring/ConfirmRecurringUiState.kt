package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.recurring.model.form.RecurringConfirmForm
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class ConfirmRecurringUiState {

    data object Loading : ConfirmRecurringUiState()

    data object Error : ConfirmRecurringUiState()

    data class Content(
        val form: RecurringConfirmForm,
        val accounts: List<Account>,
        val creditCards: List<CreditCard>,
        val invoices: List<Invoice>,
    ) : ConfirmRecurringUiState() {
        val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }
}
