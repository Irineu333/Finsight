@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.extension

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun Instant.toYearMonth(): YearMonth {
    return toLocalDateTime(TimeZone.currentSystemDefault()).yearMonth
}

fun YearMonth.toLocalDate(dayOfMonth: Int = 1): LocalDate {
    return LocalDate(year, month, dayOfMonth)
}

fun YearMonth.toLastDayOfMonth(): LocalDate {
    val daysInMonth = when (month) {
        kotlinx.datetime.Month.JANUARY, 
        kotlinx.datetime.Month.MARCH, 
        kotlinx.datetime.Month.MAY, 
        kotlinx.datetime.Month.JULY, 
        kotlinx.datetime.Month.AUGUST, 
        kotlinx.datetime.Month.OCTOBER, 
        kotlinx.datetime.Month.DECEMBER -> 31
        kotlinx.datetime.Month.APRIL, 
        kotlinx.datetime.Month.JUNE, 
        kotlinx.datetime.Month.SEPTEMBER, 
        kotlinx.datetime.Month.NOVEMBER -> 30
        kotlinx.datetime.Month.FEBRUARY -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    }
    return LocalDate(year, month, daysInMonth)
}