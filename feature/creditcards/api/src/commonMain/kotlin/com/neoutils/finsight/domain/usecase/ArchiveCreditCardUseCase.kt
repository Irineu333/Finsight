package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard

/**
 * Retires a card that has movement. The facade row stays — it is what keeps the
 * card's name readable in the history that references it; only its ledger account
 * is closed, which is what removes the card from the active lists.
 */
interface ArchiveCreditCardUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card and its `LIABILITY` account are both resolved **when the operation
     * runs**: a card identity that matches nothing is refused with
     * `CreditCardError.NOT_FOUND`, and a card whose account is gone with
     * `AccountError.NOT_FOUND`. Nothing is closed in either case.
     */
    suspend operator fun invoke(creditCardId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> =
        invoke(creditCard.id)
}
