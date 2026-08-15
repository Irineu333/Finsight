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
            date = :date
        WHERE id = :id
        """
    )
    suspend fun update(
        id: Long,
        title: String?,
        date: LocalDate,
    )

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

    /**
     * The same reading, answered once instead of observed, and cut by a **period**
     * instead of a single day.
     *
     * It is a twin of [observeBy], not a replacement: the screens are reactive and
     * keep their `Flow`. A request/response consumer has no use for one, and a
     * consumer that wanted a month would otherwise have to fetch everything and
     * narrow it outside this module — which would move the cut away from the owner
     * of the reading.
     *
     * Every predicate is null-neutral, exactly as in [observeBy], and both ends of
     * the period are **inclusive**. With `startDate == endDate` this answers what
     * [observeBy] observes for that day, in the same order.
     */
    @Query(
        """
        SELECT * FROM transactions o
        WHERE (:startDate IS NULL OR o.date >= :startDate)
          AND (:endDate IS NULL OR o.date <= :endDate)
          AND (:dimensionId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.dimensionId = :dimensionId))
          AND (:accountId IS NULL OR EXISTS (SELECT 1 FROM entries e WHERE e.transactionId = o.id AND e.accountId = :accountId))
        ORDER BY o.date DESC, o.id DESC
    """
    )
    suspend fun getBy(
        startDate: LocalDate?,
        endDate: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): List<TransactionEntity>
}
