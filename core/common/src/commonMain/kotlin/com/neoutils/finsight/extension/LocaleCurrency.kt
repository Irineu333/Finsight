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
 * The read is right for what it decides here: a **pre-selection**, which the user sees and
 * can change before anything is written. It is not enough to re-denominate a database that
 * already exists, and that is [DeviceRegion]'s job, deliberately separate from this one.
 *
 * The code returned is **raw** — whatever the platform says, including a currency
 * this app does not offer. Reducing it to one the app can denominate an account in is
 * a different job with a different owner, and it lives beside the catalog in
 * `:core:model`: this module knows what the device says, that one knows what the app
 * accepts.
 */
expect fun localeCurrencyCode(): String?
