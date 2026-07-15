package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.domain.error.UnbalancedOperationException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
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

    private val writer = LedgerEntryWriter(entryDao, accountDao, categoryDao, creditCardDao)

    private fun assetAccount(id: Long) = Account(id = id, name = "Acc $id")

    @Test
    fun `given an expense when written then two entries sum to zero`() = runTest {
        categoryDao.categories[1L] = CategoryEntity(id = 1, name = "Food", iconKey = "food", type = CategoryEntity.Type.EXPENSE, accountId = 10)
        val expense = Transaction(
            type = Transaction.Type.EXPENSE,
            amount = 50.0,
            title = null,
            date = DATE,
            account = assetAccount(1),
            category = Category(id = 1, name = "Food", icon = CategoryLazyIcon("food"), type = Category.Type.EXPENSE, createdAt = 0),
        )

        writer.writeEntries(operationId = 1, transactions = listOf(expense))

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-5000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        assertEquals(5000L, entryDao.inserted.first { it.accountId == 10L }.amount)
    }

    @Test
    fun `given a transfer when written then both legs balance without synthesis`() = runTest {
        val out = Transaction(type = Transaction.Type.EXPENSE, amount = 100.0, title = null, date = DATE, account = assetAccount(1))
        val income = Transaction(type = Transaction.Type.INCOME, amount = 100.0, title = null, date = DATE, account = assetAccount(2))

        writer.validate(listOf(out, income))
        writer.writeEntries(operationId = 2, transactions = listOf(out, income))

        assertEquals(2, entryDao.inserted.size)
        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        assertEquals(-10000L, entryDao.inserted.first { it.accountId == 1L }.amount)
        assertEquals(10000L, entryDao.inserted.first { it.accountId == 2L }.amount)
    }

    @Test
    fun `given an adjustment when written then contra is a created reconciliation equity account`() = runTest {
        val adjustment = Transaction(type = Transaction.Type.ADJUSTMENT, amount = 30.0, title = null, date = DATE, account = assetAccount(1))

        writer.writeEntries(operationId = 3, transactions = listOf(adjustment))

        assertEquals(0L, entryDao.inserted.sumOf { it.amount })
        val reconciliation = accountDao.accounts.values.first { it.type == AccountEntity.Type.EQUITY }
        assertEquals(-3000L, entryDao.inserted.first { it.accountId == reconciliation.id }.amount)
    }

    @Test
    fun `given an unbalanced multi-leg operation when validated then it is rejected`() {
        val a = Transaction(type = Transaction.Type.EXPENSE, amount = 100.0, title = null, date = DATE, account = assetAccount(1))
        val b = Transaction(type = Transaction.Type.INCOME, amount = 80.0, title = null, date = DATE, account = assetAccount(2))

        assertFailsWith<UnbalancedOperationException> { writer.validate(listOf(a, b)) }
    }
}

private class FakeEntryDao : EntryDao {
    val inserted = mutableListOf<EntryEntity>()
    override suspend fun insert(entry: EntryEntity): Long { inserted += entry; return inserted.size.toLong() }
    override suspend fun insertAll(entries: List<EntryEntity>): List<Long> { inserted += entries; return entries.indices.map { it.toLong() } }
    override suspend fun delete(entry: EntryEntity) = Unit
    override suspend fun deleteByOperationId(operationId: Long) { inserted.removeAll { it.operationId == operationId } }
    override suspend fun getAll(): List<EntryEntity> = inserted
    override fun observeAll(): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun getByOperationId(operationId: Long): List<EntryEntity> = inserted.filter { it.operationId == operationId }
    override fun observeByAccountId(accountId: Long): Flow<List<EntryEntity>> = throw NotImplementedError()
    override suspend fun naturalBalanceOf(accountId: Long, currency: String): Long = inserted.filter { it.accountId == accountId }.sumOf { it.amount }
}

private class FakeAccountDao : AccountDao {
    val accounts = linkedMapOf<Long, AccountEntity>()
    private var seq = 100L
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
    val cards = linkedMapOf<Long, CreditCardEntity>()
    override suspend fun getCreditCardById(id: Long): CreditCardEntity? = cards[id]
    override suspend fun update(creditCard: CreditCardEntity) { cards[creditCard.id] = creditCard }
    override fun observeAllCreditCards(): Flow<List<CreditCardEntity>> = throw NotImplementedError()
    override suspend fun getAllCreditCardsList(): List<CreditCardEntity> = cards.values.toList()
    override fun observeCreditCardById(id: Long): Flow<CreditCardEntity?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCardEntity): Long { cards[creditCard.id] = creditCard; return creditCard.id }
    override suspend fun delete(creditCard: CreditCardEntity) { cards.remove(creditCard.id) }
}
