package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.repository.ICreditCardRepository

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

    /**
     * Uniqueness spans closed cards too: closing keeps the name, and history still
     * renders it. Two "Nubank" side by side, one of them grey, is not a name.
     */
    private suspend fun hasDuplicateName(
        name: String,
        ignoreId: Long?
    ): Boolean {
        // TODO: improve this
        return repository.getAllCreditCardsIncludingClosed().any { creditCard ->
            creditCard.name.equals(name.trim(), ignoreCase = true) &&
                    creditCard.id != ignoreId
        }
    }
}
