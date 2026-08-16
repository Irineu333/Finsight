package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard

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
interface UpdateCreditCardUseCase {

    /**
     * The card is resolved **when the operation runs**, and [block] is applied to what
     * is stored rather than to what the caller holds; an identity that matches nothing
     * is refused with `CreditCardError.NOT_FOUND` and nothing is written.
     */
    suspend operator fun invoke(
        creditCardId: Long,
        block: (CreditCard) -> CreditCard,
    ): Either<Throwable, CreditCard>
}
