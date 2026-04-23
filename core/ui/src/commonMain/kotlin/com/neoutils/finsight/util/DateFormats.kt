@file:OptIn(FormatStringsInDatetimeFormats::class, ExperimentalTime::class)

package com.neoutils.finsight.util

import androidx.compose.runtime.compositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DateFormats(
    private val monthNames: MonthNames,
    private val dayOfWeekNames: DayOfWeekNames,
) {
    fun monthName(month: Month): String = monthNames.names[month.ordinal]

    private val abbreviatedMonthNames = MonthNames(monthNames.names.map { it.take(3) })

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

    val monthDayYear = LocalDate.Format {
        monthName(abbreviatedMonthNames)
        chars(" ")
        day()
        chars(", ")
        year()
    }

    private val monthDay = LocalDate.Format {
        monthName(abbreviatedMonthNames)
        chars(" ")
        day()
    }

    private val timeFormat = LocalTime.Format {
        hour()
        chars(":")
        minute()
    }

    fun toLocalDate(instant: Instant): LocalDate {
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    fun formatDividerDate(instant: Instant, today: String, yesterday: String): String {
        val date = toLocalDate(instant)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return when (date.daysUntil(now)) {
            0 -> today
            1 -> yesterday
            else -> if (date.year == now.year) monthDay.format(date) else monthDayYear.format(date)
        }
    }

    fun formatReportPeriod(startDate: LocalDate, endDate: LocalDate): String {
        val short = LocalDate.Format {
            day(); chars(" "); monthName(monthNames)
        }
        val full = LocalDate.Format {
            day(); chars(" "); monthName(monthNames); chars(", "); year()
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

    fun formatInstantDate(instant: Instant): String {
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return monthDayYear.format(date)
    }

    fun formatInstantTime(instant: Instant): String {
        val time = instant.toLocalDateTime(TimeZone.currentSystemDefault()).time
        return timeFormat.format(time)
    }
}

val LocalDateFormats = compositionLocalOf<DateFormats> { error("Not initialized") }
