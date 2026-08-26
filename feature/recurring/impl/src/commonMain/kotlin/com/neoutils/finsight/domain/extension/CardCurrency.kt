package com.neoutils.finsight.domain.extension

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * The currency of a card, read off the `LIABILITY` account it projects onto (design
 * D17). A card does not carry one: it names its account by `accountId`, and the account
 * is the single place a currency is stated.
 *
 * `null` when the account cannot be resolved. The forms of this feature offer accounts
 * and cards in the template's own currency, and a target that answers nothing narrows
 * nothing — which is why the nullable reading is the one they take.
 *
 * The rule about a *recurring* — which of its two possible sources denominates it —
 * lives with `IAccountRepository`, in `feature/accounts/api`, because three features
 * need it and an `api` cannot see another `api`.
 */
internal suspend fun IAccountRepository.currencyOf(creditCard: CreditCard): String? =
    getAccountById(creditCard.accountId)?.currency
