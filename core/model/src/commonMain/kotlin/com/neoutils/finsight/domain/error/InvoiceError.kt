package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_error_cannot_reopen
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.util.UiText

sealed class InvoiceError(val message: String) {

    data object NotFound : InvoiceError("Invoice not found")
    data object CreditCardNotFound : InvoiceError("Credit card not found")
    data object PeriodCollision : InvoiceError("Collision of invoice periods")
    data class BlockedInvoice(val status: Invoice.Status) : InvoiceError("Invoice with status $status does not allow transactions")
    data object NoOpenInvoice : InvoiceError("No open invoice found")
    data object AlreadyExists : InvoiceError("An invoice already exists for this month")

    // Close
    data object CannotClosePaidInvoice : InvoiceError("Cannot close a paid invoice")
    data object AlreadyClosed : InvoiceError("Invoice is already closed")
    data object CannotCloseOutsideClosingMonth : InvoiceError("Cannot close invoice outside of the closing month")
    data object NegativeBalance : InvoiceError("Cannot close invoice with negative balance")

    // Open
    data object OverlappingInvoice : InvoiceError("Invoice period overlaps with existing invoice")

    // Pay
    data object CannotPayOpenInvoice : InvoiceError("Only closed invoices can be paid")
    data object PaymentDateBeforeClosing : InvoiceError("Payment date cannot be before closing date")
    data object PaymentDateAfterDue : InvoiceError("Payment date cannot be after due date")
    data object PaymentDateInFuture : InvoiceError("Payment date cannot be in the future")

    // PayInvoicePayment / AdvancePayment
    data object NegativeAmount : InvoiceError("Payment amount must be positive")
    data object AmountExceedsInvoice : InvoiceError("Payment amount cannot exceed invoice bill")
    data object InvoiceNotClosed : InvoiceError("Invoice must be closed before payment")
    data object InvoiceNotInDebt : InvoiceError("Invoice has no outstanding debt")
    data object DateOutsideInvoicePeriod : InvoiceError("Payment date must be within invoice period")
    data object DateInFuture : InvoiceError("Payment date cannot be in the future")

    // Reopen
    data object AlreadyOpen : InvoiceError("Invoice is already open")
    data object CannotReopenPaidInvoice : InvoiceError("Cannot reopen a paid invoice")
    data object CannotReopenInvoice : InvoiceError("Only the latest closed invoice can be reopened")

    // Delete
    data object CannotDeleteInvoice : InvoiceError("Only future or retroactive invoices can be deleted")
}

class InvoiceException(val error: InvoiceError) : Exception(error.message)

/**
 * Only errors a user action can actually surface get their own message; the rest are
 * internal invariants that never reach a screen and fall back to the neutral generic.
 * Reopen is the first invoice flow to show its refusal instead of failing silently.
 */
fun InvoiceError.toUiText(): UiText = when (this) {
    InvoiceError.CannotReopenInvoice -> UiText.Res(Res.string.invoice_error_cannot_reopen)
    else -> UiText.Res(Res.string.ledger_action_error_generic)
}
