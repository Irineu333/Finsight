package com.neoutils.finance.ui.modal.payInvoice

import com.neoutils.finance.domain.model.Account

data class PayInvoiceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
)