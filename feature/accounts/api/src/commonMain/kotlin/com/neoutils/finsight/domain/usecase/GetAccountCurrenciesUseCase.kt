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
 */
interface GetAccountCurrenciesUseCase {
    suspend operator fun invoke(): AccountCurrencies
}
