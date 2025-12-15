package com.neoutils.finance.domain.usecase

import com.neoutils.finance.database.repository.CreditCardRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class PayCreditCardBillUseCase(
    private val repository: ITransactionRepository,
    private val creditCardRepository: ICreditCardRepository,
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
            creditCard = creditCardRepository.getCreditCardById(creditCardId),
        )

        repository.insert(transaction)
    }
}

