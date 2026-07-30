package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

/**
 * One observation about the world: what [currency] was worth against the user's base
 * currency on [date].
 *
 * **It is dated, and that is the whole point.** Without a date, December's net worth
 * is recomputed at today's rate and *moves on its own* when the rate changes — the
 * past stops being stable. Consolidating a figure of some period uses the last rate
 * **on or before** that period's date, which is the deterministic policy every
 * double-entry system that keeps prices converged on.
 *
 * **It does not name the base currency.** A rate is stored one-way, currency → base,
 * and the base is a display preference rather than a fact of the row. Changing it does
 * not invalidate the archive: the rate of the old base against the new one is the
 * inverse of one already here, and the rest re-express by triangulation over rates of
 * the same date. That is derivation, not migration — no stored row changes.
 */
@Entity(
    tableName = "exchange_rates",
    indices = [
        // A surrogate key with a unique triple, not a composite primary key. The
        // reason is [Source.USER] winning over [Source.DERIVED] on the same date: for
        // that precedence to mean anything, both origins have to be able to coexist on
        // the same `(currency, date)` — otherwise correcting a rate would silently
        // destroy the one the operation itself observed.
        Index(value = ["currency", "date", "source"], unique = true),
        Index(value = ["currency", "date"]),
    ],
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO 4217 code of the currency being priced. */
    val currency: String,
    /** The day this rate is an observation about. */
    val date: LocalDate,
    /**
     * Units of the **base** currency per **one** unit of [currency] — with a base of
     * BRL, the dollar at `5.50`.
     *
     * Stored as the **full quotient**, derived in cents from the two legs of the
     * operation that observed it, and **never** the rounded form a screen shows. The
     * four decimal places of the rates screen are a formatting decision with an owner
     * of its own; confusing the two would make every display a compounding loss of
     * precision. Fixing the direction matters for the same reason: the inverse is not
     * the same rounding decision, and half a table in each direction is a table with
     * no authority.
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
