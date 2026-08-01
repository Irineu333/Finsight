package com.neoutils.finsight.domain.usecase

/**
 * Which currencies the user actually holds, and which one they most likely spend in.
 *
 * @param inUse every distinct currency across the user's accounts **and cards** — one
 * entry while the app is single-currency, which is what tells a form there is no choice
 * to offer at all (design D13).
 * @param ofDefaultAccount the currency of the default account, or `null` before one
 * exists. It is the suggestion, and the base currency deliberately is not: the base
 * answers *which currency the user reads totals in*, not *which one they spend in*.
 */
data class AccountCurrencies(
    val inUse: List<String>,
    val ofDefaultAccount: String?,
)

/**
 * The single owner of "which currencies does this user have".
 *
 * It lives here, and not in the feature that asks, because the answer is derived from
 * the chart of accounts and a derived rule has exactly one owner. A budget form
 * assembling it from its own reads would get it wrong in the one case it exists to
 * serve: a user whose foreign spending is all on a card, whose currency the account
 * listing does not report at all.
 *
 * It lives in the **consolidation layer**, and not in `feature/accounts/api`, because it
 * has two consumers that cannot see each other: the budget form, which offers the choice
 * only when there is one to make (design D13), and [ConsolidateMoneyUseCase], which needs
 * it to know what a figure of nothing is denominated in (design D29). `api ⊄ api` and
 * `core ⊄ feature`, so the one owner has to be here for both to reach it. The
 * implementation stays with the chart of accounts, in the accounts feature.
 */
interface GetAccountCurrenciesUseCase {
    suspend operator fun invoke(): AccountCurrencies
}
