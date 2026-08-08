package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency every figure of a card is denominated in (design D17): a card is a facade
 * over a `LIABILITY` account, and its limit, its invoices, its installments and its
 * adjustments all read in *that account's* currency — never in the base one, which is a
 * consolidation unit and not a resource a figure may borrow (design D29).
 *
 * The card itself does not carry it: `CreditCard` names its account by [CreditCard.accountId],
 * and the account is the single place the currency is stated. A card without that account
 * cannot exist — it is created inside the same transaction as the card and held by a
 * foreign key — so its absence is a broken invariant, not a figure to be guessed at.
 */
internal suspend fun IAccountRepository.currencyOf(creditCard: CreditCard): String =
    requireNotNull(getAccountById(creditCard.accountId)) {
        "Credit card ${creditCard.id} names account ${creditCard.accountId}, which does not exist"
    }.currency
