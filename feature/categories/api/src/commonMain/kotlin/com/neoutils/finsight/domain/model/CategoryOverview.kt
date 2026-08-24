package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.ConsolidatedAmount
import kotlinx.datetime.YearMonth

/**
 * What the detail of a category reports — already decided, so the surface that renders
 * it chooses nothing.
 *
 * The state a category is in changes **which figure is the highlight** and **whether a
 * variation exists at all**, so it is a variant here rather than an `if` on the screen.
 * Were it a condition in a composable, the same decision would exist again in the view
 * model's test, and once more the day a second surface showed the same category — which
 * is how two screens come to disagree about one category.
 */
sealed interface CategoryOverview {

    /**
     * A category nothing was ever posted to. No figure at all, rather than a zero: a
     * zero in the highlight reads as a failure, and this is an absence.
     */
    data object Empty : CategoryOverview

    /**
     * A live category: the current month is the highlight, because it is the only period
     * the user can still act on.
     *
     * [window] and [variation] are two faces of one fact and are produced together:
     * a category whose first entry falls in the current month has no closed month, so it
     * has no window *and* no base to compare against — `null` here comes with
     * [SpendingVariation.Absent.NO_CLOSED_MONTH] there, never with a percentage.
     */
    data class Active(
        val currentMonth: PartialMonthFigure,
        val window: SpendingWindow?,
        val variation: SpendingVariation,
    ) : CategoryOverview

    /**
     * An archived category: the current month says nothing about it — showing it yields a
     * zero and a −100% that describe nothing — so the highlight is the whole history, over
     * the range it actually covers.
     *
     * The range ends at the **last entry**, never at the archiving: archiving has no date
     * to read, `Category` carrying `isArchived` and nothing else. Its resolution is the
     * month because the series it falls out of has that resolution, and because the range
     * is an assertion about the money rather than about the day it moved.
     */
    data class Archived(
        val total: ConsolidatedAmount,
        val firstMonth: YearMonth,
        val lastMonth: YearMonth,
    ) : CategoryOverview
}

/**
 * The current month's figure, together with what makes it incomparable to the closed
 * ones: the month has not finished.
 *
 * [elapsedDay] and [daysInMonth] are carried rather than derived by the surface, because
 * a screen that read a clock of its own could announce a different day from the one the
 * figure was computed on.
 */
data class PartialMonthFigure(
    val amount: ConsolidatedAmount,
    val elapsedDay: Int,
    val daysInMonth: Int,
)

/**
 * The closed-month window a category is read against: [months] of it, their [total], and
 * the [average] that is that total divided by that same number.
 *
 * [months] is the real count and travels with the figures precisely so the label can
 * declare it. A window of twelve in the text and of five in the divisor is the quietest
 * way for a figure to lie, and a category five months old would otherwise read as
 * spending a fifth of what it spends.
 *
 * Because both figures come from one window, `average × months = total` holds and the
 * user can check it.
 */
data class SpendingWindow(
    val months: Int,
    val average: ConsolidatedAmount,
    val total: ConsolidatedAmount,
)

/**
 * How the current month stands against the window's average — never against the month
 * before it, which may itself have been atypical and would then become the ruler of
 * every month after.
 */
sealed interface SpendingVariation {

    /** [fraction] is signed and relative to the average: `0.23` is 23% above it. */
    data class Measured(val fraction: Double) : SpendingVariation {
        val isAbove: Boolean get() = fraction > 0.0
    }

    /**
     * There is no answer, and which non-answer it is gets said in words. None of these
     * is ever rendered as `0%`: zero is an assertion.
     */
    enum class Absent : SpendingVariation {
        /** The window's average is zero — dividing by it is not an infinite rise. */
        ZERO_AVERAGE,

        /** No closed month carries an entry, so there is no base at all. */
        NO_CLOSED_MONTH,

        /** The two figures share no scale: one holds a currency no rate reaches. */
        NO_COMMON_SCALE,
    }
}
