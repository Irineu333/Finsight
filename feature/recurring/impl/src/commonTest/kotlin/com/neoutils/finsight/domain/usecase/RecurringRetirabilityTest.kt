package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeBudgetRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.domain.error.RecurringRetireError
import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.recurring
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecurringRetirabilityTest {

    private fun resolver(
        hasTransaction: Boolean = false,
        hasBudget: Boolean = false,
    ) = ResolveRecurringRetirabilityUseCaseImpl(
        recurringRepository = FakeRecurringRepository(hasTransaction = hasTransaction),
        budgetRepository = FakeBudgetRepository(hasBudget = hasBudget),
    )

    @Test
    fun `a recurring that generated entries must be archived`() = runTest {
        val result = resolver(hasTransaction = true)(recurring())
        assertEquals(
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_TRANSACTIONS),
            result,
        )
    }

    @Test
    fun `a recurring a budget points at must be archived`() = runTest {
        val result = resolver(hasBudget = true)(recurring())
        assertEquals(
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_BUDGET),
            result,
        )
    }

    @Test
    fun `a recurring with no dependent is deletable`() = runTest {
        assertEquals(RecurringRetirability.Deletable, resolver()(recurring()))
    }

    @Test
    fun `a skipped cycle is not a dependent`() = runTest {
        // A skip writes no transaction, produces no entry and moves no money — the
        // guard consults neither occurrences nor anything derived from them, so a
        // recurring whose only history is a skip stays deletable (design D2).
        assertEquals(RecurringRetirability.Deletable, resolver()(recurring()))
    }

    @Test
    fun `delete is refused with the typed error when the recurring is in use`() = runTest {
        val target = recurring()
        val repository = FakeRecurringRepository(stored = listOf(target), hasTransaction = true)
        val useCase = DeleteRecurringUseCaseImpl(
            repository = repository,
            resolveRetirability = ResolveRecurringRetirabilityUseCaseImpl(
                recurringRepository = repository,
                budgetRepository = FakeBudgetRepository(),
            ),
        )

        val error = useCase(target).leftOrNull()

        assertEquals(
            RecurringRetireError.HAS_TRANSACTIONS,
            assertIs<RecurringRetireException>(error).error,
        )
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `delete goes through when the recurring is deletable`() = runTest {
        val target = recurring()
        val repository = FakeRecurringRepository(stored = listOf(target))
        val useCase = DeleteRecurringUseCaseImpl(
            repository = repository,
            resolveRetirability = ResolveRecurringRetirabilityUseCaseImpl(
                recurringRepository = repository,
                budgetRepository = FakeBudgetRepository(),
            ),
        )

        assertTrue(useCase(target).isRight())
        assertEquals(listOf(target), repository.deleted)
    }
}
