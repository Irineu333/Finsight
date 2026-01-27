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
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        closedAt: LocalDate
    ): Either<InvoiceException, Invoice> = either {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status != Invoice.Status.PAID) {
            InvoiceException(InvoiceError.CannotClosePaidInvoice)
        }

        ensure(invoice.status != Invoice.Status.CLOSED) {
            InvoiceException(InvoiceError.AlreadyClosed)
        }

        ensure(closedAt.yearMonth == invoice.closingMonth) {
            InvoiceException(InvoiceError.CannotCloseOutsideClosingMonth)
        }

        val invoiceAmount = calculateInvoiceUseCase(invoiceId)

        ensure(invoiceAmount >= 0) {
            InvoiceException(InvoiceError.NegativeBalance)
        }

        if (invoice.status.isRetroactive) {
            return@either payInvoiceUseCase(
                invoice = invoice,
                paidAt = closedAt,
            ).bind()
        }

        val closedInvoice = invoice.copy(
            status = Invoice.Status.CLOSED,
            closedAt = closedAt,
        ).also {
            invoiceRepository.update(it)
        }

        openInvoiceUseCase(
            creditCardId = invoice.creditCard.id,
            openingMonth = invoice.closingMonth
        )

        if (invoiceAmount == 0.0) {
            return@either payInvoiceUseCase(
                invoice = closedInvoice,
                paidAt = closedAt,
            ).bind()
        }

        closedInvoice
    }
}
