package com.neoutils.finsight.domain.model

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
 * `CurrencyFormatter` always formatted by the device locale, so a user in the United
 * States **always saw `$`** while their data said BRL. The divergence never reached the
 * screen. It would now: the data's currency decides the symbol (design D10) and is
 * immutable (D12), so without this every user outside Brazil would watch the whole app
 * turn into `R$` with no way back — their accounts have entries, so they cannot be
 * deleted and recreated. Relabelling makes the data say what the user always read.
 *
 * **The region decides, not the language** — an interface in English on a device whose
 * region is Brazil answers `BRL` and nothing fires. And the curated catalog bars a
 * currency the app does not offer, which falls into the silent case of keeping the
 * legacy denomination rather than being coerced into the currency of last resort.
 *
 * It is resolved here, where both the device and the catalog are visible, and handed to
 * `core/database` as a plain code: the migration needs a currency, not a locale and not
 * a catalog. That is the same move `DimensionWriteGuard` already makes in the ledger —
 * the module below receives what it may not name.
 */
fun legacyRelabelCurrency(): String? =
    CurrencyCatalog.of(localeCurrencyCode())?.code?.takeIf { it != LEGACY_DENOMINATION }
