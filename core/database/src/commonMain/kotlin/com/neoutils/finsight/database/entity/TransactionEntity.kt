package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

/**
 * A ledger transaction: what the entries balance under, plus what the ledger cannot
 * express — a title and a date.
 *
 * The category is gone: what a transaction is spent on is carried by the dimension
 * of its nominal leg, not by a column here (design D4).
 *
 * The installment and recurring columns stay, **without their foreign keys**. This
 * table belongs to the ledger and the ledger cannot see the facade tables those keys
 * pointed at (design D12). They are grouping metadata: no ledger read consults them,
 * and the nullification the keys used to grant for free now has an explicit owner in
 * each facade's removal path.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["installmentId"]),
        Index(value = ["recurringId"]),
        Index(value = ["recurringCycle"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String?,
    val date: LocalDate,
    val recurringId: Long? = null,
    val recurringCycle: Int? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
)
