package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.CreditCardError
import com.neoutils.finance.domain.exception.CreditCardException
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository

class UpdateCreditCardUseCase(
    private val repository: ICreditCardRepository,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
) {
    suspend operator fun invoke(
        creditCardId: Long,
        block: (CreditCard) -> CreditCard
    ): Either<Throwable, CreditCard> {
        return either {
            val oldCreditCard = catch {
                ensureNotNull(repository.getCreditCardById(creditCardId)) {
                    CreditCardException(CreditCardError.NOT_FOUND)
                }
            }.bind()

            val newCreditCard = catch {
                block(oldCreditCard)
            }.bind()

            validateCreditCardName(
                name = newCreditCard.name,
                ignoreId = creditCardId,
            ).mapLeft {
                CreditCardException(it)
            }.bind()

            catch {
                repository.update(newCreditCard)
            }.bind()

            newCreditCard
        }
    }
}
