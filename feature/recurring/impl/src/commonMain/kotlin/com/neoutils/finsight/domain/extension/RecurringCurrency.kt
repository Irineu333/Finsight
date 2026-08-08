package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency a recurring's amount is denominated in (design D17): **the account the
 * template names** — its own when it posts to one, the card's `LIABILITY` account when
 * it posts to a card. Never the base currency, which is a consolidation unit and not a
 * denomination a single figure may borrow (design D29).
 *
 * `null` when neither answers — a template whose account has gone missing. The figure is
 * then not shown at all: denominating it in the locale's currency would state, of money
 * nobody decided a currency for, a currency the user never chose.
 *
 * A `CreditCard` does not carry a currency: it names its account by
 * [CreditCard.accountId], and the account is the one place the currency is stated.
 */
internal suspend fun IAccountRepository.currencyOf(recurring: Recurring): String? =
    recurring.account?.currency
        ?: recurring.creditCard?.let { currencyOf(it) }

/** The currency of a card, read off the `LIABILITY` account it projects onto. */
internal suspend fun IAccountRepository.currencyOf(creditCard: CreditCard): String? =
    getAccountById(creditCard.accountId)?.currency
