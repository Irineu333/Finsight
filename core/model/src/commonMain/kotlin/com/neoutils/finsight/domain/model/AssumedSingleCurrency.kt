package com.neoutils.finsight.domain.model

/**
 * The one currency the app has **at this point in the plan** — a marker for a figure
 * whose currency is not knowable at the site that renders it yet.
 *
 * It exists because making the currency mandatory in `DisplayAmount` and in
 * `CurrencyFormatter` reaches every money site at once, while the answers arrive in
 * stages: a site holding an `Account` passes `account.currency` and is done for good, but
 * a site that **aggregates across accounts** (net worth, month totals, a category's
 * spending) or reads a facade that carries no currency yet (a card's limit, a recurring's
 * amount, a budget's limit) has no honest answer until the per-currency reads and the
 * resolved base currency exist.
 *
 * Naming that non-answer is the point. `BASE_CURRENCY` has legitimate uses that are *not*
 * this one, so it cannot be told apart by grep; and the failure this whole change guards
 * against is precisely a figure showing the base currency "because it was at hand".
 *
 * **This constant is removed by the task that closes the inventory.** Its own deletion is
 * the done criterion: every use is eliminated as its group arrives — the facade gains a
 * currency, or the site becomes a genuinely consolidated figure and reads the resolved
 * base — and when none is left, deleting it compiles. That is a stronger check than a
 * hand-kept list, and it is why the placeholder cannot quietly become permanent.
 */
const val ASSUMED_SINGLE_CURRENCY: String = BASE_CURRENCY
