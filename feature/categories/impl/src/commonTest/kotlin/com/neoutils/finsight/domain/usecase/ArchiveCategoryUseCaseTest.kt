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

class ArchiveCategoryUseCaseTest {

    private val category = Category(
        id = 7, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10,
    )

    private fun repository() = RecordingCategoryRepository(existing = listOf(category))

    @Test
    fun `archives the category and succeeds`() = runTest {
        val repo = repository()
        val result = ArchiveCategoryUseCaseImpl(repo)(category)

        assertTrue(result.isRight())
        assertEquals(listOf(category.id), repo.archived)
    }

    @Test
    fun `an identity that matches no category is refused and archives nothing`() = runTest {
        // Archiving is a blind UPDATE by id: without the guard the caller would be told
        // a category was retired that never existed.
        val repo = RecordingCategoryRepository()
        val error = assertIs<CategoryException>(ArchiveCategoryUseCaseImpl(repo)(404L).leftOrNull())

        assertEquals(CategoryError.NOT_FOUND, error.error)
        assertTrue(repo.archived.isEmpty())
    }

    @Test
    fun `the aggregate form archives the same category the id form does`() = runTest {
        val byAggregate = repository()
        val byId = repository()

        ArchiveCategoryUseCaseImpl(byAggregate)(category)
        ArchiveCategoryUseCaseImpl(byId)(category.id)

        assertEquals(byAggregate.archived, byId.archived)
    }
}
