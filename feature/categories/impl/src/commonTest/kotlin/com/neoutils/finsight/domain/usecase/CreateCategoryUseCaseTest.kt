@file:OptIn(ExperimentalTime::class)

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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The rules the category form used to carry itself: the name is validated, it is
 * stored trimmed, and the creation instant is read here — not supplied by whoever
 * calls. One owner, so the screen and the agent create the same category.
 */
class CreateCategoryUseCaseTest {

    private val existing = Category(
        id = 1, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10,
    )

    private fun useCase(repo: RecordingCategoryRepository) = CreateCategoryUseCaseImpl(
        categoryRepository = repo,
        validateCategoryName = ValidateCategoryNameUseCase(repo),
    )

    @Test
    fun `answers the category as stored, identity included`() = runTest {
        // A caller that cannot name what it just created cannot report it either, and a
        // surface answering a request from outside has nothing else to point at.
        val repo = RecordingCategoryRepository()

        val created = useCase(repo)(
            name = "Transport",
            iconKey = "car",
            type = Category.Type.EXPENSE,
        ).getOrNull()

        assertEquals(repo.inserted.single().name, created?.name)
        assertTrue((created?.id ?: 0L) != 0L, "the identity the store gave it")
    }

    @Test
    fun `creates the category with the name trimmed`() = runTest {
        val repo = RecordingCategoryRepository()

        val result = useCase(repo)(
            name = "  Transport  ",
            iconKey = "car",
            type = Category.Type.EXPENSE,
        )

        assertTrue(result.isRight())
        val created = repo.inserted.single()
        assertEquals("Transport", created.name)
        assertEquals("car", created.icon.key)
        assertEquals(Category.Type.EXPENSE, created.type)
    }

    @Test
    fun `stamps the creation instant itself`() = runTest {
        val repo = RecordingCategoryRepository()
        val before = Clock.System.now().toEpochMilliseconds()

        useCase(repo)(name = "Transport", iconKey = "car", type = Category.Type.INCOME)

        val created = repo.inserted.single()
        assertTrue(
            created.createdAt >= before,
            "the creation instant is read when the category is created, not supplied",
        )
    }

    @Test
    fun `a blank name is refused and nothing is written`() = runTest {
        val repo = RecordingCategoryRepository()

        val error = assertIs<CategoryException>(
            useCase(repo)(name = "   ", iconKey = "car", type = Category.Type.EXPENSE).leftOrNull()
        )

        assertEquals(CategoryError.EMPTY_NAME, error.error)
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun `a name already taken is refused and nothing is written`() = runTest {
        // Uniqueness spans archived categories and ignores case — the validator owns
        // that rule, and the use case consumes it rather than restating it.
        val repo = RecordingCategoryRepository(existing = listOf(existing))

        val error = assertIs<CategoryException>(
            useCase(repo)(name = "food", iconKey = "food", type = Category.Type.EXPENSE).leftOrNull()
        )

        assertEquals(CategoryError.ALREADY_EXIST, error.error)
        assertTrue(repo.inserted.isEmpty())
    }
}
