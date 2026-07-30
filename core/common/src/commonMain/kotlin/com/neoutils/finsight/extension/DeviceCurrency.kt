package com.neoutils.finsight.extension

/**
 * The currency code of the device's locale, or `null` when the platform cannot name one.
 *
 * This is **mechanism, not policy**: it answers "what does this device's region spend in",
 * and nothing else. Whether the app is willing to denominate a figure in that code — and
 * what to do when it is not — belongs above, beside the catalog.
 *
 * The app already derived a currency from the locale; that is why a device in `en-US`
 * rendered `$` over amounts held in reais. What was missing was using the derivation to
 * **decide** once, instead of to format every time — so this reuses exactly the mechanism
 * [CurrencyFormatter] uses on each platform, rather than introducing a second one that
 * could disagree with it.
 */
expect fun deviceCurrencyCode(): String?
