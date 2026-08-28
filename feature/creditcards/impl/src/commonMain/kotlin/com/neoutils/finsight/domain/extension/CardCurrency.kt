package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency of a card, where its absence is a **broken invariant** rather than a
 * figure to withhold.
 *
 * A card without its account cannot exist — the account is created inside the same
 * transaction as the card and held by a foreign key — so every figure this feature draws
 * is denominated, and a screen here has no nullable branch to render. The rule itself is
 * [currencyOf]; what this adds is the reading of the absence, which is the only thing
 * that differs between the two callers.
 */
internal suspend fun IAccountRepository.requireCurrencyOf(creditCard: CreditCard): String =
    requireNotNull(currencyOf(creditCard)) {
        "Credit card ${creditCard.id} names account ${creditCard.accountId}, which does not exist"
    }
