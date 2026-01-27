@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlin.time.ExperimentalTime

class ReopenInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Long): Either<InvoiceException, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status != Invoice.Status.OPEN) {
            InvoiceException(InvoiceError.AlreadyOpen)
        }

        ensure(invoice.status != Invoice.Status.PAID) {
            InvoiceException(InvoiceError.CannotReopenPaidInvoice)
        }

        val existingInvoices = invoiceRepository.getInvoicesByCreditCard(invoice.creditCard.id)

        val nextOpenInvoice = existingInvoices.find { existing ->
            existing.status == Invoice.Status.OPEN &&
            existing.openingMonth == invoice.closingMonth
        }

        if (nextOpenInvoice != null) {
            invoiceRepository.update(
                nextOpenInvoice.copy(status = Invoice.Status.FUTURE)
            )
        }

        invoice.copy(
            status = Invoice.Status.OPEN,
            closedAt = null,
            paidAt = null
        ).also {
            invoiceRepository.update(it)
        }
    }
}

