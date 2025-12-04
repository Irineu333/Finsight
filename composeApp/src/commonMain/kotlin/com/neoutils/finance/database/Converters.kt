package com.neoutils.finance.database

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String {
        return date.toString()
    }

    @TypeConverter
    fun toLocalDate(dateString: String): LocalDate {
        return LocalDate.parse(dateString)
    }
}