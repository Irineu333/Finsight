package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository

class DeleteCreditCardUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(creditCard: CreditCard) {
        repository.delete(creditCard)
    }
}
