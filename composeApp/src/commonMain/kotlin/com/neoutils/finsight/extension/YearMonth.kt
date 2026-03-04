package com.neoutils.finsight.extension

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.onDay

fun YearMonth.safeOnDay(day: Int): LocalDate {
    return onDay(day.coerceAtMost(numberOfDays))
}

fun YearMonth.effectiveDay(day: Int): Int {
    return day.coerceAtMost(numberOfDays)
}

fun YearMonth.monthsUntil(other: YearMonth): Int {
    return (other.year - year) * 12 + (other.month.number - month.number)
}
