package com.neoutils.finsight.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth

/**
 * Where a month's balance sits on the calendar — the single owner of the rule that
 * turns a period into the day a balance adjustment is dated by.
 *
 * A screen chooses **which** shortcut to offer; it never decides which date that
 * shortcut means, and MUST NOT reimplement either projection.
 *
 * Both are the same expression: the projection of the context, capped at today. The cap
 * is what preserves the meaning of the gesture in the current month — a closing balance
 * for a month still running is a balance as of today, not one at a day that has not
 * happened. It is also why a future date can never be produced here.
 *
 * [today] is a parameter and never read from inside, so the caller's clock is the only
 * clock.
 */
fun openingBalanceDateOf(month: YearMonth, today: LocalDate): LocalDate =
    month.minusMonth().lastDay.coerceAtMost(today)

/** The closing side: the last day of [month], capped at [today]. */
fun closingBalanceDateOf(month: YearMonth, today: LocalDate): LocalDate =
    month.lastDay.coerceAtMost(today)
