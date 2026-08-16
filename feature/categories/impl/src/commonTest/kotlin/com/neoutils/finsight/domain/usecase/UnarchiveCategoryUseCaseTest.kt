package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnarchiveCategoryUseCaseTest {

    private val category = Category(
        id = 7, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10, isArchived = true,
    )

    private fun repository() = RecordingCategoryRepository(existing = listOf(category))

    @Test
    fun `unarchives the category and succeeds`() = runTest {
        val repo = repository()
        val result = UnarchiveCategoryUseCaseImpl(repo)(category)

        assertTrue(result.isRight())
        assertEquals(listOf(category.id), repo.unarchived)
    }

    @Test
    fun `an identity that matches no category is refused and reopens nothing`() = runTest {
        // Reopening is a blind UPDATE by id: without the guard the caller would be told
        // a category came back that never existed.
        val repo = RecordingCategoryRepository()
        val error = assertIs<CategoryException>(UnarchiveCategoryUseCaseImpl(repo)(404L).leftOrNull())

        assertEquals(CategoryError.NOT_FOUND, error.error)
        assertTrue(repo.unarchived.isEmpty())
    }

    @Test
    fun `the aggregate form reopens the same category the id form does`() = runTest {
        val byAggregate = repository()
        val byId = repository()

        UnarchiveCategoryUseCaseImpl(byAggregate)(category)
        UnarchiveCategoryUseCaseImpl(byId)(category.id)

        assertEquals(byAggregate.unarchived, byId.unarchived)
    }
}
