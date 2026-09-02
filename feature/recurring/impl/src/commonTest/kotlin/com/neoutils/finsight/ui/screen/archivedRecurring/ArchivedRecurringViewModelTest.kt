@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.archivedRecurring

import app.cash.turbine.test
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
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

/**
 * **Where an archived recurring goes, and that it is still there.**
 *
 * It generates no cycle in any month, so it belongs to no section of the monthly list —
 * and archiving is offered to the user as reversible, so it must not have left the app
 * with it. This destination is the whole of that promise.
 */
class ArchivedRecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val wallet = Account(id = 1L, name = "Wallet", type = AccountType.ASSET, currency = "BRL")

    private val active = recurring(id = 1L, createdAt = 1L).copy(account = wallet)
    private val archived = recurring(id = 2L, createdAt = 2L, isArchived = true).copy(account = wallet)

    private fun viewModel(
        repository: FakeRecurringRepository,
        accounts: FakeAccountRepository = FakeAccountRepository(listOf(wallet)),
    ) = ArchivedRecurringViewModel(
        recurringRepository = repository,
        accountRepository = accounts,
    )

    @Test
    fun `it lists the archived ones, and only those`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(active, archived)

        viewModel(repository).uiState.test {
            assertIs<ArchivedRecurringUiState.Loading>(awaitItem())
            val content = assertIs<ArchivedRecurringUiState.Content>(awaitItem())
            assertEquals(listOf(archived.id), content.recurring.map { it.recurring.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Nothing archived is its own state — an empty list would say the same with a hole. */
    @Test
    fun `nothing archived is the empty state`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(active)

        viewModel(repository).uiState.test {
            assertIs<ArchivedRecurringUiState.Loading>(awaitItem())
            assertIs<ArchivedRecurringUiState.Empty>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Un-archiving is what this destination exists for: the row leaves it at once. */
    @Test
    fun `un-archiving takes it out of the archive`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(archived)

        viewModel(repository).uiState.test {
            assertIs<ArchivedRecurringUiState.Loading>(awaitItem())
            assertIs<ArchivedRecurringUiState.Content>(awaitItem())

            repository.all.value = listOf(archived.copy(isArchived = false))

            assertIs<ArchivedRecurringUiState.Empty>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The row still shows a figure, and a card template is denominated by the `LIABILITY`
     * account the card projects onto — which the account facade does not list.
     */
    @Test
    fun `a card template keeps its denomination in the archive`() = runTest(dispatcher) {
        val cardAccount = Account(id = 90L, name = "Card", type = AccountType.LIABILITY, currency = "USD")
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(
            recurring(id = 3L, isArchived = true).copy(
                creditCard = CreditCard(
                    id = 7L,
                    name = "Card",
                    limit = 1_000.0,
                    closingDay = 1,
                    dueDay = 10,
                    accountId = cardAccount.id,
                ),
            ),
        )

        viewModel(repository, FakeAccountRepository(listOf(cardAccount))).uiState.test {
            assertIs<ArchivedRecurringUiState.Loading>(awaitItem())
            val content = assertIs<ArchivedRecurringUiState.Content>(awaitItem())
            assertEquals("USD", content.recurring.single().amount?.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
