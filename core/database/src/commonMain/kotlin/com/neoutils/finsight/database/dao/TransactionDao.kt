package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    // A purchase on the card: it has a leg on the card's LIABILITY account and that
    // is its only monetary leg — which is what excludes an invoice payment, whose
    // other monetary leg is the paying ASSET account.
    @Query(
        """
        DELETE FROM transactions
        WHERE EXISTS (
            SELECT 1 FROM entries e
            JOIN credit_cards c ON c.accountId = e.accountId
            WHERE e.transactionId = transactions.id AND c.id = :creditCardId
        )
        AND (
            SELECT COUNT(*) FROM entries e
            JOIN accounts a ON a.id = e.accountId
            WHERE e.transactionId = transactions.id AND a.type IN ('ASSET', 'LIABILITY')
        ) = 1
        """
    )
    suspend fun deleteTransactionsByCreditCardId(creditCardId: Long)

    @Query(
        """
        UPDATE transactions
        SET title = :title,
            date = :date,
            categoryId = :categoryId
        WHERE id = :id
        """
    )
    suspend fun update(
        id: Long,
        title: String?,
        date: LocalDate,
        categoryId: Long?,
    )

    @Query("SELECT COUNT(*) FROM transactions WHERE installmentId = :installmentId")
    suspend fun countByInstallmentId(installmentId: Long): Int

    // The invoice/card/account filters derive from the ledger legs: an invoice is the
    // card account's sub-ledger, a card is reached through its LIABILITY account.
    @Query(
        """
        SELECT * FROM transactions o
        WHERE (:date IS NULL OR o.date = :date)
          AND (:invoiceId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.invoiceId = :invoiceId))
          AND (:creditCardId IS NULL OR EXISTS (
              SELECT 1 FROM entries e
              JOIN credit_cards c ON c.accountId = e.accountId
              WHERE e.transactionId = o.id AND c.id = :creditCardId
          ))
          AND (:accountId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.accountId = :accountId))
        ORDER BY o.date DESC, o.id DESC
    """
    )
    fun observeBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<TransactionEntity>>
}
