package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A name stays taken after archiving: closing keeps the row and its name, and history
 * still renders it, so a new category may not reuse the name of an archived one. The
 * `existsByName` query the form validates against SHALL span archived categories — the
 * only path back to that name is to unarchive, not to recreate.
 */
class CategoryNameUniquenessSpansArchivedTest {

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

    private fun category(name: String) = Category(
        name = name,
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
    )

    private suspend fun archive(name: String) {
        repository.insert(category(name))
        val stored = db.categoryDao().getAllCategoriesIncludingClosed().single { it.name == name }
        db.categoryDao().archive(stored.id)
    }

    @Test
    fun `an archived name is still taken`() = runTest {
        archive("Mercado")

        // ignoreId = 0L: a creation ignores nothing, so the archived row counts.
        assertTrue(repository.existsByName("Mercado", ignoreId = 0L))
    }

    @Test
    fun `the archived name check is case-insensitive`() = runTest {
        archive("Mercado")

        assertTrue(repository.existsByName("mercado", ignoreId = 0L))
        assertTrue(repository.existsByName("MERCADO", ignoreId = 0L))
    }

    @Test
    fun `an unrelated name is free`() = runTest {
        archive("Mercado")

        assertFalse(repository.existsByName("Farmácia", ignoreId = 0L))
    }
}
