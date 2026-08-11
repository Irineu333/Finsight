package com.neoutils.finsight.ui.modal.editInvoiceBalance

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate

sealed interface EditInvoiceBalanceUiState {
    data object Loading : EditInvoiceBalanceUiState
    data class Content(
        val creditCards: List<CreditCard>,
        val selectedCreditCard: CreditCard,
        val editableInvoices: List<Invoice>,
        val selectedInvoice: Invoice,
        val currentBalance: Double,
        // The currency of the card the selected invoice belongs to (design D17). It
        // follows the selector: picking another card re-denominates the field.
        val currency: String,
        val date: String,
        val today: LocalDate,
    ) : EditInvoiceBalanceUiState {

        /**
         * Whether the chosen date falls outside the selected invoice's window — said, and
         * never corrected. The value the correction carries reaches the invoice through
         * the dimension, so nothing about it depends on the date; without this the form
         * would give no sign at all.
         */
        val isDateOutsideInvoice = InvoiceMonthSelection(
            creditCard = selectedCreditCard,
            dueMonth = selectedInvoice.dueMonth,
            existingInvoice = selectedInvoice,
        ).diverges(date)

        /**
         * The balance the field is pre-filled with, denominated. `NATURAL` because only
         * a negative balance is information here — the sign policy lives in the type,
         * which is what removed the local `formatMoney` that used to prepend `"-"` by
         * hand outside it.
         */
        val balanceAmount = DisplayAmount.natural(
            value = currentBalance,
            currency = currency,
            isApproximate = false,
        )
    }
}