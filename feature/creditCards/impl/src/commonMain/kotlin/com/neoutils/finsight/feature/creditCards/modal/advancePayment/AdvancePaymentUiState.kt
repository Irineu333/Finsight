package com.neoutils.finsight.feature.creditCards.modal.advancePayment

import com.neoutils.finsight.feature.accounts.model.Account
import kotlinx.datetime.LocalDate

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val openingDate: LocalDate? = null,
    val closingDate: LocalDate? = null,
)
