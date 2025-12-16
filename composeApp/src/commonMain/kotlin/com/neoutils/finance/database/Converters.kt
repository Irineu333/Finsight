package com.neoutils.finance.database

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class Converters {

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