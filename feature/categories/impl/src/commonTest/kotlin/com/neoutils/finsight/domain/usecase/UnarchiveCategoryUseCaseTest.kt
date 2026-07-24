package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnarchiveCategoryUseCaseTest {

    private val category = Category(
        id = 7, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10, isArchived = true,
    )

    @Test
    fun `unarchives the category and succeeds`() = runTest {
        val repo = RecordingCategoryRepository()
        val result = UnarchiveCategoryUseCase(repo)(category)

        assertTrue(result.isRight())
        assertEquals(listOf(category.id), repo.unarchived)
    }
}
