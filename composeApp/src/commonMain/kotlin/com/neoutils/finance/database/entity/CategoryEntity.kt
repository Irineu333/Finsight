@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val key: String,
    val type: Type,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    enum class Type {
        INCOME,
        EXPENSE;

        val isIncome: Boolean
            get() = this == INCOME

        val isExpense: Boolean
            get() = this == EXPENSE
    }
}
