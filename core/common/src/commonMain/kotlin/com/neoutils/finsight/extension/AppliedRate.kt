package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/**
 * A rate that actually took part in a figure: how many units of [baseCurrency] one unit of
 * [currency] was worth, and **the date of the quote used** — which is the last one on or
 * before the figure's own date, and so is often older than it.
 *
 * It travels inside the figure rather than being looked up again beside it. Asking the rate
 * repository a second time would be a second decision about which quote governs this number,
 * and two decisions drift: the figure was reduced at one quote and the footer would explain
 * it with another. This is the same failure `DisplayAmount` exists to rule out — the value
 * travelling apart from its legend — and a rate is legend.
 *
 * [baseCurrency] is carried for the same reason the rate is: [rate] is a price, and a price
 * read without the currency it is priced in is the very thing this file is about.
 */
@Immutable
class AppliedRate(
    val currency: String,
    val baseCurrency: String,
    val rate: Double,
    val date: LocalDate,
) {
    override fun equals(other: Any?) = other is AppliedRate &&
            currency == other.currency &&
            baseCurrency == other.baseCurrency &&
            rate == other.rate &&
            date == other.date

    override fun hashCode(): Int {
        var result = currency.hashCode()
        result = 31 * result + baseCurrency.hashCode()
        result = 31 * result + rate.hashCode()
        result = 31 * result + date.hashCode()
        return result
    }

    override fun toString() = "AppliedRate($currency, $baseCurrency, $rate, $date)"
}
