package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "operations",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetCreditCardId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetInvoiceId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InstallmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["installmentId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["sourceAccountId"]),
        Index(value = ["targetCreditCardId"]),
        Index(value = ["targetInvoiceId"]),
        Index(value = ["installmentId"]),
    ]
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val kind: Kind,
    val title: String?,
    val date: LocalDate,
    val categoryId: Long? = null,
    val sourceAccountId: Long? = null,
    val targetCreditCardId: Long? = null,
    val targetInvoiceId: Long? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
) {
    enum class Kind {
        TRANSACTION,
        PAYMENT,
        TRANSFER,
    }
}
