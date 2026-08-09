package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.form.TransactionForm
import kotlinx.datetime.LocalDate

data class AddInstallmentUiState(
    /** What the sheet renders and what the ViewModel would write — the same object. */
    val form: TransactionForm,
    /** Today as the app understands it, which bounds the date picker. */
    val today: LocalDate,
    /** Whether the form is worth submitting, decided where the clock is. */
    val canSubmit: Boolean = false,
    val categories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
    // What the amount being typed is denominated in: the currency of the selected
    // card (design D17). Until a card is chosen there is nothing to denominate it
    // with, and the form already refuses to submit without one.
    val currency: String? = null,
) {
    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true
}
