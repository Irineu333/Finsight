package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    /** The currency the card is denominated in — the one the ceiling is expressed in. */
    val cardCurrency: String? = null,
    /** What the rate archive implies leaves the account, when it has anything to say. */
    val suggestion: CrossCurrencyAmountSuggestion? = null,
) {
    val isCrossCurrency: Boolean
        get() = cardCurrency != null &&
            selectedAccount != null &&
            selectedAccount.currency != cardCurrency
}
