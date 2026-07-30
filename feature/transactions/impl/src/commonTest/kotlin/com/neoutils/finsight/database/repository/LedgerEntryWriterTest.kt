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

    /** The user's own account, id 1, open, in [currency]. */
    private fun openAsset(id: Long = 1, currency: String = "BRL") =
        AccountEntity(id = id, name = "Acc $id", type = AccountEntity.Type.ASSET, currency = currency)
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
        val nominal = accountDao.accounts.values.first { it.name == SystemAccount.EXPENSES.accountName }
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

        val nominal = accountDao.accounts.values.first { it.name == SystemAccount.EXPENSES.accountName }
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

        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.EXPENSES.accountName })
        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.INCOMES.accountName })
        assertEquals(1, accountDao.accounts.values.count { it.name == SystemAccount.RECONCILIATION.accountName })
        // The user's account plus the three system rows, and nothing else.
        assertEquals(4, accountDao.accounts.size)
    }

    @Test
    fun `given a transfer when written then both legs balance without synthesis`() = runTest {
        openAsset(1)
        openAsset(2)
        val out = TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, accountId = 1)
        val income = TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2)

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
        assertEquals(SystemAccount.RECONCILIATION.accountName, reconciliation.name)
        assertEquals(-3000L, entryDao.inserted.first { it.accountId == reconciliation.id }.amount)
    }

    @Test
    fun `given an invoice payment when written then only the liability leg tags the sub-ledger`() = runTest {
        openAsset()
        accountDao.accounts[200L] = AccountEntity(currency = "BRL", id = 200, name = "Card", type = AccountEntity.Type.LIABILITY)
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
        accountDao.accounts[1L] = AccountEntity(currency = "BRL", id = 1, name = "Checking", type = AccountEntity.Type.ASSET, isArchived = true)

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
        accountDao.accounts[200L] = AccountEntity(currency = "BRL", id = 200, name = "Card", type = AccountEntity.Type.LIABILITY, isArchived = true)

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

    // region cross-currency completion (design D1, D6, D15)

    /** A second account of the user, in another currency. */
    private fun foreignAsset(id: Long, currency: String) = openAsset(id, currency)

    private fun conversionOf(currency: String) = accountDao.accounts.values
        .single { it.type == AccountEntity.Type.CONVERSION && it.currency == currency }

    @Test
    fun `given a transfer across currencies when written then each currency sums to zero`() = runTest {
        openAsset(1)
        foreignAsset(2, "USD")

        // What the statement shows: 550 left here, 100 arrived there. No rate is stated
        // anywhere on the way in — it is the relation between the two.
        writer.writeEntries(
            transactionId = 1,
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
            ),
            contra = null,
        )

        assertEquals(4, entryDao.inserted.size)
        val byCurrency = entryDao.inserted.groupBy { it.currency }
        assertEquals(0L, byCurrency.getValue("BRL").sumOf { it.amount })
        assertEquals(0L, byCurrency.getValue("USD").sumOf { it.amount })
        assertEquals(55_000L, entryDao.inserted.single { it.accountId == conversionOf("BRL").id }.amount)
        assertEquals(-10_000L, entryDao.inserted.single { it.accountId == conversionOf("USD").id }.amount)
    }

    @Test
    fun `given a cross-currency invoice payment when written then the conversion legs carry no dimension`() = runTest {
        openAsset(1)
        accountDao.accounts[200L] = AccountEntity(
            id = 200,
            name = "Card",
            type = AccountEntity.Type.LIABILITY,
            currency = "USD",
        )
        dimensionDao.insert(DimensionEntity(id = 5, kind = DimensionKind.INVOICE))

        writer.writeEntries(
            transactionId = 1,
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 200, dimensionId = 5),
            ),
            contra = null,
        )

        // Without the absence of a dimension this write would not exist at all: the
        // landing rule only accepts an INVOICE on a LIABILITY leg.
        val conversions = entryDao.inserted.filter {
            it.accountId in setOf(conversionOf("BRL").id, conversionOf("USD").id)
        }
        assertEquals(2, conversions.size)
        assertTrue(conversions.all { it.dimensionId == null })
        // And the invoice owes exactly what its own leg says, untouched by the residue.
        assertEquals(10_000L, entryDao.inserted.single { it.dimensionId == 5L }.amount)
    }

    @Test
    fun `given a rounding residue when written then the conversion leg absorbs it`() = runTest {
        openAsset(1)
        foreignAsset(2, "USD")

        // 33.33 out, 10.00 in: the residue is whatever it is, and the conversion leg is
        // the difference rather than a second computation to be reconciled.
        writer.writeEntries(
            transactionId = 1,
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 33.33, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 10.0, accountId = 2),
            ),
            contra = null,
        )

        assertEquals(3_333L, entryDao.inserted.single { it.accountId == conversionOf("BRL").id }.amount)
        assertEquals(0L, entryDao.inserted.filter { it.currency == "BRL" }.sumOf { it.amount })
        assertEquals(0L, entryDao.inserted.filter { it.currency == "USD" }.sumOf { it.amount })
    }

    @Test
    fun `given a single-currency write then no conversion leg is synthesized`() = runTest {
        openAsset(1)
        openAsset(2)

        writer.writeEntries(
            transactionId = 1,
            legs = listOf(
                TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, accountId = 1),
                TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
            ),
            contra = null,
        )

        assertEquals(2, entryDao.inserted.size)
        assertTrue(accountDao.accounts.values.none { it.type == AccountEntity.Type.CONVERSION })
    }

    @Test
    fun `given an unbalanced single-currency intent then it is still refused`() = runTest {
        openAsset(1)
        openAsset(2)

        // The residue is not zero and there is only one currency: an imbalance, and no
        // conversion leg is invented to cover it up.
        val error = assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 80.0, accountId = 2),
                ),
                contra = null,
            )
        }
        assertEquals(LedgerError.Unbalanced, error.error)
        assertTrue(entryDao.inserted.isEmpty())
        assertTrue(accountDao.accounts.values.none { it.type == AccountEntity.Type.CONVERSION })
    }

    @Test
    fun `given residues of the same sign then nothing is written`() = runTest {
        openAsset(1)
        foreignAsset(2, "USD")

        // Both currencies gain: money without an origin. Not an exchange.
        val error = assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(
                    TransactionLeg(type = TransactionType.INCOME, amount = 550.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
                ),
                contra = null,
            )
        }
        assertEquals(LedgerError.ImpossibleExchange, error.error)
        assertTrue(entryDao.inserted.isEmpty())
        // Refused before any account is materialized: no conversion row is left behind
        // for an operation the boundary did not complete.
        assertTrue(accountDao.accounts.values.none { it.type == AccountEntity.Type.CONVERSION })
    }

    @Test
    fun `given two currencies then each gets its own conversion account`() = runTest {
        openAsset(1)
        foreignAsset(2, "USD")

        repeat(2) { index ->
            writer.writeEntries(
                transactionId = index + 1L,
                legs = listOf(
                    TransactionLeg(type = TransactionType.EXPENSE, amount = 550.0, accountId = 1),
                    TransactionLeg(type = TransactionType.INCOME, amount = 100.0, accountId = 2),
                ),
                contra = null,
            )
        }

        // One per currency, not one per pair, and looked up before being inserted.
        assertEquals(2, accountDao.accounts.values.count { it.type == AccountEntity.Type.CONVERSION })
        assertEquals(
            setOf("BRL", "USD"),
            accountDao.accounts.values
                .filter { it.type == AccountEntity.Type.CONVERSION }
                .map { it.currency }
                .toSet(),
        )
    }

    @Test
    fun `given an expense from a foreign account then the nominal is that currency's`() = runTest {
        openAsset(1, currency = "USD")

        writer.writeEntries(
            transactionId = 1,
            legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
            contra = ContraLeg(AccountType.EXPENSE),
        )

        // A nominal leg posts on the nominal of the currency of the account beside it, so
        // an expense is single-currency by construction whatever it bought.
        assertEquals(2, entryDao.inserted.size)
        assertTrue(entryDao.inserted.all { it.currency == "USD" })
        val nominal = accountDao.accounts.values.single { it.type == AccountEntity.Type.EXPENSE }
        assertEquals("USD", nominal.currency)
        assertTrue(accountDao.accounts.values.none { it.type == AccountEntity.Type.CONVERSION })
    }

    @Test
    fun `a contra leg may not name a conversion account`() = runTest {
        openAsset(1)

        // The conversion rows exist only because this boundary completed a cross-currency
        // operation. Nothing outside it may ask for one.
        assertFailsWith<UnbalancedTransactionException> {
            writer.writeEntries(
                transactionId = 1,
                legs = listOf(TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, accountId = 1)),
                contra = ContraLeg(AccountType.CONVERSION),
            )
        }
        assertTrue(entryDao.inserted.isEmpty())
    }

    // endregion
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
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): com.neoutils.finsight.database.dao.AccountPeriodTotals? = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(dimensionId: Long, yearMonth: String): Int = throw NotImplementedError()
    override suspend fun balanceOf(accountId: Long) = accountTotal(accountId)
    override suspend fun dimensionPeriodTotals(dimensionId: Long): List<com.neoutils.finsight.database.dao.DimensionPeriodTotals> = throw NotImplementedError()
    override suspend fun liabilityMonthTotals(yearMonth: String): List<com.neoutils.finsight.database.dao.LiabilityMonthTotals> = throw NotImplementedError()
    override suspend fun assetMonthTotals(yearMonth: String): List<com.neoutils.finsight.database.dao.AssetMonthTotals> = throw NotImplementedError()
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
    override suspend fun scopeStats(scopeIds: List<Long>, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): List<com.neoutils.finsight.database.dao.ScopeStatsTotals> = throw NotImplementedError()
    override suspend fun balanceUpToMonth(accountId: Long, yearMonth: String) = accountTotal(accountId)
    override suspend fun balanceUpToMonthByType(type: String, yearMonth: String) = totalsByCurrency(inserted)
    override suspend fun dimensionBalanceInMonth(dimensionId: Long, yearMonth: String) =
        totalsByCurrency(inserted.filter { it.dimensionId == dimensionId })
    override suspend fun dimensionNaturalBalance(dimensionId: Long) =
        totalsByCurrency(inserted.filter { it.dimensionId == dimensionId })
    override suspend fun naturalBalanceByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionTotal> =
        inserted.filter { it.dimensionId in dimensionIds }
            .groupBy { it.dimensionId!! }
            .flatMap { (id, entries) ->
                entries.groupBy { it.currency }
                    .map { (currency, rows) -> com.neoutils.finsight.database.dao.DimensionTotal(id, currency, rows.sumOf { it.amount }) }
            }
    override suspend fun periodTotalsByDimension(dimensionIds: List<Long>): List<com.neoutils.finsight.database.dao.DimensionPeriodTotalsRow> = throw NotImplementedError()
    override suspend fun netWorthCents() = totalsByCurrency(inserted)

    /** The fake reads the ledger the way the real queries do: grouped by currency. */
    private fun totalsByCurrency(entries: List<EntryEntity>) = entries
        .groupBy { it.currency }
        .map { (currency, rows) -> com.neoutils.finsight.database.dao.CurrencyTotal(currency, rows.sumOf { it.amount }) }

    private fun accountTotal(accountId: Long) = inserted
        .filter { it.accountId == accountId }
        .let { rows -> rows.firstOrNull()?.let { com.neoutils.finsight.database.dao.CurrencyTotal(it.currency, rows.sumOf { row -> row.amount }) } }
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
    // Currency-aware, because a system account is identified by the triple: one per
    // currency, and `EQUITY` holds two of them.
    override suspend fun getByTypeNameAndCurrency(
        type: AccountEntity.Type,
        name: String,
        currency: String,
    ): AccountEntity? = accounts.values.firstOrNull {
        it.type == type && it.name == name && it.currency == currency
    }
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


