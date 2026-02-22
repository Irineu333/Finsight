package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.model.Invoice

sealed class InvoiceError(val message: String) {

    data object NotFound : InvoiceError("Invoice not found")
    data object CreditCardNotFound : InvoiceError("Credit card not found")
    data object PeriodCollision : InvoiceError("Collision of invoice periods")
    data class BlockedInvoice(val status: Invoice.Status) : InvoiceError("Fatura $status não permite lançamentos")
    data object NoOpenInvoice : InvoiceError("Nenhuma fatura aberta encontrada")
    data object AlreadyExists : InvoiceError("Já existe uma fatura para este mês")

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

    // Delete
    data object CannotDeleteInvoice : InvoiceError("Apenas faturas futuras ou retroativas podem ser excluídas")
}

class InvoiceException(val error: InvoiceError) : Exception(error.message)
