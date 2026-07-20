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
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["accountId"]),
        Index(value = ["invoiceId"]),
    ]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val accountId: Long,
    val amount: Long,
    val currency: String = "BRL",
    // Set only on the credit-card (LIABILITY) leg of a purchase, so an invoice's
    // balance is Σ entries with this invoiceId — the sub-ledger of the card account.
    val invoiceId: Long? = null,
)
