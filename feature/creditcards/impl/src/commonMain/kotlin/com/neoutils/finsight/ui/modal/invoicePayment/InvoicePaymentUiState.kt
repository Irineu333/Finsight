package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.paymentLabel
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_payment_edit_confirm
import com.neoutils.finsight.resources.invoice_payment_edit_title
import com.neoutils.finsight.resources.invoice_payment_pay
import com.neoutils.finsight.resources.invoice_payment_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

sealed interface InvoicePaymentUiState {

    data object Loading : InvoicePaymentUiState

    data class Content(
        val creditCards: List<CreditCard> = emptyList(),
        val selectedCreditCard: CreditCard? = null,
        /** The card's invoices a payment may name — the domain's predicate, not a list of statuses. */
        val invoices: List<Invoice> = emptyList(),
        val selectedInvoice: Invoice? = null,
        val accounts: List<Account> = emptyList(),
        val selectedAccount: Account? = null,
        /** What the selected invoice owes, read from it and never received ready-made. */
        val outstandingDebt: Double = 0.0,
        /** The currency the card owes in, declared by its `LIABILITY` account (design D17). */
        val invoiceCurrency: String? = null,
        val date: String = "",
        val today: LocalDate,
        /** What the rate archive implies leaves the account, when it has anything to say. */
        val suggestion: CrossCurrencyAmountSuggestion? = null,
        /**
         * Whether this sheet is correcting an operation instead of registering one. It
         * is fixed from the moment the sheet opens and never follows a selection.
         */
        val isEditMode: Boolean = false,
        /**
         * Whether the form is still showing the operation exactly as it is recorded —
         * true from the opening of a correction until the user switches card or invoice.
         * Opening preserves, switching recalculates (design D4).
         */
        val showsRecordedOperation: Boolean = false,
    ) : InvoicePaymentUiState {

        /**
         * Whether this payment discharges the invoice. The state decides it for an
         * operation that does not exist yet, and nothing else does — not the screen that
         * opened the sheet nor the button that did.
         *
         * An operation **already written** has the mode it has: correcting a partial
         * payment is reaffirming a partial payment, and no invoice this sheet then
         * offers could turn it into a discharge anyway. Saying so here rather than
         * relying on that is what keeps the two facts from having to agree.
         */
        val settles = !isEditMode && selectedInvoice?.acceptsFullSettlement == true

        /**
         * The verb the sheet confirms with — the same one the surfaces show.
         *
         * It lives on the button and nowhere above it: the head names the operation and
         * holds still, so choosing an invoice never rewrites what sits over the selector
         * that chose it. What the button says is what pressing it will do — and on a
         * correction the money has already moved, so neither "advance" nor "pay" is true
         * of it.
         */
        val label: StringResource = when {
            isEditMode -> Res.string.invoice_payment_edit_confirm
            else -> selectedInvoice?.paymentLabel ?: Res.string.invoice_payment_pay
        }

        /**
         * What the head announces — the operation, never the selection.
         *
         * The mode is the one thing it follows, and the mode is fixed from the opening,
         * so the head still holds still while the selectors below it change. Without
         * this a sheet titled "invoice payment" with every field already filled in would
         * read as a payment merely suggested, and confirming it as creating a second.
         */
        val headline: StringResource = when {
            isEditMode -> Res.string.invoice_payment_edit_title
            else -> Res.string.invoice_payment_title
        }

        /**
         * Whether the paying account is denominated differently from the card. Derived
         * from the two current ends, because both are chosen on this same surface.
         */
        val isCrossCurrency = invoiceCurrency != null &&
            selectedAccount != null &&
            selectedAccount.currency != invoiceCurrency

        /** What is owed, denominated. Absent while no invoice is selected. */
        val debtAmount: DisplayAmount? = invoiceCurrency?.let {
            DisplayAmount.magnitude(outstandingDebt, it, isApproximate = false)
        }

        /** The days this invoice may be settled on — a limit, so the field never offers more. */
        val window: ClosedRange<LocalDate>? = selectedInvoice?.settlementWindow(today)

        /** An invoice that owes nothing is not paid here; it is discharged by closing it. */
        val hasDebt = outstandingDebt > 0.0
    }
}
