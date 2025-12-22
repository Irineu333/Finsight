package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository

class ReopenInvoiceUseCase(private val invoiceRepository: IInvoiceRepository) {
    suspend operator fun invoke(invoiceId: Long): Result<Unit> {
        val invoice =
                invoiceRepository.getInvoiceById(invoiceId)
                        ?: return Result.failure(IllegalArgumentException("Invoice not found"))

        if (invoice.status == Invoice.Status.OPEN) {
            return Result.failure(IllegalStateException("Invoice is already open"))
        }

        if (invoice.status == Invoice.Status.PAID) {
            return Result.failure(IllegalStateException("Cannot reopen a paid invoice"))
        }

        invoiceRepository.update(invoice.copy(status = Invoice.Status.OPEN, closedAt = null))
        return Result.success(Unit)
    }
}
