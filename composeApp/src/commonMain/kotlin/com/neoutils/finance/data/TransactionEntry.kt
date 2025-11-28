package com.neoutils.finance.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.LocalDate

@Entity(tableName = "transactions")
data class TransactionEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val description: String,
    val date: LocalDate,
) {
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT
    }
}