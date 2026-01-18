package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.ReopenInvoiceErrors
import com.neoutils.finance.domain.exception.ReopenInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository

private val errors = ReopenInvoiceErrors()

class ReopenInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Long): Result<Invoice> {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(ReopenInvoiceException(errors.invoiceNotFound))

        if (invoice.status == Invoice.Status.OPEN) {
            return Result.failure(ReopenInvoiceException(errors.invoiceAlreadyOpen))
        }

        if (invoice.status == Invoice.Status.PAID) {
            return Result.failure(ReopenInvoiceException(errors.cannotReopenPaidInvoice))
        }

        val existingInvoices = invoiceRepository.getInvoicesByCreditCard(invoice.creditCard.id)

        val nextOpenInvoice = existingInvoices.find { existing ->
            existing.status == Invoice.Status.OPEN && 
            existing.openingMonth == invoice.closingMonth
        }

        if (nextOpenInvoice != null) {
            invoiceRepository.update(
                nextOpenInvoice.copy(status = Invoice.Status.FUTURE)
            )
        }

        return Result.success(
            invoice.copy(
                status = Invoice.Status.OPEN,
                closedAt = null,
                paidAt = null
            ).also {
                invoiceRepository.update(it)
            }
        )
    }
}

