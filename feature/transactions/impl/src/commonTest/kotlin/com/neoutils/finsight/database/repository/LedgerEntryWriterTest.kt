package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.SystemAccount
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The write boundary, in the vocabulary it now speaks: account ids, dimension ids
 * and account natures. No facade appears here, which is the point — if one had to,
 * the writer would still be able to name it.
 */
class LedgerEntryWriterTest {

    private val entryDao = FakeEntryDao()
    private val accountDao = FakeAccountDao()
    private val dimensionDao = FakeDimensionDao()

    private val writer = LedgerEntryWriter(entryDao, accountDao, dimensionDao)

    /** The user's own account, id 1, open. */
    private fun openAsset(id: Long = 1) = AccountEntity(id = id, name = "Acc $id", type = AccountEntity.Type.ASSET)
        .also { accountDao.accounts[id] = it }

    @Test
    fun `given an expense when written then the nominal leg carries the category dimension`() = runTest {
        openAsset()
        dimensionDao.insert(DimensionEntity(id = 7, kind = DimensionKind.CATEGORY))

        writer.writeEntries(
            transactionId = 1,
            legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
            contra = ContraLeg(AccountType.EXPENSE, dimensionId = 7),
        )

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-5000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        // The category is not an account: the contra leg lands on the single EXPENSE
        // nominal, and *which* category it is comes from the dimension.
        val nominal = accountDao.accounts.values.first { it.name == SystemAccount.EXPENSES }
        val nominalEntry = entryDao.inserted.first { it.accountId == nominal.id }
        assertEquals(5000L, nominalEntry.amount)
        assertEquals(7L, nominalEntry.dimensionId)
    }

    @Test
    fun `given an expense with no category when written then the nominal leg is unclassified`() = runTest {
        openAsset()

        writer.writeEntries(
            transactionId = 1,
            legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
            contra = ContraLeg(AccountType.EXPENSE),
        )

        val nominal = accountDao.accounts.values.first { it.name == SystemAccount.EXPENSES }
        // No bucket account and no bucket dimension: "uncategorized" is the absence.
        assertNull(entryDao.inserted.first { it.accountId == nominal.id }.dimensionId)
        assertEquals(1, accountDao.accounts.values.count { it.type == AccountEntity.Type.EXPENSE })
    }

