package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard

/**
 * Removes a card that never moved, facade and ledger account together.
 *
 * A card with movement is refused — see [ArchiveCreditCardUseCase]. So is one a
 * recurring template still points at: that foreign key is `SET_NULL`, so deleting
 * would strip the link rather than fail, and a card template would silently become an
 * account one.
 */
interface DeleteCreditCardUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card is resolved **when the operation runs**, so the guards read the card as
     * it is at that moment; an identity that matches nothing is refused with
     * `CreditCardError.NOT_FOUND` and nothing is removed.
     */
    suspend operator fun invoke(creditCardId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> =
        invoke(creditCard.id)
}
