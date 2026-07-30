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
    /**
     * Whether an account or a card was left out of the lists above for being in another
     * currency. A silently shorter list is a lie by omission, so the modal says why it
     * shrank — the refusal is prevented in the control and never reported as an error
     * (design D26).
     */
    val hiddenByCurrency: Boolean = false,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    /** The currency the template's amount is stated in, and the only one it can post in. */
    val currency: String? get() = recurring.currency
}
