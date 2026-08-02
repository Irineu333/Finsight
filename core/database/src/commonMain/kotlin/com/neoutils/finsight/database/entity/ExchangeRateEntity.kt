package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

/**
 * One observation about the world, on a **pair** of currencies: one unit of [currency]
 * was worth [rate] of [counterCurrency] on [date].
 *
 * **It is dated, and that is the whole point.** Without a date, December's net worth
 * is recomputed at today's rate and *moves on its own* when the rate changes — the
 * past stops being stable. Consolidating a figure of some period uses the last rate
 * **on or before** that period's date, which is the deterministic policy every
 * double-entry system that keeps prices converged on.
 *
 * **The row names both of its ends, so it reads on its own.** The base currency is a
 * display preference and leaves no trace here. A row whose meaning depended on the
 * preference in force would start saying something else the instant it changed —
 * silently rewriting the meaning of stored data, which is the class of defect the
 * ledger was built to make impossible.
 *
 * **The direction is the observation's, and it is never canonicalised on write.**
 * Ordering the ends and inverting the quotient to fit a canonical form would store a
 * number nobody observed — the same defect as storing the displayed form, applied to
 * the entrance, where there is no way to notice it. As a consequence the same pair may
 * exist in both directions, as two distinct observations; which one answers a given
 * question is the reader's decision, and it is declared.
 */
@Entity(
    tableName = "exchange_rates",
    indices = [
        // A surrogate key with a unique tuple, not a composite primary key. The
        // reason is [Source.USER] winning over [Source.DERIVED] on the same date: for
        // that precedence to mean anything, both origins have to be able to coexist on
        // the same `(currency, counterCurrency, date)` — otherwise correcting a rate
        // would silently destroy the one the operation itself observed.
        Index(value = ["currency", "counterCurrency", "date", "source"], unique = true),
        Index(value = ["currency", "counterCurrency", "date"]),
    ],
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO 4217 code of the currency being priced — the one the row answers *how much*. */
    val currency: String,
    /** ISO 4217 code of the currency [currency] is priced **in**. */
    val counterCurrency: String,
    /** The day this rate is an observation about. */
    val date: LocalDate,
    /**
     * Units of [counterCurrency] per **one** unit of [currency] — `USD`/`BRL` at
     * `5.50` reads *one dollar is worth 5.50 reais*.
     *
     * Stored as the **full quotient**, derived in cents from the two legs of the
     * operation that observed it, and **never** the rounded form a screen shows. The
     * four decimal places of the rates screen are a formatting decision with an owner
     * of its own; confusing the two would make every display a compounding loss of
     * precision.
     */
    val rate: Double,
    val source: Source,
) {
    /** Where a rate came from — which is what decides who wins on the same date. */
    enum class Source {
        /**
         * Derived from a transaction that crossed currencies: its two legs are the
         * observation, and the user never types the same rate twice. It survives the
         * transaction that revealed it — a rate is a fact about a day, not a property
         * of a posting (design D27).
         */
        DERIVED,

        /** Typed by the user, and it prevails over a [DERIVED] one of the same date. */
        USER,
    }
}
