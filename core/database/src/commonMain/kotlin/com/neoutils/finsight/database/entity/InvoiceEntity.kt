@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DimensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["dimensionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["creditCardId"]),
        Index(value = ["dimensionId"]),
        Index(value = ["creditCardId", "openingMonth"], unique = true),
        Index(value = ["creditCardId", "closingMonth"], unique = true),
        Index(value = ["creditCardId", "dueMonth"], unique = true)
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditCardId: Long,
    // The ledger identity this invoice's legs are tagged with. Nullable only
    // because v10 added the column to existing rows; every invoice has one.
    val dimensionId: Long? = null,
    val openingMonth: YearMonth,
    val closingMonth: YearMonth,
    val dueMonth: YearMonth,
    val status: Status,
    val createdAt: Instant = Clock.System.now(),
    val openedAt: LocalDate? = null,
    val closedAt: LocalDate? = null,
    val paidAt: LocalDate? = null
) {
    enum class Status {
        FUTURE,
        OPEN,
        CLOSED,
        PAID,
        RETROACTIVE
    }
}
