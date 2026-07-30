package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single leg of a balanced transaction in the double-entry ledger. [amount] is
 * signed and stored in the currency's minor unit (debit-positive convention).
 * Deleting the parent transaction cascades to its entries; an account referenced
 * by any entry cannot be deleted (NO ACTION) so the zero-sum invariant is kept.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = DimensionEntity::class,
            parentColumns = ["id"],
            childColumns = ["dimensionId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["accountId"]),
        Index(value = ["dimensionId"]),
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val accountId: Long,
    val amount: Long,
    // No default: the currency of a leg is the currency of the account it posts to,
    // read by the write boundary from the row it already loads (design D5). Nothing
    // gets to leave it unsaid. The column stays NOT NULL either way — a Kotlin
    // constructor default never emitted SQL `DEFAULT`.
    val currency: String,
    // The analytic axis this leg is tagged with, if any: the sub-ledger it belongs
    // to inside its account. A facade's total is Σ entries carrying its dimension.
    val dimensionId: Long? = null,
)
