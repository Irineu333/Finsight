package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository

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

            catch {
                block(oldCreditCard)
            }.onRight { creditCard ->
                validateCreditCardName(
                    name = creditCard.name,
                    ignoreId = creditCardId,
                ).mapLeft {
                    CreditCardException(it)
                }.bind()
            }.onRight { newCreditCard ->
                catch {
                    repository.update(newCreditCard)
                }.bind()
            }.bind()
        }
    }
}
