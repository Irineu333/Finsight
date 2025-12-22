@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.CloseInvoiceErrors
import com.neoutils.finance.domain.exception.CloseInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val errors = CloseInvoiceErrors()

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        closedAt: LocalDate
    ): Result<Invoice> {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(CloseInvoiceException(errors.invoiceNotFound))

        if (invoice.status == Invoice.Status.PAID) {
            return Result.failure(CloseInvoiceException(errors.cannotClosePaidInvoice))
        }

        if (invoice.status == Invoice.Status.CLOSED) {
            return Result.failure(CloseInvoiceException(errors.invoiceAlreadyClosed))
        }

        val currentMonth = Clock.System.now().toYearMonth()

        if (currentMonth < invoice.closingMonth) {
            return Result.failure(CloseInvoiceException(errors.cannotCloseBeforeClosingMonth))
        }

        val invoiceAmount = calculateInvoiceUseCase(invoiceId)

        if (invoiceAmount < 0) {
            return Result.failure(CloseInvoiceException(errors.negativeBalance))
        }

        if (invoiceAmount == 0.0) {
            return payInvoiceUseCase(
                invoiceId = invoiceId,
                paidAt = closedAt,
            )
        }

        val closedInvoice = invoice.copy(
            status = Invoice.Status.CLOSED,
            closedAt = closedAt.toEpochDays(),
        )

        invoiceRepository.update(closedInvoice)

        return Result.success(closedInvoice)
    }
}
