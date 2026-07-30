package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion

data class PayInvoiceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    /** What is owed, and the currency of the card that owes it — a fact, never a choice. */
    val invoiceAmount: Double = 0.0,
    val invoiceCurrency: String? = null,
    /** What the rate archive implies leaves the account, when it has anything to say. */
    val suggestion: CrossCurrencyAmountSuggestion? = null,
) {
    /**
     * Whether the paying account is denominated differently from the card. Derived from
     * the two, so a user who never holds two currencies meets the form he always met.
     */
    val isCrossCurrency: Boolean
        get() = invoiceCurrency != null &&
            selectedAccount != null &&
            selectedAccount.currency != invoiceCurrency
}
