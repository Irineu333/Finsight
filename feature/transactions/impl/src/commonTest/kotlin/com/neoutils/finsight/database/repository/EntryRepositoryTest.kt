package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
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
    override suspend fun deleteByOperationId(operationId: Long) = throw NotImplementedError()
    override suspend fun getAll(): List<EntryEntity> = throw NotImplementedError()
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun getByOperationId(operationId: Long): List<EntryEntity> = throw NotImplementedError()
    override fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun naturalBalanceOf(accountId: Long, currency: String): Long = throw NotImplementedError()
}
