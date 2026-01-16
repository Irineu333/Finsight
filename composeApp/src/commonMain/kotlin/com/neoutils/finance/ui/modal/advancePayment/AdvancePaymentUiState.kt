package com.neoutils.finance.ui.modal.advancePayment

import com.neoutils.finance.domain.model.Account

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
)
