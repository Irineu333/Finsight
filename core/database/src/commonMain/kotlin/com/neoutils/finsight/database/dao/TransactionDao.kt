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

    /**
     * Detaches every transaction from a facade that is being removed.
     *
     * Today the `ON DELETE SET NULL` foreign key already clears the id half of the
     * pair, which makes these calls redundant — deliberately so. `transactions`
     * moves to the ledger module, where the foreign key cannot survive because its
     * parent entity stays behind (design D12), and the behaviour it granted for free
     * has to have an explicit owner *before* the key is dropped, not after.
     *
     * They are also not purely redundant: the key never cleared `installmentNumber`
     * or `recurringCycle`, so a removed facade used to leave half a reference behind.
     */
    @Query(
        """
        UPDATE transactions
        SET installmentId = NULL, installmentNumber = NULL
        WHERE installmentId = :installmentId
        """
    )
    suspend fun detachFromInstallment(installmentId: Long)

    @Query(
        """
        UPDATE transactions
        SET recurringId = NULL, recurringCycle = NULL
        WHERE recurringId = :recurringId
        """
    )
    suspend fun detachFromRecurring(recurringId: Long)

    /**
     * Transactions filtered by date, by sub-ledger and by account — all three in
     * ledger terms.
     *
     * The card filter used to be its own parameter, resolved through
     * `JOIN credit_cards ON c.accountId = e.accountId`. That join did nothing the
     * account filter does not already do: "a transaction of card X" *is* "a
     * transaction with a leg on X's LIABILITY account". Callers pass
     * `creditCard.accountId`, and with the join goes the last reference this query
     * made to a facade table.
     */
    @Query(
        """
        SELECT * FROM transactions o
        WHERE (:date IS NULL OR o.date = :date)
          AND (:dimensionId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.dimensionId = :dimensionId))
          AND (:accountId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.accountId = :accountId))
        ORDER BY o.date DESC, o.id DESC
    """
    )
    fun observeBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<TransactionEntity>>
}
