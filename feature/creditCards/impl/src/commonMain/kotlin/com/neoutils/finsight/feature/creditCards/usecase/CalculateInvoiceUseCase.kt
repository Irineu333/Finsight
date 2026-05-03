package com.neoutils.finsight.feature.creditCards.usecase

import com.neoutils.finsight.core.domain.model.Transaction
import com.neoutils.finsight.core.domain.extension.signedImpact
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository

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