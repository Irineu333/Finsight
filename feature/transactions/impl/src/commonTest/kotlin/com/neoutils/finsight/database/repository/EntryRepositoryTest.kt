package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryRepositoryTest {

    @Test
    fun `given account entries when balanceUpTo then cents are converted to reais`() = runTest {
        val repository = EntryRepository(FakeReadEntryDao(balanceUpTo = -12000))
        assertEquals(-120.0, repository.balanceUpTo(YearMonth(2026, 1), accountId = 1))
    }

    @Test
    fun `given a liability invoice balance when invoiceOwed then it reads positive`() = runTest {
        // A card purchase leaves the liability leg negative (-5000 cents); owed reads +50.
        val repository = EntryRepository(FakeReadEntryDao(invoice = -5000))
        assertEquals(50.0, repository.invoiceOwed(invoiceId = 7))
    }

    @Test
    fun `given assets and liabilities when netWorth then it sums naturally`() = runTest {
        // 100.00 assets + (-30.00) liability natural = 70.00.
        val repository = EntryRepository(FakeReadEntryDao(netWorth = 7000))
        assertEquals(70.0, repository.netWorth())
    }
}

private class FakeReadEntryDao(
    private val balanceUpTo: Long = 0,
    private val assets: Long = 0,
    private val inMonth: Long = 0,
    private val invoice: Long = 0,
    private val netWorth: Long = 0,
) : EntryDao {
    override suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): Long = balanceUpTo
    override suspend fun assetsBalanceUpToMonth(yearMonth: String): Long = assets
    override suspend fun balanceInMonth(accountId: Long, yearMonth: String): Long = inMonth
    override suspend fun invoiceNaturalBalance(invoiceId: Long): Long = invoice
    override suspend fun netWorthCents(): Long = netWorth
    override suspend fun insert(entry: EntryEntity): Long = throw NotImplementedError()
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> = throw NotImplementedError()
    override suspend fun delete(entry: EntryEntity) = throw NotImplementedError()
    override suspend fun deleteByTransactionId(transactionId: Long) = throw NotImplementedError()
    override suspend fun getAll(): List<EntryEntity> = throw NotImplementedError()
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override fun observeEntryCount(): Flow<Long> = flowOf(0)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun getByTransactionId(transactionId: Long): List<EntryEntity> = throw NotImplementedError()
    override suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<com.neoutils.finsight.database.dao.EntryWithAccount> = throw NotImplementedError()
    override fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<com.neoutils.finsight.database.dao.EntryWithAccount>> = throw NotImplementedError()
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): com.neoutils.finsight.database.dao.AccountPeriodTotals = throw NotImplementedError()
    override suspend fun entryCountInMonth(accountId: Long, yearMonth: String): Int = throw NotImplementedError()
    override fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun naturalBalanceOf(accountId: Long, currency: String): Long = throw NotImplementedError()
    override suspend fun balanceOf(accountId: Long): Long = throw NotImplementedError()
    override suspend fun invoicePeriodTotals(invoiceId: Long): com.neoutils.finsight.database.dao.InvoicePeriodTotals = throw NotImplementedError()
    override suspend fun cardMonthTotals(yearMonth: String): com.neoutils.finsight.database.dao.CardMonthTotals = throw NotImplementedError()
    override suspend fun categoryTotalsWithSiblingLeg(
        categoryType: String,
        start: kotlinx.datetime.LocalDate,
        end: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.CategoryAccountTotal> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(
        categoryType: String,
        invoiceIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.CategoryAccountTotal> = throw NotImplementedError()
}
