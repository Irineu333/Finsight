package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.CurrencyScoped
import com.neoutils.finsight.domain.model.MoneyByCurrency

private const val CENTS_PER_UNIT = 100.0

/**
 * One field of a `GROUP BY currency` aggregate, lifted from cents to a figure per
 * currency — the single path [CurrencyScoped] exists to make possible.
 *
 * The row list carries several figures at once (income *and* expense *and* …), so each
 * is read out with its own [value] selector rather than by mapping the list once.
 *
 * [negated] is for the projections whose column is credit-natured: an `INCOME` account
 * holds its amount negative, and a figure the app reads as a magnitude turns it round
 * here rather than at each surface that displays it.
 *
 * **Public, and read by facades as well as by the ledger.** Cents are the ledger's
 * storage convention, and dividing by a hundred at each projection is that convention
 * copied into whichever module happens to write the next aggregate — where nothing keeps
 * it in step. A facade whose own table fronts a ledger read (`recurring_occurrences`
 * joined to `entries`) lifts its rows through here for the same reason.
 */
fun <T : CurrencyScoped> List<T>.toMoney(
    negated: Boolean = false,
    value: (T) -> Long,
): MoneyByCurrency = MoneyByCurrency.of(
    associate { it.currency to (if (negated) -value(it) else value(it)) / CENTS_PER_UNIT },
)
