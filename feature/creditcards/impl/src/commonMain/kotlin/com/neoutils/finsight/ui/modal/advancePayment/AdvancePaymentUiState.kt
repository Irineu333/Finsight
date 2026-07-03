package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
)
