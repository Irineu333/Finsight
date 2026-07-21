@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
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

        // A retroactive invoice belongs to a past cycle and never owned the current
        // OPEN one — closing it opens no successor (CloseInvoiceUseCase). Reopening it
        // must restore RETROACTIVE, not OPEN: turning it OPEN would leave two OPEN
        // invoices on the card and erase the RETROACTIVE status the cycle depends on.
        // Reachable only since a retroactive invoice with a balance now reaches CLOSED
        // instead of going straight to PAID.
        if (invoice.status.isRetroactive) {
            return@either invoice.copy(
                status = Invoice.Status.RETROACTIVE,
                closedAt = null,
                paidAt = null,
            ).also {
                invoiceRepository.update(it)
            }
        }

        // A closed invoice is reopened by demoting the successor that closing it opened
        // (openingMonth == this.closingMonth) back to FUTURE. That successor must be the
        // current OPEN one; if it is not, this is a mid-chain invoice and reopening it
        // would leave two OPEN invoices on the card — refuse.
        val successor = invoiceRepository.getInvoicesByCreditCard(invoice.creditCard.id)
            .find { existing -> existing.openingMonth == invoice.closingMonth }

        ensureNotNull(successor?.takeIf { it.status == Invoice.Status.OPEN }) {
            InvoiceException(InvoiceError.CannotReopenInvoice)
        }

        invoiceRepository.update(successor.copy(status = Invoice.Status.FUTURE))

        invoice.copy(
            status = Invoice.Status.OPEN,
            closedAt = null,
            paidAt = null
        ).also {
            invoiceRepository.update(it)
        }
    }
}

