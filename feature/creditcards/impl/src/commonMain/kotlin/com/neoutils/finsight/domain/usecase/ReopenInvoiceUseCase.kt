@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.reopenSuccessor
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

        // Reopening any closed invoice — including one that was retroactive, whose
        // RETROACTIVE status closing already overwrote with CLOSED and which nothing
        // persists — demotes back to FUTURE the successor that would otherwise stay
        // OPEN alongside it. That successor must be the current OPEN one; if it is
        // absent or not OPEN, reopening would leave two OPEN invoices on the card —
        // refuse. `isReopenable` (core/model) is the same rule the screens read to not
        // offer the button.
        val successor = invoice.reopenSuccessor(
            invoiceRepository.getInvoicesByCreditCard(invoice.creditCard.id)
        )

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

