package com.neoutils.finsight.ui.modal.archiveCategory

import com.neoutils.finsight.RecordingAnalytics
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveCategoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private object SilentCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    /** Only the one write the use case makes; every other read is out of this test. */
    private class ArchivingCategories : ICategoryRepository {
        val archived = mutableListOf<Long>()
        override suspend fun archive(id: Long) { archived += id }
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override suspend fun getAllCategoriesIncludingClosed(): List<Category> = throw NotImplementedError()
        override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
        override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
        override suspend fun unarchive(id: Long) = throw NotImplementedError()
        override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    /** See `ArchiveAccountViewModelTest`: the same conflation, in the same shape. */
    @Test
    fun `retiring a category reports itself as an archive, not as a deletion`() = runTest(dispatcher) {
        val analytics = RecordingAnalytics()
        val repository = ArchivingCategories()
        val category = Category(
            id = 5L,
            name = "Food",
            icon = CategoryLazyIcon("shopping"),
            type = Category.Type.EXPENSE,
            createdAt = 0L,
            isArchived = false,
            dimensionId = 10L,
        )

        val viewModel = ArchiveCategoryViewModel(
            category = category,
            archiveCategoryUseCase = ArchiveCategoryUseCase(repository),
            modalManager = ModalManager(),
            analytics = analytics,
            crashlytics = SilentCrashlytics,
        )

        viewModel.archiveCategory()
        runCurrent()

        assertEquals(listOf(5L), repository.archived)
        assertEquals(listOf("archive_category"), analytics.events.map { it.name })
        assertEquals(mapOf("name" to "Food", "type" to "expense"), analytics.events.single().params)
    }
}
