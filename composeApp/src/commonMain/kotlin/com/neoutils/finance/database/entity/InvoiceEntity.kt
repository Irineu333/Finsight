@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["creditCardId"]),
        Index(value = ["creditCardId", "openingMonth"], unique = true),
        Index(value = ["creditCardId", "closingMonth"], unique = true)
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val creditCardId: Long,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val status: Status,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    enum class Status {
        OPEN,
        CLOSED,
        PAID
    }
}
