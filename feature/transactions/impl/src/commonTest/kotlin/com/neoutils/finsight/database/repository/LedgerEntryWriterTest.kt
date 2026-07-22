package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CategoryWithArchival
import com.neoutils.finsight.database.dao.CreditCardWithArchival
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val DATE = LocalDate(2026, 1, 1)

class LedgerEntryWriterTest {

    private val entryDao = FakeEntryDao()
    private val accountDao = FakeAccountDao()
    private val categoryDao = FakeCategoryDao()
    private val creditCardDao = FakeCreditCardDao()
    private val dimensionDao = FakeDimensionDao()

    private val writer = LedgerEntryWriter(entryDao, accountDao, categoryDao, creditCardDao, dimensionDao)

    private fun assetAccount(id: Long) = Account(id = id, name = "Acc $id")

    @Test
    fun `given an expense when written then two entries sum to zero`() = runTest {
        categoryDao.categories[1L] = CategoryEntity(id = 1, name = "Food", iconKey = "food", type = CategoryEntity.Type.EXPENSE, accountId = 10)
        val expense = TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = 50.0,
            account = assetAccount(1),
            category = Category(id = 1, name = "Food", icon = CategoryLazyIcon("food"), type = Category.Type.EXPENSE, createdAt = 0),
        )

        writer.writeEntries(transactionId = 1, legs = listOf(expense))

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-5000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        assertEquals(5000L, entryDao.inserted.first { it.accountId == 10L }.amount)
    }

    @Test
    fun `given a transfer when written then both legs balance without synthesis`() = runTest {
        val out = TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, account = assetAccount(1))
        val income = TransactionLeg(type = TransactionType.INCOME, amount = 100.0, account = assetAccount(2))

        writer.validate(listOf(out, income))
        writer.writeEntries(transactionId = 2, legs = listOf(out, income))

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-10000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        assertEquals(10000L, entryDao.inserted.first { it.accountId == 2L }.amount)
    }

    @Test
    fun `given an adjustment when written then contra is a created reconciliation equity account`() = runTest {
        val adjustment = TransactionLeg(type = TransactionType.ADJUSTMENT, amount = 30.0, account = assetAccount(1))

        writer.writeEntries(transactionId = 3, legs = listOf(adjustment))

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        val reconciliation = accountDao.accounts.values.first { it.type == AccountEntity.Type.EQUITY }
        assertEquals(-3000L, entryDao.inserted.first { it.accountId == reconciliation.id }.amount)
    }

    @Test
    fun `given an invoice payment when written then the bank account is debited and only the card leg tags the invoice`() = runTest {
        // Card 100 already promoted to ledger account 200.
        creditCardDao.cards[100L] = CreditCardEntity(id = 100, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 200)
        val card = CreditCard(id = 100, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20)
        accountDao.accounts[200L] = AccountEntity(id = 200, name = "Card", type = AccountEntity.Type.LIABILITY)
        accountDao.accounts[1L] = AccountEntity(id = 1, name = "Acc 1", type = AccountEntity.Type.ASSET)
        dimensionDao.insert(DimensionEntity(id = 5, kind = DimensionKind.INVOICE))
        val invoice = Invoice(
            id = 5,
            creditCard = card,
            dimensionId = 5,
            openingMonth = YearMonth(2026, 1),
            closingMonth = YearMonth(2026, 2),
            dueMonth = YearMonth(2026, 2),
            status = Invoice.Status.CLOSED,
        )
        // The paying leg carries account + card + invoice (as the real use case builds it).
        val accountLeg = TransactionLeg(type = TransactionType.EXPENSE, amount = 50.0, account = assetAccount(1), creditCard = card, invoice = invoice)
        val cardLeg = TransactionLeg(type = TransactionType.INCOME, amount = 50.0, creditCard = card, invoice = invoice)

        writer.writeEntries(transactionId = 4, legs = listOf(accountLeg, cardLeg))

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        val bankEntry = entryDao.inserted.first { it.accountId == 1L }
        assertEquals(-5000L, bankEntry.amount) // bank account is debited
        assertEquals(null, bankEntry.dimensionId) // account leg must NOT carry the dimension
        val cardEntry = entryDao.inserted.first { it.accountId == 200L }
        assertEquals(5000L, cardEntry.amount) // liability leg reduces the owed
        assertEquals(5L, cardEntry.dimensionId) // only the card leg carries the dimension
    }

    @Test
    fun `given an archived category when written then the write is accepted`() = runTest {
        // A category holds no money: closing one strands nothing, so it carries no
        // invariant a write could break. Refusing here froze editing every old
        // transaction whose category was archived meanwhile — a rule the ledger
        // never had. Keeping an archived category out of a *new* transaction is the
        // selector's job (it lists open ones).
        accountDao.accounts[10L] = AccountEntity(id = 10, name = "Food", type = AccountEntity.Type.EXPENSE, currency = "BRL", isArchived = true)
        categoryDao.categories[1L] = CategoryEntity(id = 1, name = "Food", iconKey = "food", type = CategoryEntity.Type.EXPENSE, accountId = 10)
        val expense = TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = 50.0,
            account = assetAccount(1),
            category = Category(id = 1, name = "Food", icon = CategoryLazyIcon("food"), type = Category.Type.EXPENSE, createdAt = 0),
        )

        writer.writeEntries(transactionId = 1, legs = listOf(expense))

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(5000L, entryDao.inserted.first { it.accountId == 10L }.amount)
    }

    @Test
    fun `given an archived account when written then the write is rejected`() = runTest {
        // The monetary case, which does carry an invariant: closing an ASSET
        // required a zero balance, so a new entry there strands money.
        accountDao.accounts[1L] = AccountEntity(id = 1, name = "Checking", type = AccountEntity.Type.ASSET, currency = "BRL", isArchived = true)
        val expense = TransactionLeg(
            type = TransactionType.EXPENSE,
            amount = 50.0,
            account = assetAccount(1),
        )

        val error = assertFailsWith<ClosedAccountException> {
            writer.writeEntries(transactionId = 1, legs = listOf(expense))
        }
        assertEquals(LedgerError.ClosedAccount(ClosedFacade.ACCOUNT), error.error)
        assertTrue(entryDao.inserted.isEmpty())
    }

    @Test
    fun `given an unbalanced multi-leg transaction when validated then it is rejected`() {
        val a = TransactionLeg(type = TransactionType.EXPENSE, amount = 100.0, account = assetAccount(1))
        val b = TransactionLeg(type = TransactionType.INCOME, amount = 80.0, account = assetAccount(2))

        assertFailsWith<UnbalancedTransactionException> { writer.validate(listOf(a, b)) }
    }
}

