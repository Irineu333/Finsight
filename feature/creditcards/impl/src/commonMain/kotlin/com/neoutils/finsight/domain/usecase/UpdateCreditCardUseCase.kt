package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository

/**
 * Updates a card — and, like an account, never its currency (design D12).
 *
 * Here the rule needs no refusal, because the card cannot *say* a currency: it is
 * denominated by its `LIABILITY` account, [CreditCard] carries no such field, and
 * `CreditCardRepository.update` writes only the name and the icon onto that account.
 * The whole class of error is unutterable rather than validated, which is the same move
 * `TransactionLeg` makes on the write path.
 *
 * That is why this is documented instead of guarded: a guard here would have to invent
 * a currency to compare, and a reader would be left believing the field exists.
 */
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
