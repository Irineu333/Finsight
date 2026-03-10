@file:OptIn(FormatStringsInDatetimeFormats::class, ExperimentalTime::class)

package com.neoutils.finsight.util

import androidx.compose.runtime.compositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val dayMonthYear = LocalDate.Format {
    byUnicodePattern("dd/MM/yyyy")
}

val dayMonth = LocalDate.Format {
    byUnicodePattern("dd/MM")
}

class DateFormats(
    private val monthNames: MonthNames,
    private val dayOfWeekNames: DayOfWeekNames,
) {
    val dayOfWeek = LocalDate.Format {
        day()
        chars(", ")
        dayOfWeek(dayOfWeekNames)
    }

    val yearMonth = YearMonth.Format {
        monthName(monthNames)
        chars(", ")
        year()
    }

    fun formatReportPeriod(startDate: LocalDate, endDate: LocalDate): String {
        val short = LocalDate.Format {
            day(); chars(" "); monthName(monthNames)
        }
        val full = LocalDate.Format {
            day(); chars(" "); monthName(monthNames); chars(" "); year()
        }
        return if (startDate.year == endDate.year) {
            "${short.format(startDate)} – ${full.format(endDate)}"
        } else {
            "${full.format(startDate)} – ${full.format(endDate)}"
        }
    }

    fun formatRelativeDate(date: LocalDate): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        return when {
            date.year != today.year -> dayMonthYear.format(date)
            date.month != today.month -> dayMonth.format(date)
            else -> dayOfWeek.format(date)
        }
    }
}

val LocalDateFormats = compositionLocalOf<DateFormats> { error("Not initialized") }
