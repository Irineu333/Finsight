package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.DeviceRegion
import com.neoutils.finsight.extension.isTwoDecimalCurrency

/**
 * The currency every row of every existing database is denominated in — not because
 * anybody chose it, but because it was the model's default.
 */
const val LEGACY_DENOMINATION: String = "BRL"

/**
 * What the legacy chart of accounts should be **re-denominated** to, or `null` for
 * "leave it alone" — which is the common case.
 *
 * `CurrencyFormatter` always formatted by the device locale, so a user in the United
 * States **always saw `$`** while their data said BRL. The divergence never reached the
 * screen. It would now: the data's currency decides the symbol (design D10) and is
 * immutable (D12), so without this every user outside Brazil would watch the whole app
 * turn into `R$` with no way back — their accounts have entries, so they cannot be
 * deleted and recreated. Relabelling makes the data say what the user always read.
 *
 * **Only a statement about location may fire this** — hence [DeviceRegion] and not the
 * locale. The locale carries a country because a language is written differently in
 * different places, and on Android that country is simply the top of the language list:
 * a Brazilian reading the interface in *English (United States)* has a locale that says
 * `US` over a database of reais, and relabelling on it would re-denominate every row they
 * own, irreversibly, because they read English. A device that cannot state where it is
 * answers `null`, and `null` here means what it has always meant — leave it alone.
 *
 * **What bars a currency here is the two-decimal premise, and not a list.** It used to be
 * the curated catalog, and that was a curation doing a rule's job; now that the offered
 * set is a table, "what the app admits" is the base-100 premise, which the platform
 * answers for a code — with no table, no database and no ordering.
 *
 * That the table is not consulted is what makes this work at all. The relabel is
 * migration `10 → 11` and the currency seeding can only be `12 → 13`, so on an upgrade
 * from v10 the relabel runs *before* the table exists, and no ordering fixes that without
 * rewriting a published migration. The two fit together from the other direction instead:
 * this writes `accounts.currency`, and the seeding reads `SELECT DISTINCT currency FROM
 * accounts` — so whatever this denominates is seeded as a consequence, without either
 * migration knowing the other.
 *
 * A currency the platform declares to have zero or three decimal places falls into the
 * same silent case as no region at all, rather than being coerced into the currency of
 * last resort.
 *
 * It is resolved here, where both the device and the premise are visible, and handed to
 * `core/database` as a plain code: the migration needs a currency, not a locale. That is
 * the same move `DimensionWriteGuard` already makes in the ledger — the module below
 * receives what it may not name.
 */
fun legacyRelabelCurrency(region: DeviceRegion): String? =
    region.currencyCode()
        ?.uppercase()
        ?.takeIf { it != LEGACY_DENOMINATION && isTwoDecimalCurrency(it) }

/**
 * [legacyRelabelCurrency] as something `core/database` can ask for.
 *
 * The migration's module may not name [DeviceRegion] — `core/database` does not depend on
 * `:core:common`, and giving it that dependency to read a country would be a wide door
 * opened for a narrow reason. It already names this module, so the resolution is bound
 * here, where the device is visible, and arrives there as a plain code.
 */
fun interface LegacyRelabel {

    fun currency(): String?
}
