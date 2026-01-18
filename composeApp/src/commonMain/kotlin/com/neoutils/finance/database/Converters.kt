@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.database

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class Converters {

    @TypeConverter
    fun fromInstant(instant: Instant): Long {
        return instant.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(timestamp: Long): Instant {
        return Instant.fromEpochMilliseconds(timestamp)
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String {
        return date.toString()
    }

    @TypeConverter
    fun toLocalDate(dateString: String): LocalDate {
        return LocalDate.parse(dateString)
    }

    @TypeConverter
    fun fromYearMonth(yearMonth: YearMonth): String {
        return yearMonth.toString()
    }

    @TypeConverter
    fun toYearMonth(value: String): YearMonth {
        return YearMonth.parse(value)
    }
}