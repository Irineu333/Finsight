package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val date: LocalDate,
    val categoryId: Long? = null
) {
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT;

        val isExpense: Boolean
            get() = this == EXPENSE

        val isIncome: Boolean
            get() = this == INCOME

        val isAdjustment: Boolean
            get() = this == ADJUSTMENT
    }
}
