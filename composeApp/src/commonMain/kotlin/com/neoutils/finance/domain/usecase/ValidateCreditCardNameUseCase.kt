package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finance.domain.error.CreditCardError
import com.neoutils.finance.domain.repository.ICreditCardRepository

class ValidateCreditCardNameUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Either<CreditCardError, String> {

        if (name.isBlank()) {
            return CreditCardError.EMPTY_NAME.left()
        }

        if (hasDuplicateName(name, ignoreId)) {
            return CreditCardError.ALREADY_EXIST_NAME.left()
        }

        return name.right()
    }

    private suspend fun hasDuplicateName(
        name: String,
        ignoreId: Long?
    ): Boolean {
        // TODO: improve this
        return repository.getAllCreditCards().any { creditCards ->
            creditCards.name.equals(name.trim(), ignoreCase = true) &&
                    creditCards.id != ignoreId
        }
    }
}
