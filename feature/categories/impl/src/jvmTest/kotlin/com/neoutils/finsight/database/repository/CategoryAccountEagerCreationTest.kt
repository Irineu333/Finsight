package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The invariant eager creation buys (design D21): a category always has its
 * chart-of-accounts row, so nothing downstream has to handle its absence.
 */
class CategoryAccountEagerCreationTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val repository = CategoryRepository(
        database = db,
        dao = db.categoryDao(),
        accountDao = db.accountDao(),
        mapper = CategoryMapper(),
    )

    private fun category(name: String, type: Category.Type) = Category(
        name = name,
        icon = CategoryLazyIcon("food"),
        type = type,
        createdAt = 0L,
    )

    @Test
    fun `inserting a category creates its account with the matching type`() = runTest {
        repository.insert(category("Food", Category.Type.EXPENSE))
        repository.insert(category("Salary", Category.Type.INCOME))

        val stored = db.categoryDao().getAllCategories()
        assertEquals(2, stored.size)

        val accounts = db.accountDao().getAllLedgerAccounts().associateBy { it.id }
        val food = stored.first { it.name == "Food" }
        val salary = stored.first { it.name == "Salary" }

        assertEquals(AccountEntity.Type.EXPENSE, accounts.getValue(food.accountId).type)
        assertEquals(AccountEntity.Type.INCOME, accounts.getValue(salary.accountId).type)
        assertEquals("Food", accounts.getValue(food.accountId).name)
    }

    @Test
    fun `renaming a category moves its account too`() = runTest {
        repository.insert(category("Food", Category.Type.EXPENSE))
        val stored = db.categoryDao().getAllCategories().single()

        repository.update(CategoryMapper().toDomain(stored).copy(name = "Groceries"))

        val account = db.accountDao().getAccountById(stored.accountId)
        assertEquals("Groceries", account?.name)
    }
}
