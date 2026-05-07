package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate

sealed class ConfirmRecurringUiState {

    data object Loading : ConfirmRecurringUiState()

    data class Content(
        val recurring: Recurring,
        val confirmDate: LocalDate,
        val selectedTarget: Transaction.Target,
        val accounts: List<Account>,
        val selectedAccount: Account?,
        val creditCards: List<CreditCard>,
        val selectedCreditCard: CreditCard?,
        val invoices: List<Invoice>,
        val selectedInvoice: Invoice?,
    ) : ConfirmRecurringUiState() {
        val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }
}
