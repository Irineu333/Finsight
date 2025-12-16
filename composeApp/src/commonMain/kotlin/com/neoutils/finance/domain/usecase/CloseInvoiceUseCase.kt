@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CloseInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Long): Result<Unit> {
        val invoice = invoiceRepository.getById(invoiceId)
            ?: return Result.failure(IllegalArgumentException("Invoice not found"))

        if (invoice.status == Invoice.Status.CLOSED) {
            return Result.failure(IllegalStateException("Invoice is already closed"))
        }

        val currentMonth = Clock.System.now().toYearMonth()
        if (currentMonth < invoice.closingMonth) {
            return Result.failure(
                IllegalStateException("Cannot close invoice before the closing month")
            )
        }

        invoiceRepository.update(invoice.copy(status = Invoice.Status.CLOSED))
        return Result.success(Unit)
    }
}
