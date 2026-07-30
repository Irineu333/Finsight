package com.neoutils.finsight.database.entity

import androidx.room.Entity
import kotlinx.datetime.LocalDate

/**
 * One exchange rate, on one date, against the user's base currency.
 *
 * A table of `(currency, date, rate)` rather than a single current value, because without
 * the date December's net worth is recalculated at today's rate and **moves on its own**
 * when the rate changes — the past stops being stable. Every system that keeps prices does
 * it this way (GnuCash's `GNCPrice`, Beancount's `price`, Firefly's
 * `currency_exchange_rates`, hledger's `P`), and the reading policy here is theirs too: the
 * last rate on or before the date asked about.
 *
 * [rate] is how many units of the base currency one unit of [currency] is worth. The base
 * currency itself is never a row: it is worth one of itself by definition, and a row saying
 * so could be edited into saying otherwise.
 */
@Entity(tableName = "exchange_rates", primaryKeys = ["currency", "date", "source"])
data class ExchangeRateEntity(
    val currency: String,
    val date: LocalDate,
    val rate: Double,
    val source: Source,
) {
    /**
     * Where the rate came from. It is part of the key rather than a note, because the two
     * can legitimately disagree on the same date and the answer is decided by which is
     * which: the rate a person typed wins over one derived from an operation, on that date.
     *
     * [OPERATION] costs nothing to collect — a cross-currency operation states both ends, so
     * the rate is already there and the user never types the same one twice.
     */
    enum class Source {
        /** Derived from the two ends of a cross-currency operation, on its date. */
        OPERATION,

        /** Entered by the user on the rates screen. It prevails on its own date. */
        USER,
    }
}
