package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IEntryRepository

class CalculateInvoiceUseCase(
    private val entryRepository: IEntryRepository
) {
    suspend operator fun invoke(
        invoiceId: Long
    ): Double {
        // Owed = Σ liability-leg entries of the invoice, read positive.
        return entryRepository.invoiceOwed(invoiceId)
    }
}
