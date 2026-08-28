package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency a card's figures are denominated in (design D17): the `LIABILITY` account
 * it projects onto. Its limit, its invoices, its installments and its adjustments all
 * read in that account's currency — never in the base one, which is a consolidation unit
 * and not a denomination a single figure may borrow (design D29).
 *
 * A card does not carry one: it names its account by [CreditCard.accountId], and the
 * account is the single place a currency is stated.
 *
 * `null` when that account cannot be resolved. Whether the absence is a figure to
 * withhold or a broken invariant is the **caller's** reading, not this rule's, and a
 * caller that takes the second wraps this one rather than resolving the account again —
 * see `requireCurrencyOf` in the credit cards feature.
 */
suspend fun IAccountRepository.currencyOf(creditCard: CreditCard): String? =
    getAccountById(creditCard.accountId)?.currency

/**
 * The currency a recurring's amount is denominated in (design D17): **the account the
 * template names** — its own when it posts to one, the card's when it posts to a card.
 *
 * `null` when neither answers — a template whose account has gone missing. What a
 * surface does with that is its own decision: a list row says so out loud, and a total
 * leaves the template out and declares how many it left out. What none of them may do is
 * denominate it in the locale's currency, which would state, of money nobody decided a
 * currency for, a currency the user never chose.
 *
 * **Why the two live beside [IAccountRepository] and not with the features that ask.**
 * Four `impl`s need one or the other — the recurring list, the recurring forms, the
 * credit card screens and the dashboard — and an `api` may not depend on another `api`,
 * so a home inside any one of those features would be unreachable from the rest. The
 * chart of accounts is also what actually answers the question: the receiver is the
 * repository, and the facade is only what is being asked about.
 */
suspend fun IAccountRepository.currencyOf(recurring: Recurring): String? =
    recurring.currencyBy { currencyOf(it) }

/**
 * The same rule, with **where the card's currency is read** left to the caller.
 *
 * [cardCurrency] is the only thing that differs between the two ways of asking — a query
 * for a single template, a chart of accounts already in hand for a list of them — and
 * keeping it a parameter is what stops a list from growing a second copy of the rule in
 * order to avoid a query per row.
 *
 * A caller resolving a list reads the **whole** chart, not the account facade: the
 * account a card projects onto is a `LIABILITY` row, and the facade is `ASSET` only.
 */
inline fun Recurring.currencyBy(cardCurrency: (CreditCard) -> String?): String? =
    account?.currency ?: creditCard?.let(cardCurrency)
