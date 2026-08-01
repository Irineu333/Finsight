package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.DeviceRegion

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
 * The curated catalog bars a currency the app does not offer, which falls into the same
 * silent case rather than being coerced into the currency of last resort.
 *
 * It is resolved here, where both the device and the catalog are visible, and handed to
 * `core/database` as a plain code: the migration needs a currency, not a locale and not
 * a catalog. That is the same move `DimensionWriteGuard` already makes in the ledger —
 * the module below receives what it may not name.
 */
fun legacyRelabelCurrency(region: DeviceRegion): String? =
    CurrencyCatalog.of(region.currencyCode())?.code?.takeIf { it != LEGACY_DENOMINATION }

/**
 * [legacyRelabelCurrency] as something `core/database` can ask for.
 *
 * The migration's module may not name [DeviceRegion] — `core/database` does not depend on
 * `:core:common`, and giving it that dependency to read a country would be a wide door
 * opened for a narrow reason. It already names this module, so the resolution is bound
 * here, where the device and the catalog both are, and arrives there as a plain code.
 */
fun interface LegacyRelabel {

    fun currency(): String?
}