    @Test
    fun `however many writes, the chart holds one nominal of each nature`() = runTest {
        // `ensureSystemAccount` looks up before inserting, so the chart keeps exactly
        // the three system rows however much is posted through them. A second
        // 'Despesas' would not fail anything — it would just split every expense
        // total in two, silently (spec `chart-of-accounts`).
        openAsset(1)
        repeat(3) { index ->
            writer.writeEntries(
                transactionId = index + 1L,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 10.0, accountId = 1)),
                contra = ContraLeg(AccountType.EXPENSE),
            )
            writer.writeEntries(
                transactionId = index + 10L,
                legs = listOf(TransactionLeg(type = TransactionType.INCOME, amount = 10.0, accountId = 1)),
                contra = ContraLeg(AccountType.INCOME),
            )
            writer.writeEntries(
                transactionId = index + 20L,
                legs = listOf(TransactionLeg(type = TransactionType.ADJUSTMENT, amount = 10.0, accountId = 1)),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }

        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.EXPENSES })
        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.INCOMES })
        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.RECONCILIATION })
        // The user's account plus the three system rows, and nothing else.
        assertEquals(4, accountDao.accounts.size)
    }

    @Test
    fun `given a transfer when written then both legs balance without synthesis`() = runTest {
        openAsset(1)
        openAsset(2)
        val out = TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, accountId = 1)
        val income = TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2)

        writer.validate(listOf(out, income))
        writer.writeEntries(transactionId = 2, legs = listOf(out, income), contra = null)

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-10000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        assertEquals(10000L, entryDao.inserted.first { it.accountId == 2L }.amount)
    }

    @Test
    fun `given an adjustment when written then contra is a created reconciliation equity account`() = runTest {
        openAsset()

        writer.writeEntries(
            transactionId = 3,
            legs = listOf(TransactionLeg(type = TransactionType.ADJUSTMENT, amount = 30.0, accountId = 1)),
            contra = ContraLeg(AccountType.EQUITY),
        )

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        val reconciliation = accountDao.accounts.values.first { it.type == AccountEntity.Type.EQUITY }
        assertEquals(SystemAccount.RECONCILIATION, reconciliation.name)
        assertEquals(-3000L, entryDao.inserted.first { it.accountId == reconciliation.id }.amount)
    }

    @Test
    fun `given an invoice payment when written then only the liability leg tags the sub-ledger`() = runTest {
        openAsset()
        accountDao.accounts[200L] = AccountEntity(id = 200, name = "Card", type = AccountEntity.Type.LIABILITY)
        dimensionDao.insert(DimensionEntity(id = 5, kind = DimensionKind.INVOICE))

        writer.writeEntries(
            transactionId = 4,
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 50.0, accountId = 200, dimensionId = 5),
            ),
            // Two legs already balance: there is nothing to synthesize.
            contra = null,
        )

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        val bankEntry = entryDao.inserted.first { it.accountId == 1L }
        assertEquals(-5000L, bankEntry.amount) // credit on the ASSET: money leaves the bank account
        assertNull(bankEntry.dimensionId) // or the two legs would cancel the sub-ledger out
        val cardEntry = entryDao.inserted.first { it.accountId == 200L }
        assertEquals(5000L, cardEntry.amount) // liability leg reduces the owed
        assertEquals(5L, cardEntry.dimensionId)
    }

    @Test
    fun `given a dimension landing on the wrong nature when written then nothing is written`() = runTest {
        openAsset()
        // An invoice's sub-ledger may only sit on a LIABILITY leg. Landing it on the
        // nominal produces no wrong number anywhere — it just makes every sum by that
        // dimension quietly wrong, which is why the boundary refuses it.
        dimensionDao.insert(DimensionEntity(id = 5, kind = DimensionKind.INVOICE))

        val error = assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
                contra = ContraLeg(AccountType.EXPENSE, dimensionId = 5),
            )
        }
        assertEquals(LedgerError.MisplacedDimension, error.error)
        assertTrue(entryDao.inserted.isEmpty())
    }

    @Test
    fun `given an archived account when written then the write is rejected`() = runTest {
        // Closing an ASSET required a zero balance, so a new entry there strands money.
        accountDao.accounts[1L] = AccountEntity(id = 1, name = "Checking", type = AccountEntity.Type.ASSET, isArchived = true)

        val error = assertFailsWith<ClosedAccountException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
                contra = ContraLeg(AccountType.EXPENSE),
            )
        }
        assertEquals(LedgerError.ClosedAccount(ClosedFacade.ACCOUNT), error.error)
        assertTrue(entryDao.inserted.isEmpty())
    }

    @Test
    fun `given an archived card when written then the error names the card`() = runTest {
        accountDao.accounts[200L] = AccountEntity(id = 200, name = "Card", type = AccountEntity.Type.LIABILITY, isArchived = true)

        val error = assertFailsWith<ClosedAccountException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 200)),
                contra = ContraLeg(AccountType.EXPENSE),
            )
        }
        // Which facade the account belongs to is read off its nature — the ledger
        // reports what it knows, and the screen says the right word.
        assertEquals(LedgerError.ClosedAccount(ClosedFacade.CREDIT_CARD), error.error)
    }

    @Test
    fun `given a one-sided intent with no contra when written then nothing is written`() = runTest {
        openAsset()

        assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
                contra = null,
            )
        }
        assertTrue(entryDao.inserted.isEmpty())
    }

    @Test
    fun `given no legs at all when written then nothing is written`() = runTest {
        // The empty set balances vacuously, so without this an intent with no legs
        // produced a transaction with no entries — fewer than the two a double entry
        // has by definition.
        assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(transactionId = 1, legs = emptyList(), contra = null)
        }
        assertTrue(entryDao.inserted.isEmpty())
    }

    @Test
    fun `given an unbalanced multi-leg transaction when validated then it is rejected`() {
        val a = TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, accountId = 1)
        val b = TransactionLeg(type = TransactionType.INCOME, amount = 80.0, accountId = 2)

        assertFailsWith<UnbalancedTransactionException> { writer.validate(listOf(a, b)) }
    }
}

