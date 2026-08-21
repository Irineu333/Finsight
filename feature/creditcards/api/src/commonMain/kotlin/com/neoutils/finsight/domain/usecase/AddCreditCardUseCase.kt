package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm

/**
 * Creates one of the user's cards, with its `LIABILITY` account and its first invoice
 * open on the cycle the card is already in today.
 *
 * Opening that invoice is part of the creation and not a follow-up to it: a card whose
 * invoice failed to open would accept no expense at all, so the failure fails the
 * creation rather than being reported as success.
 *
 * It takes no identity: there is nothing to resolve, since the card it operates on is
 * the one it brings into existence. It answers the card as stored, identity included,
 * because a caller that cannot name what it just created cannot report it either.
 */
interface AddCreditCardUseCase {

    /**
     * @param currency what the card's `LIABILITY` account is denominated in — chosen in
     * the form, carried explicitly, and fixed from the moment the card exists (D12).
     */
    suspend operator fun invoke(
        form: CreditCardForm,
        currency: String,
    ): Either<Throwable, CreditCard>
}
