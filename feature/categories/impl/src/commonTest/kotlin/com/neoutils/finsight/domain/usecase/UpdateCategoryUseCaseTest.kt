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

/**
 * The edit the category form used to perform itself: validate, trim, and write the
 * name and icon onto the category as it is now — leaving its type, its dimension and
 * its creation instant alone.
 */
class UpdateCategoryUseCaseTest {

    private val category = Category(
        id = 1, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 42L, dimensionId = 10,
    )

    private val other = Category(
        id = 2, name = "Transport", icon = CategoryLazyIcon("car"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 20,
    )

    private fun useCase(repo: RecordingCategoryRepository) = UpdateCategoryUseCaseImpl(
        categoryRepository = repo,
        validateCategoryName = ValidateCategoryNameUseCase(repo),
    )

    @Test
    fun `writes the trimmed name and the icon onto the resolved category`() = runTest {
        val repo = RecordingCategoryRepository(existing = listOf(category))

        val result = useCase(repo)(categoryId = category.id, name = "  Groceries  ", iconKey = "cart")

        assertTrue(result.isRight())
        val updated = repo.updated.single()
        assertEquals("Groceries", updated.name)
        assertEquals("cart", updated.icon.key)
    }

    @Test
    fun `leaves everything the caller did not name untouched`() = runTest {
        val repo = RecordingCategoryRepository(existing = listOf(category))

        useCase(repo)(categoryId = category.id, name = "Groceries", iconKey = "cart")

        val updated = repo.updated.single()
        assertEquals(category.id, updated.id)
        assertEquals(category.type, updated.type)
        assertEquals(category.dimensionId, updated.dimensionId)
        assertEquals(category.createdAt, updated.createdAt)
    }

    @Test
    fun `keeping its own name is not a clash with itself`() = runTest {
        val repo = RecordingCategoryRepository(existing = listOf(category))

        val result = useCase(repo)(categoryId = category.id, name = "Food", iconKey = "cart")

        assertTrue(result.isRight())
        assertEquals("Food", repo.updated.single().name)
    }

    @Test
    fun `a name another category already holds is refused and nothing is written`() = runTest {
        val repo = RecordingCategoryRepository(existing = listOf(category, other))

        val error = assertIs<CategoryException>(
            useCase(repo)(categoryId = category.id, name = "transport", iconKey = "cart").leftOrNull()
        )

        assertEquals(CategoryError.ALREADY_EXIST, error.error)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `a blank name is refused and nothing is written`() = runTest {
        val repo = RecordingCategoryRepository(existing = listOf(category))

        val error = assertIs<CategoryException>(
            useCase(repo)(categoryId = category.id, name = "   ", iconKey = "cart").leftOrNull()
        )

        assertEquals(CategoryError.EMPTY_NAME, error.error)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `an identity that matches no category is refused and nothing is written`() = runTest {
        val repo = RecordingCategoryRepository()

        val error = assertIs<CategoryException>(
            useCase(repo)(categoryId = 404L, name = "Groceries", iconKey = "cart").leftOrNull()
        )

        assertEquals(CategoryError.NOT_FOUND, error.error)
        assertTrue(repo.updated.isEmpty())
    }
}
