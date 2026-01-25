package com.neoutils.finance.domain.error

import com.neoutils.finance.domain.model.Invoice

sealed class InvoiceError(val message: String) {

    data object NoInvoicesFound : InvoiceError(
        message = "No invoices found"
    )

    data object PeriodCollision : InvoiceError(
        message = "Collision of invoice periods"
    )

    data class BlockedInvoice(
        val status: Invoice.Status
    ) : InvoiceError(
        message = "Fatura $status não permite lançamentos"
    )

    data object NoOpenInvoice : InvoiceError(
        message = "Nenhuma fatura aberta encontrada"
    )

    data object InvoiceAlreadyExists : InvoiceError(
        message = "Já existe uma fatura para este mês"
    )
}

class InvoiceException(val error: InvoiceError) : Exception(error.message)
