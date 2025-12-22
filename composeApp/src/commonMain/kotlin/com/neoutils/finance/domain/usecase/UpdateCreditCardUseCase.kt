package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.UpdateCreditCardErrors
import com.neoutils.finance.domain.exception.UpdateCreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository

private val errors = UpdateCreditCardErrors()

class UpdateCreditCardUseCase(
    private val repository: ICreditCardRepository
) {

    suspend operator fun invoke(
        creditCardId: Long,
        block: (CreditCard) -> CreditCard
    ): Result<CreditCard> {
        val creditCard = repository.getCreditCardById(creditCardId)
            ?: return Result.failure(UpdateCreditCardException(errors.notFound))

        return runCatching {
            block(creditCard)
        }.onSuccess { updatedCreditCard ->
            repository.update(updatedCreditCard)
        }
    }
}
