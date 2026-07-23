@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCreditCard

import app.cash.turbine.test
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.repository.ScopeStats
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewCreditCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private class FakeCreditCardRepository : ICreditCardRepository {
        private val byId = MutableSharedFlow<CreditCard?>(replay = 1)
        val unarchived = mutableListOf<Long>()
        fun emit(card: CreditCard?) { byId.tryEmit(card) }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = byId
        override suspend fun unarchive(accountId: Long) { unarchived += accountId }
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    private class FakeEntryRepository(var balances: Map<Long, Double> = emptyMap()) : IEntryRepository {
        val ledger = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        override suspend fun balance(accountId: Long): Double = balances[accountId] ?: 0.0
        override fun observeLedgerChanges(): Flow<Unit> = ledger
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun hasEntries(accountId: Long): Boolean = throw NotImplementedError()
        override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = throw NotImplementedError()
        override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
        override suspend fun dimensionOwed(dimensionId: Long): Double = throw NotImplementedError()
        override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = throw NotImplementedError()
        override suspend fun liabilityMonthFlows(month: YearMonth): LiabilityMonthFlows = throw NotImplementedError()
        override suspend fun assetMonthFlows(month: YearMonth): AssetMonthFlows = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
        override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
        override suspend fun scopeStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): ScopeStats = throw NotImplementedError()
    }

    private fun card(id: Long = 1L, accountId: Long = 10L, isArchived: Boolean = true) = CreditCard(
        id = id,
        name = "Card",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = accountId,
        isArchived = isArchived,
    )

    private fun viewModel(
        creditCardRepository: FakeCreditCardRepository,
        entryRepository: FakeEntryRepository = FakeEntryRepository(),
    ) = ViewCreditCardViewModel(
        cardId = 1L,
        creditCardRepository = creditCardRepository,
        entryRepository = entryRepository,
        unarchiveCreditCard = UnarchiveCreditCardUseCase(creditCardRepository),
        crashlytics = FakeCrashlytics(),
    )

    @Test
    fun `an archived card is shown archived, with its balance read from the ledger`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val vm = viewModel(repository, FakeEntryRepository(balances = mapOf(10L to 0.0)))

        vm.uiState.test {
            assertEquals(ViewCreditCardUiState.Loading, awaitItem())
            repository.emit(card(accountId = 10L, isArchived = true))
            val content = assertIs<ViewCreditCardUiState.Content>(awaitItem())
            assertTrue(content.creditCard.isArchived)
            assertEquals(0.0, content.balance)
        }
    }

    @Test
    fun `the unarchive action unarchives the shown card by its accountId`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCreditCardUiState.Loading, awaitItem())
            repository.emit(card(accountId = 77L, isArchived = true))
            assertIs<ViewCreditCardUiState.Content>(awaitItem())

            vm.onAction(ViewCreditCardAction.Unarchive)
            runCurrent()

            assertEquals(listOf(77L), repository.unarchived)
        }
    }
}
