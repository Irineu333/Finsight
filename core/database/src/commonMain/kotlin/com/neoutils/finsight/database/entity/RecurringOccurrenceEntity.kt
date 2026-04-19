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
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["recurringId"]),
        Index(value = ["operationId"], unique = true),
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
    val operationId: Long? = null,
    val effectiveDate: LocalDate,
    val handledAt: Long,
) {
    enum class Status {
        CONFIRMED,
        SKIPPED,
    }
}