private class FakeEntryDao : EntryDao {
    val inserted = mutableListOf<EntryEntity>()
    override suspend fun insert(entry: EntryEntity): Long { inserted += entry; return inserted.size.toLong() }
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> { inserted += entries; return entries.indices.map { it.toLong() } }
    override suspend fun delete(entry: EntryEntity) = Unit
    override suspend fun deleteByTransactionId(transactionId: Long) { inserted.removeAll { it.transactionId == transactionId } }
    override suspend fun getAll(): List<EntryEntity> = inserted
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override fun observeEntryCount(): Flow<Long> = flowOf(0)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun getByTransactionId(transactionId: Long): List<EntryEntity> = inserted.filter { it.transactionId == transactionId }
    override suspend fun getEntriesWithAccountByTransactionId(transactionId: Long): List<com.neoutils.finsight.database.dao.EntryWithAccount> = throw NotImplementedError()
    override fun observeEntriesWithAccountByTransactionId(transactionId: Long): Flow<List<com.neoutils.finsight.database.dao.EntryWithAccount>> = throw NotImplementedError()
    override suspend fun accountPeriodTotals(accountId: Long, yearMonth: String): com.neoutils.finsight.database.dao.AccountPeriodTotals = throw NotImplementedError()
    override suspend fun entryCountInMonth(accountId: Long, yearMonth: String): Int = throw NotImplementedError()
    override fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun naturalBalanceOf(accountId: Long, currency: String): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun balanceOf(accountId: Long): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun dimensionPeriodTotals(dimensionId: Long): com.neoutils.finsight.database.dao.InvoicePeriodTotals = throw NotImplementedError()
    override suspend fun cardMonthTotals(yearMonth: String): com.neoutils.finsight.database.dao.CardMonthTotals = throw NotImplementedError()
    override suspend fun categoryTotalsWithSiblingLeg(
        categoryType: String,
        start: kotlinx.datetime.LocalDate,
        end: kotlinx.datetime.LocalDate,
        siblingAccountIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.CategoryAccountTotal> = throw NotImplementedError()
    override suspend fun categoryTotalsForDimensions(
        categoryType: String,
        dimensionIds: List<Long>,
    ): List<com.neoutils.finsight.database.dao.CategoryAccountTotal> = throw NotImplementedError()
    override suspend fun reportStats(scopeIds: List<Long>, startDate: kotlinx.datetime.LocalDate, endDate: kotlinx.datetime.LocalDate): com.neoutils.finsight.database.dao.ReportStatsTotals = throw NotImplementedError()
    override suspend fun balanceUpToMonth(accountId: Long, yearMonth: String): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun assetsBalanceUpToMonth(yearMonth: String): Long = inserted.sumOf { it.amount }
    override suspend fun balanceInMonth(accountId: Long, yearMonth: String): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
    override suspend fun dimensionNaturalBalance(dimensionId: Long): Long = inserted.filter { it.dimensionId == dimensionId }.sumOf { it.amount }
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

private class FakeCategoryDao : CategoryDao {
    override suspend fun getAllCategoriesIncludingClosed(): List<CategoryWithArchival> = emptyList()
    override suspend fun getCategoryWithArchivalById(id: Long): CategoryWithArchival? = null
    override fun observeCategoryWithArchivalById(id: Long): Flow<CategoryWithArchival?> = flowOf(null)
    override fun observeAllCategoriesIncludingClosed(): Flow<List<CategoryWithArchival>> = flowOf(emptyList())
    val categories = linkedMapOf<Long, CategoryEntity>()
    override suspend fun getCategoryById(id: Long): CategoryEntity? = categories[id]
    override suspend fun update(category: CategoryEntity) { categories[category.id] = category }
    override fun observeAllCategories(): Flow<List<CategoryEntity>> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<CategoryEntity> = categories.values.toList()
    override fun observeCategoriesByType(type: CategoryEntity.Type): Flow<List<CategoryEntity>> = throw NotImplementedError()
    override fun observeCategoryById(id: Long): Flow<CategoryEntity?> = throw NotImplementedError()
    override suspend fun insert(category: CategoryEntity) { categories[category.id] = category }
    override suspend fun delete(category: CategoryEntity) { categories.remove(category.id) }
}

private class FakeCreditCardDao : CreditCardDao {
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCardWithArchival> = emptyList()
    override fun observeCreditCardWithArchivalById(creditCardId: Long): Flow<CreditCardWithArchival?> = flowOf(null)
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCardWithArchival>> = flowOf(emptyList())
    val cards = linkedMapOf<Long, CreditCardEntity>()
    override suspend fun getCreditCardById(id: Long): CreditCardEntity? = cards[id]
    override suspend fun update(creditCard: CreditCardEntity) { cards[creditCard.id] = creditCard }
    override fun observeAllCreditCards(): Flow<List<CreditCardEntity>> = throw NotImplementedError()
    override suspend fun getAllCreditCardsList(): List<CreditCardEntity> = cards.values.toList()
    override fun observeCreditCardById(id: Long): Flow<CreditCardEntity?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCardEntity): Long { cards[creditCard.id] = creditCard; return creditCard.id }
    override suspend fun delete(creditCard: CreditCardEntity) { cards.remove(creditCard.id) }
}
