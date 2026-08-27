package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.RecurringMonthOverview
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.YearMonth

/**
 * The month's four figures, reduced.
 *
 * A function and not a second view model, in the shape `BalanceOverviewFactory` already
 * settled on: the reducer arrives as a parameter, the view model stays a wiring class,
 * and the domain that decided *which money belongs where* is left knowing nothing about
 * the currency it will be read in. Consolidating inside the use case would tie the owner
 * of the rule to a display preference.
 *
 * **Every figure spans accounts, so every one of them leaves through the reducer** — the
 * base currency is its to name, never this file's. A figure tagged with the base at the
 * point of formatting is indistinguishable, for a user whose accounts are all in the
 * base, from one that was properly reduced (design D29).
 *
 * **All four are magnitudes, and that is a reconciliation, not an oversight.** The rule
 * that a summary line must show its effect on the sum above it applies, by its own
 * terms, to a line that takes part in a displayed sum — and none of these does: the card
 * shows no total, between the blocks or inside them. A sign here would be typography
 * borrowed from a policy whose meaning is something else. **If the card ever shows a
 * total, all four must take a sign**, and this comment is where that is decided.
 *
 * @param month the month on the card. Its own last day is the reference date for every
 * figure, the fact included — a month is consolidated at that month's rates, or the past
 * would move on its own whenever a rate changed.
 */
internal suspend fun RecurringMonthOverview.toSummary(
    month: YearMonth,
    consolidate: ConsolidateMoneyUseCase,
): RecurringMonthSummary {
    suspend fun figure(money: MoneyByCurrency) = consolidate(
        money = money,
        on = month.lastDay,
        policy = DisplayAmount::magnitude,
    )

    return RecurringMonthSummary(
        settledExpense = figure(settledExpense),
        settledIncome = figure(settledIncome),
        forecastExpense = figure(forecastExpense),
        forecastIncome = figure(forecastIncome),
        undenominated = undenominated,
    )
}
