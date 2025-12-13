package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository

class UpdateCreditCardUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(creditCard: CreditCard) {
        require(creditCard.name.isNotBlank()) { "Credit card name cannot be blank" }
        require(creditCard.limit >= 0) { "Credit card limit must be non-negative" }
        repository.update(creditCard)
    }
}
