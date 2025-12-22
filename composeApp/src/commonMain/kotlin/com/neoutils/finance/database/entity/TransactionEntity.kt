package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
        ),
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["creditCardId"]),
        Index(value = ["invoiceId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Type,
    val amount: Double,
    val title: String?,
    val date: LocalDate,
    val categoryId: Long? = null,
    val target: Target = Target.ACCOUNT,
    val creditCardId: Long? = null,
    val invoiceId: Long? = null
) {
    enum class Type {
        EXPENSE,
        INCOME,
        ADJUSTMENT,
        INVOICE_PAYMENT,
        ADVANCE_PAYMENT
    }

    enum class Target {
        ACCOUNT,
        CREDIT_CARD,
        INVOICE_PAYMENT
    }
}
