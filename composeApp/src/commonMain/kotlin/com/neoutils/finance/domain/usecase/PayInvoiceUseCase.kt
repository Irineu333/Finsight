package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.PayInvoiceErrors
import com.neoutils.finance.domain.exception.PayInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate

private val errors = PayInvoiceErrors()

class PayInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val createInvoiceUseCase: CreateInvoiceUseCase
) {
    suspend operator fun invoke(
        invoiceId: Long,
        paidAt: LocalDate,
    ): Result<Invoice> {

        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(PayInvoiceException(errors.invoiceNotFound))

        if (invoice.status != Invoice.Status.CLOSED) {
            return Result.failure(PayInvoiceException(errors.cannotPayOpenInvoice))
        }

        val paidInvoice = invoice.copy(
            status = Invoice.Status.PAID,
            paidAt = paidAt.toEpochDays(),
        )

        invoiceRepository.update(paidInvoice)

        createInvoiceUseCase(invoice.creditCard.id)

        return Result.success(paidInvoice)
    }
}
