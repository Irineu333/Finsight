@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.feature.creditCards.error.InvoiceError
import com.neoutils.finsight.feature.creditCards.exception.InvoiceException
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class PayInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
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

        val creditCard = ensureNotNull(creditCardRepository.getCreditCardById(invoice.creditCardId)) {
            InvoiceException(InvoiceError.NotFound)
        }
        val closingDate = invoice.closingMonth.safeOnDay(creditCard.closingDay)
        val dueDate = invoice.dueMonth.safeOnDay(creditCard.dueDay)

        ensure(paidAt >= closingDate) {
            InvoiceException(InvoiceError.PaymentDateBeforeClosing)
        }

        ensure(paidAt <= dueDate) {
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
