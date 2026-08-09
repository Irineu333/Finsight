package com.neoutils.finsight.extension

/**
 * The ISO 4217 code of the currency the **device's locale** names, or `null` when the
 * platform cannot name one.
 *
 * This adds no machinery: the app already derives a currency from the locale, and it
 * is exactly what `NumberFormat.getCurrencyInstance()` and `NSLocale.currentLocale`
 * do inside `CurrencyFormatter` today — which is why a device in `en-US` renders `$`
 * over values denominated in reais. The derivation existed; what was missing was
 * using it to **decide** rather than to format (design D28).
 *
 * **It is the locale's country, and a locale's country is not a location.** The currency
 * of a locale is a property of its country, so an interface in English on a device set to
 * `en-BR` answers `BRL` — but on Android that country comes from the system language list,
 * where choosing *English (United States)* is `en-US` whatever the user's money is in.
 *
 * That is the right read for both of its callers, and for the same reason: neither asks
 * where the user is. The base currency is a **pre-selection**, which the user sees and can
 * change before anything is written; and the legacy relabel
 * (`legacyRelabelCurrency`) re-denominates an existing database precisely to keep the
 * symbol on screen from changing — so the signal it needs is the one that put the symbol
 * there, which is this one, and not a stronger statement that could disagree with it
 * (design D30).
 *
 * The code returned is **raw** — whatever the platform says, including a currency
 * this app does not offer. Reducing it to one the app can denominate an account in is
 * a different job with a different owner, and it lives beside the catalog in
 * `:core:model`: this module knows what the device says, that one knows what the app
 * accepts.
 */
expect fun localeCurrencyCode(): String?

/**
 * ISO 4217's code for **no currency**, which is what a platform answers for a locale
 * that names none — a language chosen without a country, most of it.
 *
 * It is translated to `null` here rather than being passed on, because it is not a
 * currency: it is the absence of one wearing a code. Letting it through would make every
 * caller re-discover that on its own, and the one that matters re-discovers it by
 * relabelling a database into it.
 */
internal const val NO_CURRENCY = "XXX"
