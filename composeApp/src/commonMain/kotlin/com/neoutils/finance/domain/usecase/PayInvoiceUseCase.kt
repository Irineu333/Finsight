@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.PayInvoiceErrors
import com.neoutils.finance.domain.exception.PayInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = PayInvoiceErrors()

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class PayInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Result<Invoice> {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(PayInvoiceException(errors.invoiceNotFound))

        return invoke(invoice, paidAt)
    }

    suspend operator fun invoke(
        invoice: Invoice,
        paidAt: LocalDate,
    ): Result<Invoice> {

        if (!invoice.isPayable) {
            return Result.failure(PayInvoiceException(errors.cannotPayOpenInvoice))
        }

        if (paidAt < invoice.closingDate) {
            return Result.failure(PayInvoiceException(errors.paymentDateBeforeClosing))
        }

        if (paidAt > invoice.dueDate) {
            return Result.failure(PayInvoiceException(errors.paymentDateAfterDue))
        }

        if (paidAt > currentDate) {
            return Result.failure(PayInvoiceException(errors.paymentDateInFuture))
        }

        val paidInvoice = invoice.copy(
            status = Invoice.Status.PAID,
            paidAt = paidAt,
        ).also {
            invoiceRepository.update(it)
        }

        return Result.success(paidInvoice)
    }
}

