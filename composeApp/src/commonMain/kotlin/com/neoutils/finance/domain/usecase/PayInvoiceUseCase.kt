package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository

class PayInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Long): Result<Unit> {
        val invoice = invoiceRepository.getById(invoiceId)
            ?: return Result.failure(IllegalArgumentException("Invoice not found"))

        if (invoice.status == Invoice.Status.PAID) {
            return Result.failure(IllegalStateException("Invoice is already paid"))
        }

        if (invoice.status == Invoice.Status.OPEN) {
            return Result.failure(IllegalStateException("Cannot pay an open invoice. Close it first."))
        }

        invoiceRepository.update(invoice.copy(status = Invoice.Status.PAID))
        return Result.success(Unit)
    }
}
