package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.repository.ICreditCardRepository

class UnarchiveCreditCardUseCaseImpl(
    private val repository: ICreditCardRepository,
) : UnarchiveCreditCardUseCase {

    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        // Reopening is a blind `UPDATE` by account id, which touches nothing when the
        // id matches nothing: without this the caller would be told the card is back.
        val creditCard = ensureNotNull(
            catch { repository.getCreditCardById(creditCardId) }.bind()
        ) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }

        catch { repository.unarchive(creditCard.accountId) }.bind()
    }
}
