package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency a recurring's amount is denominated in (design D17): **the account the
 * template names** — its own when it posts to one, the card's `LIABILITY` account when
 * it posts to a card. Never the base currency, which is a consolidation unit and not a
 * denomination a single figure may borrow (design D29).
 *
 * `null` when neither answers — a template whose account has gone missing. What a
 * surface does with that is its own decision: a list row says so out loud, and a total
 * leaves the template out and declares how many it left out. What none of them may do is
 * denominate it in the locale's currency, which would state, of money nobody decided a
 * currency for, a currency the user never chose.
 *
 * A `CreditCard` does not carry a currency: it names its account by
 * `accountId`, and the account is the one place the currency is stated.
 *
 * **Why it lives beside [IAccountRepository] and not with the recurring feature.** Three
 * `impl`s need the rule — the recurring list, the recurring detail and the dashboard —
 * and an `api` may not depend on another `api`, so a home inside `feature/recurring/api`
 * would be unreachable from the very module that had copied it. The chart of accounts is
 * also what actually answers the question: the receiver is the repository, and the
 * facade is only what is being asked about.
 */
suspend fun IAccountRepository.currencyOf(recurring: Recurring): String? =
    recurring.account?.currency
        ?: recurring.creditCard?.let { getAccountById(it.accountId)?.currency }
