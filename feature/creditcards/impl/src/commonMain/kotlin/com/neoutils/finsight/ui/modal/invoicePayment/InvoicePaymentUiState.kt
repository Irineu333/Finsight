package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.paymentLabel
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_payment_pay
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
    ) : InvoicePaymentUiState {

        /**
         * Whether this payment discharges the invoice. The state decides it, and nothing
         * else does — not the screen that opened the sheet nor the button that did.
         */
        val settles = selectedInvoice?.acceptsFullSettlement == true

        /**
         * The verb the sheet confirms with — the same one the surfaces show.
         *
         * It lives on the button and nowhere above it: the head names the operation and
         * holds still, so choosing an invoice never rewrites what sits over the selector
         * that chose it. What the button says is what pressing it will do.
         */
        val label: StringResource = selectedInvoice?.paymentLabel ?: Res.string.invoice_payment_pay

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
