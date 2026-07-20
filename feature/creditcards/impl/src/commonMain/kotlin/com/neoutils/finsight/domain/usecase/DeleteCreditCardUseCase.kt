package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository

/**
 * Removes a card that never moved, facade and ledger account together.
 *
 * A card with movement is refused — see [ArchiveCreditCardUseCase].
 */
class DeleteCreditCardUseCase(
    private val creditCardRepository: ICreditCardRepository,
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> {
        if (entryRepository.hasEntries(creditCard.accountId)) {
            return AccountException(AccountError.HAS_TRANSACTIONS).left()
        }
        return catch { creditCardRepository.delete(creditCard) }
    }
}
