package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.UpdateCreditCardErrors
import com.neoutils.finance.domain.exception.UpdateCreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository

private val errors = UpdateCreditCardErrors()

class UpdateCreditCardUseCase(
    private val repository: ICreditCardRepository,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
) {

    suspend operator fun invoke(
        creditCardId: Long,
        block: (CreditCard) -> CreditCard
    ): Result<CreditCard> {
        val creditCard = repository.getCreditCardById(creditCardId)
            ?: return Result.failure(UpdateCreditCardException(errors.notFound))

        return validate(
            block(creditCard)
        ).onSuccess {
            repository.update(it)
        }
    }

    private suspend fun validate(
        creditCard: CreditCard
    ): Result<CreditCard> {

        validateCreditCardName(
            name = creditCard.name,
            ignoreId = creditCard.id
        )?.let { error ->
            return Result.failure(UpdateCreditCardException(error))
        }

        return Result.success(creditCard)
    }
}
