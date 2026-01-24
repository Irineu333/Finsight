package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.BuildCreditCardErrors
import com.neoutils.finance.domain.exception.CreditCardException
import com.neoutils.finance.domain.repository.ICreditCardRepository

private val errors = BuildCreditCardErrors()

class ValidateCreditCardNameUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Result<String> {
        if (name.isBlank()) {
            return Result.failure(CreditCardException(errors.nameRequired))
        }

        if (hasDuplicateName(name, ignoreId)) {
            return Result.failure(CreditCardException(errors.nameAlreadyExists))
        }

        return Result.success(name)
    }

    private suspend fun hasDuplicateName(
        name: String,
        ignoreId: Long?
    ): Boolean {
        val creditCards = repository.getAllCreditCards()
        return creditCards.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != ignoreId
        }
    }
}
