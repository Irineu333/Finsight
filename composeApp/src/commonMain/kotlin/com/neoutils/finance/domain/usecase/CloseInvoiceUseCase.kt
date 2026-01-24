package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.error.CloseInvoiceErrors
import com.neoutils.finance.domain.exception.CloseInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

private val errors = CloseInvoiceErrors()

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
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

        if (closedAt.yearMonth != invoice.closingMonth) {
            return Result.failure(CloseInvoiceException(errors.cannotCloseOutsideClosingMonth))
        }

        val invoiceAmount = calculateInvoiceUseCase(invoiceId)

        if (invoiceAmount < 0) {
            return Result.failure(CloseInvoiceException(errors.negativeBalance))
        }

        if (invoice.status.isRetroactive) {
            return payInvoiceUseCase(
                invoice = invoice,
                paidAt = closedAt,
            )
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
            return payInvoiceUseCase(
                invoice = closedInvoice,
                paidAt = closedAt,
            )
        }

        return Result.success(closedInvoice)
    }
}
