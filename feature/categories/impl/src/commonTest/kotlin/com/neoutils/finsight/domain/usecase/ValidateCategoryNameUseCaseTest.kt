package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateCategoryNameUseCaseTest {

    private fun category(id: Long, name: String) = Category(
        id = id, name = name, icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = id,
    )

    private fun useCase(existing: List<Category> = emptyList()) =
        ValidateCategoryNameUseCase(RecordingCategoryRepository(existing))

    @Test
    fun `a name that is only spaces is rejected as empty`() = runTest {
        assertEquals(CategoryError.EMPTY_NAME, useCase()("   ").leftOrNull())
    }

    @Test
    fun `a duplicate name is rejected case-insensitively`() = runTest {
        val result = useCase(listOf(category(id = 1, name = "Mercado")))("  mercado ")
        assertEquals(CategoryError.ALREADY_EXIST, result.leftOrNull())
    }

    @Test
    fun `an edit keeping its own name is accepted`() = runTest {
        val result = useCase(listOf(category(id = 1, name = "Mercado")))("Mercado", ignoreId = 1)
        assertEquals("Mercado", result.getOrNull())
    }

    @Test
    fun `a unique name is accepted trimmed`() = runTest {
        assertEquals("Lazer", useCase()("  Lazer  ").getOrNull())
    }
}
