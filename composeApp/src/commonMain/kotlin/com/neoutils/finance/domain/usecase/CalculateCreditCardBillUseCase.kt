package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction

class CalculateCreditCardBillUseCase {
    operator fun invoke(
        invoiceId: Long,
        transactions: List<Transaction>
    ): Double {
        return transactions
            .filter { it.invoice?.id == invoiceId }
            .sumOf { it.amount }
    }
}