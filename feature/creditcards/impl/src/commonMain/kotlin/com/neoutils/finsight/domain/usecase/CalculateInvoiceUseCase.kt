package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IEntryRepository

/**
 * Amount owed on an invoice = Σ the entries carrying its dimension, read positive.
 *
 * It takes the invoice, not its id: the ledger knows only the dimension, and
 * resolving facade → identity is the caller's business, which is the same
 * direction the write intent takes.
 */
class CalculateInvoiceUseCase(
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(invoice: Invoice): Double =
        invoice.dimensionId?.let { entryRepository.dimensionOwed(it) } ?: 0.0
}
