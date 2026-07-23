@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.archived

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArchivedCreditCardsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeCreditCardRepository : ICreditCardRepository {
        private val all = MutableSharedFlow<List<CreditCard>>(replay = 1)
        fun emit(cards: List<CreditCard>) { all.tryEmit(cards) }
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = all
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    }

    private fun card(id: Long, isArchived: Boolean) = CreditCard(
        id = id,
        name = "Card $id",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = id * 10,
        isArchived = isArchived,
    )

    @Test
    fun `lists only archived cards`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val vm = ArchivedCreditCardsViewModel(repository)

        vm.uiState.test {
            assertEquals(ArchivedCreditCardsUiState.Loading, awaitItem())
            repository.emit(
                listOf(
                    card(id = 1L, isArchived = false),
                    card(id = 2L, isArchived = true),
                    card(id = 3L, isArchived = true),
                )
            )
            val content = assertIs<ArchivedCreditCardsUiState.Content>(awaitItem())
            assertEquals(listOf(2L, 3L), content.creditCards.map { it.cardId })
        }
    }

    @Test
    fun `empty when there are no archived cards`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val vm = ArchivedCreditCardsViewModel(repository)

        vm.uiState.test {
            assertEquals(ArchivedCreditCardsUiState.Loading, awaitItem())
            repository.emit(listOf(card(id = 1L, isArchived = false)))
            assertEquals(ArchivedCreditCardsUiState.Empty, awaitItem())
        }
    }
}
