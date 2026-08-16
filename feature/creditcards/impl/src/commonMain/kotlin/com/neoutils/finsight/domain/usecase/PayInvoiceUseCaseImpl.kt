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
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class PayInvoiceUseCaseImpl(
    private val invoiceRepository: IInvoiceRepository,
    private val clock: Clock,
) : PayInvoiceUseCase {

    private val currentDate get() = clock.today()

    override suspend fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Either<InvoiceException, Invoice> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

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
