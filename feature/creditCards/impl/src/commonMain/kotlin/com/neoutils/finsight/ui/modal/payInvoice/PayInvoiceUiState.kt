package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.core.domain.model.Account

data class PayInvoiceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
)