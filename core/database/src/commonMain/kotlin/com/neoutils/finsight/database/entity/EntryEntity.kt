package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single leg of a balanced operation in the double-entry ledger. [amount] is
 * signed and stored in the currency's minor unit (debit-positive convention).
 * Deleting the parent operation cascades to its entries; an account referenced
 * by any entry cannot be deleted (NO ACTION) so the zero-sum invariant is kept.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.NO_ACTION
        ),
    ],
    indices = [
        Index(value = ["operationId"]),
        Index(value = ["accountId"]),
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationId: Long,
    val accountId: Long,
    val amount: Long,
    val currency: String = "BRL",
)
