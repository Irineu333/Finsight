package com.neoutils.finsight.extension

/**
 * How a currency code is shown to a person: its name in the reader's language, and the glyph
 * that stands for it.
 *
 * It comes from the platform rather than from string resources, and that is the decision this
 * KDoc records. A hand-written table would be one entry per offered currency **per language**,
 * kept in step by hand, while every platform already ships the ICU data that answers exactly
 * this — and answers it for languages the app has no resources for. It is the same mechanism
 * [deviceCurrencyCode] and [CurrencyFormatter] already use, so a third one that could disagree
 * with them never appears.
 *
 * Both fall back to the **code itself**, which is never wrong and is what a reader who knows
 * the code expects: `USD`, `US$`. A currency the platform cannot name is still nameable.
 */
expect fun currencyDisplayName(currency: String): String

expect fun currencySymbol(currency: String): String
