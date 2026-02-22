package com.neoutils.finsight.extension

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.onDay

fun YearMonth.safeOnDay(day: Int): LocalDate {
    return onDay(day.coerceAtMost(numberOfDays))
}

fun YearMonth.effectiveDay(day: Int): Int {
    return day.coerceAtMost(numberOfDays)
}
