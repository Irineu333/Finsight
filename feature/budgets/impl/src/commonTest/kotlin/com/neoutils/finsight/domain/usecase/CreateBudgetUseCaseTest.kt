@file:OptIn(ExperimentalTime::class)

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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The rules the budget form used to carry itself: the title is validated and stored
 * trimmed, the creation instant is read here, and a `PERCENTAGE` limit's amount is
 * derived from the share and the recurring income it is a share of. One owner, so the
 * screen and the agent create the same budget.
 */
class CreateBudgetUseCaseTest {

    private fun useCase(repo: RecordingBudgetRepository) = CreateBudgetUseCaseImpl(
        budgetRepository = repo,
        validateBudgetTitle = ValidateBudgetTitleUseCase(repo),
    )

    @Test
    fun `answers the budget as stored, identity included`() = runTest {
        // A caller that cannot name what it just created cannot report it either, and a
        // surface answering a request from outside has nothing else to point at.
        val repo = RecordingBudgetRepository()

        val created = useCase(repo)(
            title = "Groceries",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.FIXED,
            amount = 500.0,
        ).getOrNull()

        assertEquals(repo.inserted.single().title, created?.title)
        assertTrue((created?.id ?: 0L) != 0L, "the identity the store gave it")
    }

    @Test
    fun `creates the budget with the title trimmed`() = runTest {
        val repo = RecordingBudgetRepository()

        val result = useCase(repo)(
            title = "  Groceries  ",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.FIXED,
            amount = 500.0,
        )

        assertTrue(result.isRight())
        val created = repo.inserted.single()
        assertEquals("Groceries", created.title)
        assertEquals("shopping", created.iconKey)
        assertEquals("BRL", created.currency)
        assertEquals(500.0, created.amount)
    }

    @Test
    fun `stamps the creation instant itself`() = runTest {
        val repo = RecordingBudgetRepository()
        val before = Clock.System.now().toEpochMilliseconds()

        useCase(repo)(
            title = "Groceries",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.FIXED,
            amount = 500.0,
        )

        assertTrue(
            repo.inserted.single().createdAt >= before,
            "the creation instant is read when the budget is created, not supplied",
        )
    }

    @Test
    fun `a fixed limit carries neither share nor base income`() = runTest {
        // Leaving them behind would make the budget read as a fraction of something the
        // user had already stopped measuring against.
        val repo = RecordingBudgetRepository()

        useCase(repo)(
            title = "Groceries",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.FIXED,
            amount = 500.0,
            percentage = 30.0,
            baseIncome = testRecurring(),
        )

        val created = repo.inserted.single()
        assertEquals(500.0, created.amount)
        assertNull(created.percentage)
        assertNull(created.recurringId)
    }

    @Test
    fun `a percentage limit derives its amount from the base income`() = runTest {
        // The path the form used to walk itself: 30% of a 3.000 salary is 900, and the
        // amount handed in is ignored because it is not what the limit is.
        val repo = RecordingBudgetRepository()

        useCase(repo)(
            title = "Groceries",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.PERCENTAGE,
            amount = 12.0,
            percentage = 30.0,
            baseIncome = testRecurring(amount = 3_000.0),
        )

        val created = repo.inserted.single()
        assertEquals(900.0, created.amount)
        assertEquals(30.0, created.percentage)
        assertEquals(7L, created.recurringId)
    }

    @Test
    fun `a percentage limit without a base income is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository()

        val error = assertIs<BudgetException>(
            useCase(repo)(
                title = "Groceries",
                categories = listOf(testCategory()),
                iconKey = "shopping",
                currency = "BRL",
                limitType = LimitType.PERCENTAGE,
                percentage = 30.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.MISSING_BASE_INCOME, error.error)
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun `a share nobody stated is zero, and is not refused`() = runTest {
        // The same answer the progress reads back for it, so the write and the read
        // agree rather than one of them inventing a refusal the other does not have.
        val repo = RecordingBudgetRepository()

        val result = useCase(repo)(
            title = "Groceries",
            categories = listOf(testCategory()),
            iconKey = "shopping",
            currency = "BRL",
            limitType = LimitType.PERCENTAGE,
            baseIncome = testRecurring(amount = 3_000.0),
        )

        assertTrue(result.isRight())
        assertEquals(0.0, repo.inserted.single().amount)
    }

    @Test
    fun `a blank title is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository()

        val error = assertIs<BudgetException>(
            useCase(repo)(
                title = "   ",
                categories = listOf(testCategory()),
                iconKey = "shopping",
                currency = "BRL",
                limitType = LimitType.FIXED,
                amount = 500.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.EMPTY_TITLE, error.error)
        assertTrue(repo.inserted.isEmpty())
    }

    @Test
    fun `a title already taken is refused and nothing is written`() = runTest {
        val repo = RecordingBudgetRepository(existing = listOf(testBudget(title = "Groceries")))

        val error = assertIs<BudgetException>(
            useCase(repo)(
                title = "groceries",
                categories = listOf(testCategory()),
                iconKey = "shopping",
                currency = "BRL",
                limitType = LimitType.FIXED,
                amount = 500.0,
            ).leftOrNull()
        )

        assertEquals(BudgetError.ALREADY_EXIST, error.error)
        assertTrue(repo.inserted.isEmpty())
    }
}
