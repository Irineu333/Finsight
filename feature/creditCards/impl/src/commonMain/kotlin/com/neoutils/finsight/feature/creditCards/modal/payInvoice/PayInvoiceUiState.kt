package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import com.neoutils.finsight.feature.accounts.model.Account
import kotlinx.datetime.LocalDate

data class PayInvoiceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val closingDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
)