private class FakeEntryDao : EntryDao {
    val inserted = mutableListOf<EntryEntity>()
    override suspend fun insert(entry: EntryEntity): Long { inserted += entry; return inserted.size.toLong() }
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> { inserted += entries; return entries.indices.map { it.toLong() } }
    override suspend fun deleteByTransactionId(transactionId: Long) { inserted.removeAll { it.transactionId == transactionId } }
    override suspend fun getAll(): List<EntryEntity> = inserted
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override fun observeEntryCount(): Flow<Long> = flowOf(0)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun getByTransactionId(transactionId: Long): List<EntryEntity> = inserted.filter { it.transactionId == transactionId }
    override suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<com.neoutils.finsight.database.dao.EntryWithAccount> = throw NotImplementedError()
    override fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<com.neoutils.finsight.database.dao.EntryWithAccount>> = throw NotImplementedError()
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): com.neoutils.finsight.database.dao.AccountPeriodTotals = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(dimensionId: Long, yearMonth: String): Int = throw NotImplementedError()
    override suspend fun balanceOf(accountId: Long): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun dimensionPeriodTotals(dimensionId: Long): com.neoutils.finsight.database.dao.DimensionPeriodTotals = throw NotImplementedError()
    override suspend fun liabilityMonthTotals(yearMonth: String): com.neoutils.finsight.database.dao.LiabilityMonthTotals = throw NotImplementedError()
    override suspend fun assetMonthTotals(yearMonth: String): com.neoutils.finsight.database.dao.AssetMonthTotals = throw NotImplementedError()
    override suspend fun totalsByDimensionWithSiblingLeg(
        nominalType: String,
        start: kotlinx.datetime.LocalDate,
        end: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.DimensionTotal> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: String,
        scopeDimensionIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.DimensionTotal> = throw NotImplementedError()
    override suspend fun scopeStats(scopeIds: List<Long>, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): com.neoutils.finsight.database.dao.ScopeStatsTotals = throw NotImplementedError()
    override suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun assetsBalanceUpToMonth(yearMonth: String): Long = inserted.sumOf { it.amount }
    override suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String): Long = inserted.filter { it.dimensionId == dimensionId }.sumOf { it.amount }
    override suspend fun dimensionNaturalBalance(dimensionId: Long): Long = inserted.filter { it.dimensionId == dimensionId }.sumOf { it.amount }
    override suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionTotal> =
        inserted.filter { it.dimensionId in dimensionIds }
            .groupBy { it.dimensionId!! }
            .map { (id, entries) -> com.neoutils.finsight.database.dao.DimensionTotal(id, entries.sumOf { it.amount }) }
    override suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionPeriodTotalsRow> = throw NotImplementedError()
    override suspend fun netWorthCents(): Long = inserted.sumOf { it.amount }
}

private class FakeDimensionDao : DimensionDao {
    val dimensions = linkedMapOf<Long, DimensionEntity>()
    private var seq = 0L
    override suspend fun insert(dimension: DimensionEntity): Long {
        val id = if (dimension.id != 0L) dimension.id else ++seq
        dimensions[id] = dimension.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): DimensionEntity? = dimensions[id]
    override suspend fun deleteById(id: Long) { dimensions.remove(id) }
}

private class FakeAccountDao : AccountDao {
    val accounts = linkedMapOf<Long, AccountEntity>()
    private var seq = 100L
    override suspend fun close(id: Long) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = true) }
    }
    override suspend fun reopen(id: Long) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = false) }
    }
    override suspend fun entryCount(accountId: Long): Int = 0
    override suspend fun getAllAccountsIncludingClosed(): List<AccountEntity> =
        accounts.values.filter { it.type == AccountEntity.Type.ASSET }

    override fun observeAllAccountsIncludingClosed(): Flow<List<AccountEntity>> =
        flowOf(accounts.values.filter { it.type == AccountEntity.Type.ASSET })

    override suspend fun getAllLedgerAccounts(): List<AccountEntity> = accounts.values.toList()
    override fun observeAllLedgerAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override suspend fun insert(account: AccountEntity): Long {
        val id = seq++
        accounts[id] = account.copy(id = id)
        return id
    }
    override suspend fun getByTypeAndName(type: AccountEntity.Type, name: String): AccountEntity? =
        accounts.values.firstOrNull { it.type == type && it.name == name }
    override fun observeAllAccounts(): Flow<List<AccountEntity>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<AccountEntity> = accounts.values.toList()
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]
    override fun observeAccountById(id: Long): Flow<AccountEntity?> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): AccountEntity? = null
    override fun observeDefaultAccount(): Flow<AccountEntity?> = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun update(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun delete(account: AccountEntity) { accounts.remove(account.id) }
}


