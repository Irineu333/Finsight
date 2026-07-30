package com.neoutils.finsight.extension

/**
 * The ISO 4217 code of the currency the **device's region** uses, or `null` when the
 * platform cannot name one.
 *
 * This adds no machinery: the app already derives a currency from the locale, and it
 * is exactly what `NumberFormat.getCurrencyInstance()` and `NSLocale.currentLocale`
 * do inside `CurrencyFormatter` today — which is why a device in `en-US` renders `$`
 * over values denominated in reais. The derivation existed; what was missing was
 * using it to **decide** rather than to format (design D28).
 *
 * **The region decides, not the language.** An interface in English on a device whose
 * region is Brazil answers `BRL`, because the currency of a locale is a property of
 * its country. That narrowing is what keeps the legacy relabelling of design D30 from
 * firing on someone who merely reads English.
 *
 * The code returned is **raw** — whatever the platform says, including a currency
 * this app does not offer. Reducing it to one the app can denominate an account in is
 * a different job with a different owner, and it lives beside the catalog in
 * `:core:model`: this module knows what the device says, that one knows what the app
 * accepts.
 */
expect fun localeCurrencyCode(): String?
