package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@Entity(
    tableName = "recurring_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["recurringId"]),
        Index(value = ["transactionId"], unique = true),
        Index(value = ["recurringId", "yearMonth"], unique = true),
        Index(value = ["recurringId", "cycleNumber"], unique = true),
    ],
)
data class RecurringOccurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recurringId: Long,
    val cycleNumber: Int,
    val yearMonth: YearMonth,
    val status: Status,
    val transactionId: Long? = null,
    val effectiveDate: LocalDate,
    val handledAt: Long,
) {
    enum class Status {
        CONFIRMED,
        SKIPPED,
    }
}
