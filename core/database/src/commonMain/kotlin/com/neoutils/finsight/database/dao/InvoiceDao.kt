package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.InvoiceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

@Dao
interface InvoiceDao {

    @Query("SELECT * FROM invoices ORDER BY openingMonth DESC")
    fun observeAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY openingMonth DESC")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId ORDER BY openingMonth DESC")
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId ORDER BY openingMonth DESC")
    suspend fun getAllInvoicesByCreditCard(creditCardId: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status = 'OPEN' LIMIT 1")
    fun observeOpenInvoice(creditCardId: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenInvoice(creditCardId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status IN ('OPEN', 'FUTURE') ORDER BY openingMonth ASC")
    fun observeAvailableInvoices(creditCardId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status NOT IN ('PAID', 'RETROACTIVE') ORDER BY openingMonth DESC")
    suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<InvoiceEntity>

    // The batched form of the query above, and deliberately a copy of its predicate:
    // "unpaid" is stated in SQL, and the two statements are the only expression of it.
    @Query("SELECT * FROM invoices WHERE creditCardId IN (:creditCardIds) AND status NOT IN ('PAID', 'RETROACTIVE') ORDER BY openingMonth DESC")
    suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE status NOT IN ('PAID', 'RETROACTIVE') ORDER BY openingMonth DESC")
    fun observeUnpaidInvoices(): Flow<List<InvoiceEntity>>

    /**
     * Every invoice that has not been paid and whose due month is not later than
     * [month] — the debt whose settlement window has already arrived, overdue months
     * included.
     *
     * The criterion is the negation of `PAID`, not a whitelist of statuses, so
     * `RETROACTIVE` is in it: a retroactive invoice carrying a balance is debt being
     * regularised, and this read is the one place that says so. `FUTURE` past its due
     * month is in for the same reason — its instalments were posted in advance and are
     * owed now.
     */
    @Query("SELECT * FROM invoices WHERE status != 'PAID' AND dueMonth <= :month ORDER BY dueMonth ASC")
    fun observeInvoicesToSettle(month: YearMonth): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices 
        WHERE creditCardId = :creditCardId AND status IN ('CLOSED', 'OPEN')
        ORDER BY 
            CASE status 
                WHEN 'CLOSED' THEN 0 
                WHEN 'OPEN' THEN 1 
            END,
            openingMonth ASC 
        LIMIT 1
    """)
    fun observeUnpaidInvoice(creditCardId: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observeInvoiceById(id: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Long): InvoiceEntity?

    @Insert
    suspend fun insert(invoice: InvoiceEntity): Long

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: Long)
}
