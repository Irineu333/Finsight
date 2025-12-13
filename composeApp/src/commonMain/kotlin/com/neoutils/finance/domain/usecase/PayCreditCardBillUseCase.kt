package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class PayCreditCardBillUseCase(
    private val repository: ITransactionRepository
) {
    suspend operator fun invoke(
        creditCardId: Long,
        amount: Double,
        date: LocalDate
    ) {
        require(amount > 0) { "Payment amount must be positive" }

        val transaction = Transaction(
            type = Transaction.Type.INVOICE_PAYMENT,
            amount = -amount,
            title = "Pagamento de Fatura",
            date = date,
            category = null,
            target = Transaction.Target.INVOICE_PAYMENT,
            creditCardId = creditCardId
        )

        repository.insert(transaction)
    }
}

