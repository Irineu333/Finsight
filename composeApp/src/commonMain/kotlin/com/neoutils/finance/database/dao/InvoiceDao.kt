package com.neoutils.finance.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finance.database.entity.InvoiceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

@Dao
interface InvoiceDao {

    @Query("SELECT * FROM invoices ORDER BY openingMonth DESC")
    fun observeAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId ORDER BY openingMonth DESC")
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenInvoice(creditCardId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND status != 'PAID' ORDER BY openingMonth DESC LIMIT 1")
    suspend fun getLatestUnpaidInvoice(creditCardId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND :month >= openingMonth AND :month <= closingMonth LIMIT 1")
    suspend fun getInvoiceForMonth(creditCardId: Long, month: YearMonth): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND openingMonth = :openingMonth LIMIT 1")
    suspend fun getByOpeningMonth(creditCardId: Long, openingMonth: YearMonth): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE creditCardId = :creditCardId AND closingMonth = :closingMonth LIMIT 1")
    suspend fun getByClosingMonth(creditCardId: Long, closingMonth: YearMonth): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observeById(id: Long): Flow<InvoiceEntity?>

    @Insert
    suspend fun insert(invoice: InvoiceEntity): Long

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: Long)
}
