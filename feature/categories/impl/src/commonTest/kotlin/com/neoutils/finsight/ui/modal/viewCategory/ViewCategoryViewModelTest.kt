@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewCategoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        val recorded = mutableListOf<Throwable>()
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) { recorded += e }
    }

    private class FakeCategoryRepository : ICategoryRepository {
        private val byId = MutableSharedFlow<Category?>(replay = 1)
        fun emit(category: Category?) { byId.tryEmit(category) }
        override fun observeCategoryById(id: Long): Flow<Category?> = byId
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    private class FakeTransactionRepository(
        private val transactions: List<Transaction> = emptyList(),
    ) : ITransactionRepository {
        override suspend fun getAllTransactions(): List<Transaction> = transactions
        override suspend fun insert(transaction: Transaction): Long = throw NotImplementedError()
        override suspend fun update(transaction: Transaction) = throw NotImplementedError()
        override suspend fun delete(transaction: Transaction) = throw NotImplementedError()
        override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getTransactionBy(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun getTransactionsBy(
            type: Transaction.Type?,
            target: Transaction.Target?,
            date: LocalDate?,
            invoiceId: Long?,
            accountId: Long?,
        ): List<Transaction> = throw NotImplementedError()
        override fun observeTransactionsBy(
            type: Transaction.Type?,
            target: Transaction.Target?,
            date: LocalDate?,
            invoiceId: Long?,
            creditCardId: Long?,
            accountId: Long?,
        ): Flow<List<Transaction>> = throw NotImplementedError()
    }

    private fun category(id: Long = 1L, name: String = "Food") = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
    )

    private fun viewModel(
        categoryRepository: FakeCategoryRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
        transactions: List<Transaction> = emptyList(),
    ) = ViewCategoryViewModel(
        categoryId = 1L,
        categoryRepository = categoryRepository,
        transactionRepository = FakeTransactionRepository(transactions),
        crashlytics = crashlytics,
    )

    // The ViewModel starts on the current month (Clock.System.now()), so the dataset
    // is dated relative to it — as the ViewModel itself is.
    private val currentMonth = Clock.System.now().toYearMonth()

    private fun categoryLeg(
        amount: Double,
        day: Int,
        categoryId: Long = 1L,
        month: YearMonth = currentMonth,
    ) = Transaction(
        type = Transaction.Type.EXPENSE,
        amount = amount,
        title = null,
        date = LocalDate(month.year, month.month, day),
        category = category(id = categoryId),
    )

    // Characterizes the current totalAmount (Σ amount) and transactionCount (leg count)
    // for a category in the selected month. Task 4.1 flips this to the ledger
    // (Σ entries of the category account + entryCountInMonth, task 2.5); these numbers
    // must survive.
    @Test
    fun `content characterizes total amount and transaction count for the month`() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(
            categoryRepository = repository,
            transactions = listOf(
                categoryLeg(amount = 30.0, day = 10),
                categoryLeg(amount = 12.5, day = 15),
                categoryLeg(amount = 99.0, day = 3, categoryId = 2L),                 // other category → excluded
                categoryLeg(amount = 40.0, day = 20, month = currentMonth.plusMonth()), // other month → excluded
            ),
        )

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food"))
            val content = assertIs<ViewCategoryUiState.Content>(awaitItem())
            assertEquals(42.5, content.totalAmount)
            assertEquals(2, content.transactionCount)
        }
    }

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(category(id = 1L, name = "Food"))
            assertEquals("Food", assertIs<ViewCategoryUiState.Content>(awaitItem()).category.name)
        }
    }

    @Test
    fun firstEmissionNullShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewCategoryUiState.Loading, awaitItem())
            repository.emit(null)
            assertEquals(ViewCategoryUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewCategoryUiState.Loading, state.awaitItem())
            repository.emit(category(id = 1L))
            assertIs<ViewCategoryUiState.Content>(state.awaitItem())

            repository.emit(null)
            assertIs<ViewCategoryEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }
}
