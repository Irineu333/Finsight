package com.neoutils.finance.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finance.database.entity.InvoiceEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status IN ('OPEN', 'FUTURE') ORDER BY openingMonth ASC")
    fun observeAvailableInvoices(creditCardId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status NOT IN ('PAID', 'RETROACTIVE') ORDER BY openingMonth DESC")
    suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE status NOT IN ('PAID', 'RETROACTIVE') ORDER BY openingMonth DESC")
    fun observeUnpaidInvoices(): Flow<List<InvoiceEntity>>

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

    @Insert
    suspend fun insert(invoice: InvoiceEntity): Long

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: Long)
}
