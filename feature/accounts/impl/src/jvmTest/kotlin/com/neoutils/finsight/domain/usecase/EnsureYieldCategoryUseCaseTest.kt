package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The category exists because something needs it, is found by key rather than by
 * name, and is only ever created once — the three properties the whole design of the
 * yield account rests on.
 */
class EnsureYieldCategoryUseCaseTest {

    @Test
    fun `with no declared account the category does not exist`() = runTest {
        val repo = YieldCategoryStore()

        assertNull(repo.getCategoryBySystemKey(SystemCategoryKey.YIELD))
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun `the first declaration creates the category with its dimension`() = runTest {
        val repo = YieldCategoryStore()

        val category = EnsureYieldCategoryUseCase(repo)()

        assertEquals(SystemCategoryKey.YIELD, category.systemKey)
        assertEquals(Category.Type.INCOME, category.type)
        // The dimension is what every read separates by; a category without one
        // would make the whole separation silently return zero.
        assertTrue(category.dimensionId != 0L)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun `a second declaration creates no second category`() = runTest {
        val repo = YieldCategoryStore()
        val useCase = EnsureYieldCategoryUseCase(repo)

        val first = useCase()
        val second = useCase()

        assertEquals(first.id, second.id)
        assertEquals(first.dimensionId, second.dimensionId)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun `renaming the category does not break the identification`() = runTest {
        val repo = YieldCategoryStore()
        val useCase = EnsureYieldCategoryUseCase(repo)
        val created = useCase()

        repo.update(created.copy(name = "CDI", icon = CategoryLazyIcon("money")))

        val found = useCase()
        assertEquals(created.id, found.id)
        assertEquals(created.dimensionId, found.dimensionId)
        assertEquals("CDI", found.name)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun `an archived yield category is still found`() = runTest {
        val repo = YieldCategoryStore()
        val useCase = EnsureYieldCategoryUseCase(repo)
        val created = useCase()

        repo.archive(created.id)

        // Closing hides a facade from the selectors; it does not withdraw it from
        // the accounts already classifying their yield in it.
        assertEquals(created.id, useCase().id)
        assertEquals(1, repo.inserted.size)
    }
}

/** A category store that mints ids and dimensions on insert, as the real one does. */
internal class YieldCategoryStore : ICategoryRepository {

    val inserted = mutableListOf<Category>()
    private val rows = mutableListOf<Category>()
    private var nextId = 0L

    override suspend fun insert(category: Category) {
        nextId++
        val stored = category.copy(id = nextId, dimensionId = 100 + nextId)
        rows += stored
        inserted += stored
    }

    override suspend fun update(category: Category) = replace(category.id) { category }

    override suspend fun archive(id: Long) = replace(id) { it.copy(isArchived = true) }

    override suspend fun getCategoryBySystemKey(systemKey: String): Category? =
        rows.firstOrNull { it.systemKey == systemKey }

    override fun observeAllCategories(): Flow<List<Category>> = flowOf(rows.filter { !it.isArchived })
    override suspend fun getAllCategories(): List<Category> = rows.filter { !it.isArchived }
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = rows
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(rows)
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategoryById(id: Long): Category? = rows.firstOrNull { it.id == id }
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        rows.firstOrNull { it.dimensionId == dimensionId }
    override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(rows.firstOrNull { it.id == id })
    override suspend fun unarchive(id: Long) = replace(id) { it.copy(isArchived = false) }

    private fun replace(id: Long, transform: (Category) -> Category) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = transform(rows[index])
    }
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean =
        rows.any { it.name.equals(name, ignoreCase = true) && it.id != ignoreId }
    override suspend fun insertAll(categories: List<Category>) = categories.forEach { insert(it) }
    override suspend fun delete(category: Category) { rows.removeAll { it.id == category.id } }
}
