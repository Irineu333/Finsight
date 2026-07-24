package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository

/**
 * Brings an archived card back into circulation — the inverse of
 * [ArchiveCreditCardUseCase], mirroring its shape (`Either` via `catch`, taking the
 * [CreditCard] and reading its `accountId`). The wiring differs: archiving goes
 * through `IAccountRepository` + `ArchiveAccountUseCase` for the zero-balance guard;
 * unarchiving has no guard — it is reversible and innocuous, so it goes straight to
 * the repo, which reopens the card's `LIABILITY` account. No guard, no confirmation.
 */
class UnarchiveCreditCardUseCase(
    private val repository: ICreditCardRepository,
) {
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> = catch {
        repository.unarchive(creditCard.accountId)
    }
}
