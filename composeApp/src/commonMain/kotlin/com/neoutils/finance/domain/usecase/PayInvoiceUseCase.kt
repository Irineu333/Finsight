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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class PayInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        invoke(invoice, paidAt).bind()
    }

    suspend operator fun invoke(
        invoice: Invoice,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice> = either {
        ensure(invoice.isPayable) {
            InvoiceException(InvoiceError.CannotPayOpenInvoice)
        }

        ensure(paidAt >= invoice.closingDate) {
            InvoiceException(InvoiceError.PaymentDateBeforeClosing)
        }

        ensure(paidAt <= invoice.dueDate) {
            InvoiceException(InvoiceError.PaymentDateAfterDue)
        }

        ensure(paidAt <= currentDate) {
            InvoiceException(InvoiceError.PaymentDateInFuture)
        }

        invoice.copy(
            status = Invoice.Status.PAID,
            paidAt = paidAt,
        ).also {
            invoiceRepository.update(it)
        }
    }
}

