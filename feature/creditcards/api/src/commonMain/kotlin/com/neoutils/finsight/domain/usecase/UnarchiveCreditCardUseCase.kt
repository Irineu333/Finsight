package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard

/**
 * Brings an archived card back into circulation — the inverse of
 * [ArchiveCreditCardUseCase]. The wiring differs: archiving goes through
 * `ArchiveAccountUseCase` for the zero-balance guard; unarchiving has no guard — it is
 * reversible and innocuous, so it reopens the card's `LIABILITY` account and nothing
 * else. No guard, no confirmation.
 */
interface UnarchiveCreditCardUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card is resolved **when the operation runs**, because the account to reopen
     * is the one the card names now; an identity that matches nothing is refused with
     * `CreditCardError.NOT_FOUND` and nothing is reopened.
     */
    suspend operator fun invoke(creditCardId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> =
        invoke(creditCard.id)
}
