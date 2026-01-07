package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.repository.ITransactionRepository

class CalculateInvoiceUseCase(
    private val repository: ITransactionRepository
) {
    suspend operator fun invoke(
        invoiceId: Long
    ): Double {
        return repository.getTransactionsBy(
            invoiceId = invoiceId,
            type = null,
            target = null,
            date = null,
        ).sumOf { it.creditAmount }
    }
}