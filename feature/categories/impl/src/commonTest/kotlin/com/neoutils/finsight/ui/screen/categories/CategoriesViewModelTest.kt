@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.categories

import app.cash.turbine.test
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.categories_expense
import com.neoutils.finsight.resources.categories_income
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CategoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private class FakeCategoryRepository(categories: List<Category>) : ICategoryRepository {
        private val all = MutableStateFlow(categories)
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = all
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = all.value
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = null
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(null)
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private fun category(id: Long, name: String, type: Category.Type, archived: Boolean = false) = Category(
        id = id, name = name, icon = CategoryLazyIcon("food"),
        type = type, createdAt = id, isArchived = archived, dimensionId = id,
    )

    private fun viewModel(categories: List<Category>): CategoriesViewModel {
        val repo = FakeCategoryRepository(categories)
        return CategoriesViewModel(
            categoryRepository = repo,
            createDefaultCategories = CreateDefaultCategoriesUseCase(repo),
            crashlytics = FakeCrashlytics(),
        )
    }

    @Test
    fun `ACTIVE splits into expense and income sections and excludes archived`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                category(1, "Food", Category.Type.EXPENSE),
                category(2, "Salary", Category.Type.INCOME),
                category(3, "Old", Category.Type.EXPENSE, archived = true),
            )
        )
        vm.uiState.test {
            assertEquals(CategoriesUiState.Loading, awaitItem())
            val content = assertIs<CategoriesUiState.Content>(awaitItem())
            assertEquals(CategoryFilter.ACTIVE, content.filter)
            assertEquals(
                listOf(Res.string.categories_expense, Res.string.categories_income),
                content.sections.map { it.header },
            )
            val shown = content.sections.flatMap { it.categories }
            assertEquals(listOf("Food", "Salary"), shown.map { it.name })
            assertTrue(shown.none { it.isArchived })
        }
    }

    @Test
    fun `ARCHIVED lists only archived in a single headerless section`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                category(1, "Food", Category.Type.EXPENSE),
                category(2, "Old", Category.Type.EXPENSE, archived = true),
            )
        )
        vm.uiState.test {
            assertEquals(CategoriesUiState.Loading, awaitItem())
            assertIs<CategoriesUiState.Content>(awaitItem())

            vm.onAction(CategoriesAction.SelectFilter(CategoryFilter.ARCHIVED))

            val content = assertIs<CategoriesUiState.Content>(awaitItem())
            val section = content.sections.single()
            assertEquals(null, section.header)
            assertEquals(listOf("Old"), section.categories.map { it.name })
        }
    }

    @Test
    fun `an empty database yields the big empty state`() = runTest(dispatcher) {
        val vm = viewModel(emptyList())
        vm.uiState.test {
            assertEquals(CategoriesUiState.Loading, awaitItem())
            assertIs<CategoriesUiState.Empty>(awaitItem())
        }
    }
}
