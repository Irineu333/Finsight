package com.neoutils.finsight.feature.creditCards.modal.advancePayment

import com.neoutils.finsight.core.domain.model.Account

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
)
