package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.signedImpact
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
            target = Transaction.Target.CREDIT_CARD,
            date = null,
        ).sumOf { -it.signedImpact() }
    }
}