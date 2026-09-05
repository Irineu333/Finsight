package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.exception.BudgetException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The removal the delete modal used to perform itself, and the identity rule the whole
 * change is written around: the budget is resolved when the operation runs, and the
 * form that receives the aggregate is a convenience over the same implementation.
 */
class DeleteBudgetUseCaseTest {

    private val budget = testBudget(id = 1L)

    private fun repository() = RecordingBudgetRepository(existing = listOf(budget))

    private fun useCase(repo: RecordingBudgetRepository) = DeleteBudgetUseCaseImpl(
        budgetRepository = repo,
    )

    @Test
    fun `removes the resolved budget`() = runTest {
        val repo = repository()

        val result = useCase(repo)(budget.id)

        assertTrue(result.isRight())
        assertEquals(listOf(budget.id), repo.deleted)
    }

    @Test
    fun `an identity that matches no budget is refused and removes nothing`() = runTest {
        val repo = RecordingBudgetRepository()

        val error = assertIs<BudgetException>(useCase(repo)(404L).leftOrNull())

        assertEquals(BudgetError.NOT_FOUND, error.error)
        assertTrue(repo.deleted.isEmpty(), "a budget that does not exist cannot be removed")
    }

    @Test
    fun `the aggregate form and the id form remove the same budget`() = runTest {
        val byAggregate = repository()
        val byId = repository()

        assertTrue(useCase(byAggregate)(budget).isRight())
        assertTrue(useCase(byId)(budget.id).isRight())

        assertEquals(byAggregate.deleted, byId.deleted)
    }

    @Test
    fun `the aggregate form refuses exactly as the id form does`() = runTest {
        // The aggregate is stale — nothing answers to its identity any more — and the
        // refusal is the same one, because there is only one implementation.
        val empty = RecordingBudgetRepository()

        val byAggregate = assertIs<BudgetException>(useCase(empty)(budget).leftOrNull())
        val byId = assertIs<BudgetException>(useCase(empty)(budget.id).leftOrNull())

        assertEquals(byAggregate.error, byId.error)
        assertTrue(empty.deleted.isEmpty())
    }
}
