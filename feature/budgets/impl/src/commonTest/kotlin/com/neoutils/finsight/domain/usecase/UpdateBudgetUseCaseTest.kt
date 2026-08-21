package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.model.LimitType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The edit the budget form used to perform itself: validate, trim, resolve the limit,
 * and write the result onto the budget as it is now — leaving its denomination and its
 * creation instant alone.
 */
class UpdateBudgetUseCaseTest {

    private val budget = testBudget(id = 1L, title = "Groceries", currency = "USD")
    private val other = testBudget(id = 2L, title = "Transport")

    private fun useCase(repo: RecordingBudgetRepository) = UpdateBudgetUseCaseImpl(
        budgetRepository = repo,
        validateBudgetTitle = ValidateBudgetTitleUseCase(repo),
    )

    @Test
    fun `writes the trimmed title and the rest onto the resolved budget`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget))
        val categories = listOf(testCategory(id = 3L, name = "Market"))

        val result = useCase(repo)(
            budgetId = budget.id,
            title = "  Market  ",
            categories = categories,
            iconKey = "cart",
            limitType = LimitType.FIXED,
            amount = 800.0,
        )

        assertTrue(result.isRight())
        val updated = repo.updated.single()
        assertEquals("Market", updated.title)
        assertEquals("cart", updated.iconKey)
        assertEquals(categories, updated.categories)
        assertEquals(800.0, updated.amount)
    }

    @Test
    fun `leaves the denomination and the history untouched`() = runTest {
        // A limit is denominated once, at creation: reinterpreting a stored one would
        // silently rewrite the meaning of a number the user typed (design D12/D13).
        val repo = RecordingBudgetRepository(existing = listOf(budget))

        useCase(repo)(
            budgetId = budget.id,
            title = "Market",
            categories = budget.categories,
            iconKey = "cart",
            limitType = LimitType.FIXED,
            amount = 800.0,
        )

        val updated = repo.updated.single()
        assertEquals(budget.id, updated.id)
        assertEquals("USD", updated.currency)
        assertEquals(budget.createdAt, updated.createdAt)
    }

    @Test
    fun `switching to a fixed limit clears the share and the base income`() = runTest {
        val percentageBudget = testBudget(
            id = 1L,
            limitType = LimitType.PERCENTAGE,
            percentage = 30.0,
            recurringId = 7L,
        )
        val repo = RecordingBudgetRepository(existing = listOf(percentageBudget))

        useCase(repo)(
            budgetId = percentageBudget.id,
            title = percentageBudget.title,
            categories = percentageBudget.categories,
            iconKey = percentageBudget.iconKey,
            limitType = LimitType.FIXED,
            amount = 800.0,
        )

        val updated = repo.updated.single()
        assertEquals(800.0, updated.amount)
        assertNull(updated.percentage)
        assertNull(updated.recurringId)
    }

    @Test
    fun `switching to a percentage limit derives its amount from the base income`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget))

        useCase(repo)(
            budgetId = budget.id,
            title = budget.title,
            categories = budget.categories,
            iconKey = budget.iconKey,
            limitType = LimitType.PERCENTAGE,
            amount = 800.0,
            percentage = 25.0,
            baseIncome = testRecurring(amount = 4_000.0),
        )

        val updated = repo.updated.single()
        assertEquals(1_000.0, updated.amount)
        assertEquals(25.0, updated.percentage)
        assertEquals(7L, updated.recurringId)
    }

    @Test
    fun `a percentage limit without a base income is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget))

        val error = assertIs<BudgetException>(
            useCase(repo)(
                budgetId = budget.id,
                title = budget.title,
                categories = budget.categories,
                iconKey = budget.iconKey,
                limitType = LimitType.PERCENTAGE,
                percentage = 25.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.MISSING_BASE_INCOME, error.error)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `a negative limit is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget))

        val error = assertIs<BudgetException>(
            useCase(repo)(
                budgetId = budget.id,
                title = budget.title,
                categories = budget.categories,
                iconKey = budget.iconKey,
                limitType = LimitType.FIXED,
                amount = -800.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.NEGATIVE_LIMIT, error.error)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `keeping its own title is not a clash with itself`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget))

        val result = useCase(repo)(
            budgetId = budget.id,
            title = "Groceries",
            categories = budget.categories,
            iconKey = "cart",
            limitType = LimitType.FIXED,
            amount = 800.0,
        )

        assertTrue(result.isRight())
        assertEquals("Groceries", repo.updated.single().title)
    }

    @Test
    fun `a title another budget already holds is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(budget, other))

        val error = assertIs<BudgetException>(
            useCase(repo)(
                budgetId = budget.id,
                title = "transport",
                categories = budget.categories,
                iconKey = "cart",
                limitType = LimitType.FIXED,
                amount = 800.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.ALREADY_EXIST, error.error)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `an identity that matches no budget is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository()

        val error = assertIs<BudgetException>(
            useCase(repo)(
                budgetId = 404L,
                title = "Market",
                categories = listOf(testCategory()),
                iconKey = "cart",
                limitType = LimitType.FIXED,
                amount = 800.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.NOT_FOUND, error.error)
        assertTrue(repo.updated.isEmpty())
    }
}
