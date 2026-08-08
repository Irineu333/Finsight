package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion

data class TransferBetweenAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val destinationAccounts: List<Account> = emptyList(),
    val selectedSourceAccount: Account? = null,
    val selectedDestinationAccount: Account? = null,
    /** What the rate archive implies arrives, when it has anything to say (design D24). */
    val suggestion: CrossCurrencyAmountSuggestion? = null,
) {
    /**
     * Whether the two ends are denominated differently — the one condition that reveals
     * the second amount. It is derived from the accounts and never declared by the
     * screen, so the single-currency form stays identical to what it was.
     */
    val isCrossCurrency: Boolean
        get() = selectedSourceAccount != null &&
            selectedDestinationAccount != null &&
            selectedSourceAccount.currency != selectedDestinationAccount.currency
}
