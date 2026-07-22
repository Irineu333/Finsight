package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The invariant eager emission buys (design D4): a category always has its ledger
 * dimension, so nothing downstream has to handle its absence — and removing the
 * category takes the dimension with it, in the same transaction, without the ledger
 * losing a cent.
 */
class CategoryDimensionEagerCreationTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val repository = CategoryRepository(
        database = db,
        dao = db.categoryDao(),
        dimensionDao = db.dimensionDao(),
        mapper = CategoryMapper(),
    )

    private fun category(name: String, type: Category.Type) = Category(
        name = name,
        icon = CategoryLazyIcon("food"),
        type = type,
        createdAt = 0L,
    )

    @Test
    fun `inserting a category emits its dimension`() = runTest {
        repository.insert(category("Food", Category.Type.EXPENSE))
        repository.insert(category("Salary", Category.Type.INCOME))

        val stored = db.categoryDao().getAllCategories()
        assertEquals(2, stored.size)

        stored.forEach { category ->
            val dimension = db.dimensionDao().getById(category.dimensionId)
            assertNotNull(dimension, "category ${category.name} has no dimension")
            assertEquals(DimensionKind.CATEGORY, dimension.kind)
        }
        // Two categories, two distinct identities — a sum by dimension never mixes them.
        assertEquals(2, stored.map { it.dimensionId }.distinct().size)
    }

    @Test
    fun `a category no longer owns a chart account`() = runTest {
        repository.insert(category("Food", Category.Type.EXPENSE))

        // The chart holds only what is accounting: the nominal accounts are created
        // by the write boundary, on the first posting, and never by the facade.
        assertEquals(emptyList(), db.accountDao().getAllLedgerAccounts())
    }

    @Test
    fun `deleting a category removes its dimension and unclassifies its entries`() = runTest {
        repository.insert(category("Food", Category.Type.EXPENSE))
        val stored = db.categoryDao().getAllCategories().single()

        db.accountDao().insert(AccountEntity(id = 1, name = "A", type = AccountEntity.Type.ASSET))
        db.accountDao().insert(AccountEntity(id = 2, name = "Despesas", type = AccountEntity.Type.EXPENSE))
        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Groceries", date = LocalDate(2026, 3, 10)),
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -5000),
                EntryEntity(transactionId = transactionId, accountId = 2, amount = 5000, dimensionId = stored.dimensionId),
            )
        )

        repository.delete(CategoryMapper().toDomain(stored))

        assertEquals(0, db.categoryDao().getAllCategories().size)
        assertNull(db.dimensionDao().getById(stored.dimensionId))
        // The legs survive, unclassified: same amounts, same accounts, same balances.
        val entries = db.entryDao().getByTransactionId(transactionId)
        assertEquals(listOf(null, null), entries.map { it.dimensionId })
        assertEquals(-5000L, db.entryDao().balanceOf(1))
        assertEquals(5000L, db.entryDao().balanceOf(2))
    }
}
