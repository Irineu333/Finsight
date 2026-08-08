@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.recurring

import app.cash.turbine.test
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.recurring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val expense = recurring(id = 1L, createdAt = 1L)
    private val income = recurring(id = 2L, type = TransactionType.INCOME, createdAt = 2L)
    private val archived = recurring(id = 3L, createdAt = 3L, isArchived = true)
    private val archivedIncome =
        recurring(id = 4L, type = TransactionType.INCOME, createdAt = 4L, isArchived = true)

    private suspend fun idsListedBy(
        filter: RecurringFilter,
        recurrings: List<Recurring>,
    ): List<Long> {
        val repository = FakeRecurringRepository()
        repository.all.value = recurrings
        val vm = RecurringViewModel(repository, FakeAccountRepository())

        var ids: List<Long> = emptyList()
        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            vm.onAction(RecurringAction.SelectFilter(filter))
            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.filter != filter) {
                state = awaitItem()
            }
            ids = state.filteredRecurring.map { it.recurring.id }
            cancelAndIgnoreRemainingEvents()
        }
        return ids
    }

    @Test
    fun `active does not include archived`() = runTest(dispatcher) {
        assertEquals(
            listOf(expense.id, income.id),
            idsListedBy(RecurringFilter.ACTIVE, listOf(expense, income, archived)),
        )
    }

    @Test
    fun `archived lists only archived - of both types`() = runTest(dispatcher) {
        assertEquals(
            listOf(archived.id, archivedIncome.id),
            idsListedBy(RecurringFilter.ARCHIVED, listOf(expense, archived, archivedIncome)),
        )
    }

    @Test
    fun `a typed filter excludes the archived of that type`() = runTest(dispatcher) {
        assertEquals(
            listOf(expense.id),
            idsListedBy(RecurringFilter.EXPENSE, listOf(expense, income, archived)),
        )
    }

    @Test
    fun `empty means no recurring at all - not an empty filter`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(archived)
        val vm = RecurringViewModel(repository, FakeAccountRepository())

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            // Only an archived one exists and the default filter is ACTIVE: the list is
            // empty, but the database is not — so this is Content, not Empty.
            val content = assertIs<RecurringUiState.Content>(awaitItem())
            assertEquals(emptyList(), content.filteredRecurring)

            repository.all.value = emptyList()
            assertIs<RecurringUiState.Empty>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
