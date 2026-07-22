@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database

import androidx.room.TypeConverter
import kotlinx.datetime.YearMonth
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The conversions the facade tables need. A transaction's date lives in
 * [LedgerConverters], with the ledger — `AppDatabase` declares both.
 */
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
    fun fromYearMonth(yearMonth: YearMonth): String {
        return yearMonth.toString()
    }

    @TypeConverter
    fun toYearMonth(value: String): YearMonth {
        return YearMonth.parse(value)
    }
}