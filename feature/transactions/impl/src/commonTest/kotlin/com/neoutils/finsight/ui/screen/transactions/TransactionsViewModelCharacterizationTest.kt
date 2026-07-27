@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Characterizes the balance overview [TransactionsViewModel] showed before the scope
 * axis existed — income/expense/adjustment, the month-wide card payment, and the
 * opening/final balances. That summary is now the *accounts* scope, and it must survive
 * value for value: this is the change's safety net, not one of its casualties.
 */
class TransactionsViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val month = Clock.System.now().toYearMonth()
    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val cardAcc = Account(id = 200, name = "Card", type = AccountType.LIABILITY)
    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)
    private val equityAcc = Account(id = 102, name = "reconciliation", type = AccountType.EQUITY)

    private fun date(day: Int) = LocalDate(month.year, month.month, day)

    private fun entry(acc: Account, amount: Double) = Entry(account = acc, amount = (amount * 100).toLong())

    private fun op(id: Long, day: Int, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date(day), entries = entries)

    @Test
    fun `the accounts scope still characterizes stats payment and balances`() = runTest(dispatcher) {
        val transactions = listOf(
            op(1, day = 5, listOf(entry(account, 100.0), entry(incomeAcc, -100.0))),
            op(2, day = 10, listOf(entry(account, -30.0), entry(expenseAcc, 30.0))),
            op(3, day = 15, listOf(entry(account, 40.0), entry(equityAcc, -40.0))),
            // ASSET out + LIABILITY in → label PAYMENT: excluded from stats, counted in payment.
            op(4, day = 20, listOf(entry(account, -80.0), entry(cardAcc, 80.0))),
        )

        // Ledger opening/final balance: 0 up to the previous month, 30 up to the month
        // (Σ the account's signed legs 100 − 30 + 40 − 80). Month-wide card payment = 80.
        // Month-wide asset flows: income 100, expense 30, adjustment 40.
        val vm = TransactionsViewModel(
            filterLabel = null, category = null, filterTarget = null,
            transactionRepository = FakeTransactionRepository(transactions),
            categoryRepository = FakeCategoryRepository(),
            installmentRepository = NoInstallments,
        entryRepository = FakeLedger(transactions),
        )

        vm.uiState.test {
            // Skip the Loading initialValue of stateIn; assert on the computed state.
            var state = awaitItem()
            while (state.listState is TransactionsUiState.ListState.Loading) state = awaitItem()

            vm.onAction(TransactionsAction.SelectScope(TransactionScope.ACCOUNTS))
            while (state.selectedScope != TransactionScope.ACCOUNTS) state = awaitItem()

            val overview = state.balanceOverview as TransactionsUiState.BalanceOverview.Accounts
            // Each figure carries the sign it is displayed with: spending and the payment
            // leave the account perimeter, so they arrive negative.
            assertEquals(100.0, overview.income.value)
            assertEquals(-30.0, overview.expense.value)
            assertEquals(40.0, overview.adjustment?.value)
            assertEquals(-80.0, overview.invoicePayment?.value, "month-wide card payment from the ledger")
            assertEquals(0.0, overview.openingBalance.value)
            assertEquals(30.0, overview.finalBalance.value, "Σ signed account legs up to the month")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

internal class FakeTransactionRepository(transactions: List<Transaction>) : ITransactionRepository {
    private val flow = MutableStateFlow(transactions)
    override fun observeAllTransactions(): Flow<List<Transaction>> = flow
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}

internal class FakeCategoryRepository : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun getCategoryBySystemKey(systemKey: String): Category? = null
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? = null
    override suspend fun archive(id: Long) = Unit
    override suspend fun unarchive(id: Long) = Unit
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = false

    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

/** No installment badge is under test here; the list only needs the read to answer. */
internal object NoInstallments : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(emptyList())
    override suspend fun getAllInstallments(): List<Installment> = emptyList()
    override suspend fun getInstallmentById(id: Long): Installment? = null
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = throw NotImplementedError()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = throw NotImplementedError()
    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}
