package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

class DeleteCreditCardUseCaseImpl(
    private val creditCardRepository: ICreditCardRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
) : DeleteCreditCardUseCase {

    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        // Resolved before either guard reads: a card the guards cannot see would be
        // reported as removed, and the removal would have touched nothing.
        val creditCard = ensureNotNull(
            catch { creditCardRepository.getCreditCardById(creditCardId) }.bind()
        ) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }

        ensure(!catch { entryRepository.hasEntries(creditCard.accountId) }.bind()) {
            AccountException(AccountError.HAS_TRANSACTIONS)
        }

        // Same shape of guard: the recurring FK is SET_NULL, so deleting would
        // strip the link rather than fail, and a card template would silently
        // become an account one.
        ensure(!catch { recurringRepository.hasRecurringForCreditCard(creditCard.id) }.bind()) {
            AccountException(AccountError.HAS_RECURRING)
        }

        catch { creditCardRepository.delete(creditCard) }.bind()
    }
}
