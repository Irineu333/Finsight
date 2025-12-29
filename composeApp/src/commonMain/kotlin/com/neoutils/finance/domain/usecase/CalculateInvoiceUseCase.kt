package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository

class CalculateInvoiceUseCase(
    private val repository: ITransactionRepository
) {
    operator fun invoke(
        invoiceId: Long,
        transactions: List<Transaction>
    ): Double {
        return transactions.filter {
            it.invoice?.id == invoiceId
        }.sumOf { it.creditAmount }
    }

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