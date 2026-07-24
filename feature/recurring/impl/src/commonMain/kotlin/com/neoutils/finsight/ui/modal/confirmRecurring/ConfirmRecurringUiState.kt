package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.LocalDate

data class ConfirmRecurringUiState(
    val recurring: Recurring,
    val confirmDate: LocalDate,
    val selectedTarget: TransactionTarget = TransactionTarget.ACCOUNT,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoices: List<Invoice> = emptyList(),
    val selectedInvoice: Invoice? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)
}
