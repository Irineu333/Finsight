@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryOverview
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.PartialMonthFigure
import com.neoutils.finsight.domain.model.SpendingVariation
import com.neoutils.finsight.domain.model.SpendingWindow
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.extension.displaySign
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.today
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The longest window a category is read against — a year of closed months. */
private const val MAX_WINDOW_MONTHS = 12

/** The two figures the variation puts on one scale. */
private enum class Compared { CURRENT_MONTH, WINDOW_AVERAGE }

/**
 * The figures the detail of a category reports: the current month, the window of closed
 * months it is read against, and how far apart the two are.
 *
 * **The window, the average and the variation are rules derivable from the domain, so
 * they have one owner and it is here** — not in a view model, and not in a composable.
 * It lives in categories' `api` because it is a rule of the category facade rather than
 * of consolidation, and the ledger and the reducer it needs are `:core:*`, which an `api`
 * already reaches.
 *
 * **The arithmetic happens per currency, before any conversion.** The window's total and
 * average are sums and a division over [MoneyByCurrency] — inside the ledger's own space,
 * with no rate involved — and only the *result* crosses the reducer, once per figure. That
 * is what keeps three separate rules intact at the same time: no `ConsolidatedAmount` is
 * ever added to another, one conversion happens per figure instead of twelve roundings,
 * and each figure is converted at the rates of the period **it is about**, so a past figure
 * does not move when a rate changes.
 *
 * **Nothing dated in the future reaches any figure.** A purchase in instalments writes one
 * transaction per month ahead, and the nominal leg of each carries the category's
 * dimension, so a category used on a card has future months populated. The cut is made in
 * the read itself — the series is asked for up to the current month — rather than filtered
 * afterwards, so no consumer of it can forget.
 */
class CalculateCategoryOverviewUseCase(
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke(category: Category): CategoryOverview {
        val currentMonth = clock.currentYearMonth()
        // The natural balance is debit-positive; the display sign is the one a category
        // already reads by, and nothing new about sign is introduced here.
        val displaySign = category.type.accountType.displaySign
        val series = entryRepository
            .dimensionMonthlySeriesByCurrency(dimensionId = category.dimensionId, upTo = currentMonth)
            .mapValues { (_, natural) -> natural.signedBy(displaySign) }

        if (series.isEmpty()) return CategoryOverview.Empty

        if (category.isArchived) return series.archivedOverview()

        return series.activeOverview(currentMonth)
    }

    /**
     * The whole history in one figure, over the range it covers. The range runs from the
     * first entry to the **last** one — the series' first and last rows, at no extra read
     * — and both are already inside the cut, so neither can name a month that has not
     * arrived.
     */
    private suspend fun Map<YearMonth, MoneyByCurrency>.archivedOverview(): CategoryOverview {
        val lastMonth = keys.last()
        return CategoryOverview.Archived(
            // Converted at the rates of the period it is about, like every other figure
            // here: the past does not move when a rate does.
            total = values.sum().consolidatedOn(lastMonth),
            firstMonth = keys.first(),
            lastMonth = lastMonth,
        )
    }

    private suspend fun Map<YearMonth, MoneyByCurrency>.activeOverview(
        currentMonth: YearMonth,
    ): CategoryOverview {
        val currentMoney = this[currentMonth] ?: MoneyByCurrency.zero
        val currentFigure = PartialMonthFigure(
            amount = currentMoney.consolidatedOn(currentMonth),
            elapsedDay = clock.today().day,
            daysInMonth = currentMonth.numberOfDays,
        )

        // Shortened to the category's own age, and the count declared travels with the
        // figures: a window of twelve in the label and of five in the divisor would read
        // as spending a fifth of what is spent. The first entry comes from the series
        // itself, already cut, so it is never a future month.
        val windowMonths = minOf(MAX_WINDOW_MONTHS, keys.first().monthsUntil(currentMonth))
        if (windowMonths == 0) {
            return CategoryOverview.Active(
                currentMonth = currentFigure,
                window = null,
                variation = SpendingVariation.Absent.NO_CLOSED_MONTH,
            )
        }

        // The closed months of the window, the current one deliberately left out: it is
        // half a month, and including it would drag the average down every first of the
        // month — and break `average × months = total`.
        val totalMoney = filterKeys { it.monthsUntil(currentMonth) in 1..windowMonths }
            .values
            .sum()
        // Divided per currency, before any rate: a month with no entry is not a missing
        // divisor but a zero, which is what dividing by the window's own length gives.
        val averageMoney = MoneyByCurrency.of(
            totalMoney.toList().associate { it.currency to it.value / windowMonths },
        )
        val windowEnd = currentMonth.minusMonth()

        return CategoryOverview.Active(
            currentMonth = currentFigure,
            window = SpendingWindow(
                months = windowMonths,
                average = averageMoney.consolidatedOn(windowEnd),
                total = totalMoney.consolidatedOn(windowEnd),
            ),
            variation = variationOf(currentMoney, averageMoney, currentMonth),
        )
    }

    /**
     * How far the current month stands from the average, as a fraction of it.
     *
     * The percentage comes off the **comparative scale**, not off the figures the user
     * reads: a ranking is not money, carries no currency and no exactness, and it is what
     * lets two figures be compared at all. Both are placed on it at **one** date — put on
     * scales of different dates, their difference would mix a change in spending with a
     * change in exchange rate.
     *
     * A missing magnitude propagates as an absence — named here, rendered nowhere — and
     * never as `0%`.
     */
    private suspend fun variationOf(
        currentMoney: MoneyByCurrency,
        averageMoney: MoneyByCurrency,
        currentMonth: YearMonth,
    ): SpendingVariation {
        val magnitudes = consolidateMoney.comparativeMagnitudes(
            figures = mapOf(
                Compared.CURRENT_MONTH to currentMoney,
                Compared.WINDOW_AVERAGE to averageMoney,
            ),
            on = currentMonth.lastDay,
        )
        val current = magnitudes.magnitudeOf(Compared.CURRENT_MONTH)
        val average = magnitudes.magnitudeOf(Compared.WINDOW_AVERAGE)
        return when {
            current == null || average == null -> SpendingVariation.Absent.NO_COMMON_SCALE
            average == 0.0 -> SpendingVariation.Absent.ZERO_AVERAGE
            else -> SpendingVariation.Measured((current - average) / average)
        }
    }

    /** One figure, reduced once, at the rates of the month it speaks about. */
    private suspend fun MoneyByCurrency.consolidatedOn(month: YearMonth) = consolidateMoney(
        money = this,
        on = month.lastDay,
        policy = DisplayAmount::magnitude,
    )
}

/** Each currency with its own, by the ledger's single implementation of the sum. */
private fun Iterable<MoneyByCurrency>.sum(): MoneyByCurrency =
    fold(MoneyByCurrency.zero, MoneyByCurrency::plus)

/** The ledger's debit-positive figure as the category reads it. */
private fun MoneyByCurrency.signedBy(displaySign: Int): MoneyByCurrency =
    MoneyByCurrency.of(toList().associate { it.currency to it.value * displaySign })
