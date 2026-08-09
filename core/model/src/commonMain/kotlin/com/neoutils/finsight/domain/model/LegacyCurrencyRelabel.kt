package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.isTwoDecimalCurrency
import com.neoutils.finsight.extension.localeCurrencyCode

/**
 * The currency every row of every existing database is denominated in — not because
 * anybody chose it, but because it was the model's default.
 */
const val LEGACY_DENOMINATION: String = "BRL"

/**
 * What the legacy chart of accounts should be **re-denominated** to, or `null` for
 * "leave it alone" — which is the common case.
 *
 * `CurrencyFormatter` always formatted by the device locale, so a user whose locale named
 * dollars **always saw `$`** while their data said BRL. The divergence never reached the
 * screen. It would now: the data's currency decides the symbol (design D10) and is
 * immutable (D12), so without this every such user would watch the whole app turn into
 * `R$` with no way back — their accounts have entries, so they cannot be deleted and
 * recreated. Relabelling makes the data say what the user always read.
 *
 * **The signal is the locale, because the locale is what produced the reading.** This is
 * not an inference about where the user is; it is the *same read* the old formatter
 * performed — `NumberFormat.getCurrencyInstance()` on the JVM and Android,
 * `NSLocale.currentLocale` on iOS — which is what [localeCurrencyCode] answers. Any other
 * signal is a second guess at a question the first one already answered, and can
 * therefore disagree with it: a device whose network says one thing and whose locale says
 * another rendered the locale's symbol, every time, for as long as the user has had the
 * app. Relabelling by anything else is the only way to *change* what someone sees, which
 * is the one thing this must not do.
 *
 * **So a Brazilian reading the interface in English is relabelled, and that is the
 * correct outcome.** On Android there is no language without a country, so *English
 * (United States)* is `en-US` — and that user has been reading `$` over their reais all
 * along. Leaving them in BRL is the option that changes their screen; relabelling is the
 * one that does not. What no signal on the device can recover is the *intent* behind the
 * numbers, and the locale of today does not report the locale of yesterday: the residual
 * false positive is narrow and stated — someone who switched the interface language
 * shortly before updating is relabelled to the currency they have been reading since they
 * switched.
 *
 * **What bars a currency here is the two-decimal premise, and not a list.** It used to be
 * the curated catalog, and that was a curation doing a rule's job; now that the offered
 * set is a table, "what the app admits" is the base-100 premise, which the platform
 * answers for a code — with no table, no database and no ordering.
 *
 * That the table is not consulted is what makes this work at all. The relabel is
 * migration `11 → 12` and the currency seeding can only be `13 → 14`, so on an upgrade
 * from v10 the relabel runs *before* the table exists, and no ordering fixes that without
 * rewriting a published migration. The two fit together from the other direction instead:
 * this writes `accounts.currency`, and the seeding reads `SELECT DISTINCT currency FROM
 * accounts` — so whatever this denominates is seeded as a consequence, without either
 * migration knowing the other.
 *
 * A locale the platform cannot name a currency for, and a currency it declares to have
 * zero or three decimal places, fall into the same silent case: nothing is relabelled,
 * rather than the data being coerced into the currency of last resort.
 *
 * It is resolved here, where both the device and the premise are visible, and handed to
 * `core/database` as a plain code: the migration needs a currency, not a locale. That is
 * the same move `DimensionWriteGuard` already makes in the ledger — the module below
 * receives what it may not name.
 */
fun legacyRelabelCurrency(): String? = legacyRelabelCurrency(localeCurrencyCode())

/**
 * The rule itself, over an already-read code.
 *
 * The parameter is what lets the narrowing be proved without a platform to set a locale
 * on — the reading is one function call, and the rule is everything that can go wrong.
 */
fun legacyRelabelCurrency(deviceCurrency: String?): String? =
    deviceCurrency
        ?.uppercase()
        ?.takeIf { it != LEGACY_DENOMINATION && isTwoDecimalCurrency(it) }

/**
 * [legacyRelabelCurrency] as something `core/database` can ask for.
 *
 * The migration's module may not read the device — `core/database` does not depend on
 * `:core:common`, and giving it that dependency to read a locale would be a wide door
 * opened for a narrow reason. It already names this module, so the resolution is bound
 * here, where the device is visible, and arrives there as a plain code.
 */
fun interface LegacyRelabel {

    fun currency(): String?
}
