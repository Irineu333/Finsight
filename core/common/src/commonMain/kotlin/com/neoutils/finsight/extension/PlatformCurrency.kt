package com.neoutils.finsight.extension

/**
 * What the **platform** knows about a currency code: how it names it in the current
 * language, the glyph it suggests, and how many decimal places it declares.
 *
 * Answering `null` is the normal outcome for a code the platform does not recognise —
 * a made-up one, or an ISO code the operating system's version does not carry. Nothing
 * here throws, and the worst case degrades to the code itself, which is what
 * `CurrencyFormatter` already does.
 *
 * **This names one code; it never enumerates them.** Which currencies the app offers is
 * stored data, and asking the platform for the *set* would give a different answer per
 * operating system and per version — the same user would see different lists on Android
 * and on the desktop. What is asked here is a question about a row that already exists.
 */
expect fun platformCurrency(code: String): PlatformCurrency?

/**
 * The platform's answer about one code.
 *
 * [name] is resolved **at every read**, in the current language: storing it would freeze
 * it in the language of the first run, and switching the app's language would silently
 * stop translating it.
 */
data class PlatformCurrency(
    val code: String,
    val name: String,
    val symbol: String,
    val fractionDigits: Int,
)

/**
 * Whether the platform declares this code to have the **two** decimal places the app's
 * arithmetic assumes — `false` only when it declares otherwise. A code the platform does
 * not know is not refused here: the app allows currencies it invented, and there is
 * nothing to contradict.
 */
fun isTwoDecimalCurrency(code: String): Boolean =
    platformCurrency(code)?.fractionDigits?.let { it == 2 } ?: true
