package com.neoutils.finsight.database

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

/**
 * The one conversion the ledger's own tables need: a transaction's date.
 *
 * Split from the facade converters rather than duplicating them — Room accepts
 * several converter classes on one `@Database`, so `AppDatabase` declares both and
 * neither module carries the other's types.
 */
class LedgerConverters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(dateString: String): LocalDate = LocalDate.parse(dateString)
}
