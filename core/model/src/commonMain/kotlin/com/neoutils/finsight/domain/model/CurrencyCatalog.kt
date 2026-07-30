package com.neoutils.finsight.domain.model

/**
 * The currencies the app offers, curated and **all of two decimal places**.
 *
 * The restriction is a recorded premise and not an oversight. Every amount in this app is
 * held and written as a minor unit at base 100 — cents — on both sides of the ledger, so a
 * currency of zero decimals (yen, won, peso chileno) or of three (dinar kuwaitiano) would
 * need that boundary rebuilt, not merely a formatter told about it. Offering one before that
 * happens would show a figure a hundred times off.
 *
 * The catalog belongs to this layer and **not to the ledger**, which persists only the code:
 * which currencies a product offers is a curation decision, and the ledger holds facts.
 *
 * Curated rather than exhaustive: the list is what a person plausibly holds money in, and
 * adding a code to it costs nothing beyond checking that it is exponent 2.
 */
object CurrencyCatalog {

    /**
     * Offered in this order — codes only. The symbol and the separators come from the
     * formatter, which is where the locale legitimately decides.
     */
    val offered: List<String> = listOf(
        "BRL", "USD", "EUR", "GBP", "CHF",
        "CAD", "AUD", "NZD",
        "ARS", "UYU", "PEN", "COP", "MXN",
        "ZAR", "INR", "CNY", "HKD", "SGD", "PHP", "THB", "MYR", "IDR",
        "SEK", "NOK", "DKK", "PLN", "CZK", "RON", "TRY", "ILS",
    )

    /** Whether a code is one the app is willing to denominate a figure in. */
    fun offers(currency: String): Boolean = currency in offered
}
