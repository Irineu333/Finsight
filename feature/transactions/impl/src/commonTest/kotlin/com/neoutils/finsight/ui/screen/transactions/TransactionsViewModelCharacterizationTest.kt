@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
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
 * Characterizes the balance overview of [TransactionsViewModel] (sites :55,56,70,72):
 * income/expense/adjustment via the stats use case, the PAYMENT-label month sum, and
 * the opening/final balances. Task 4.11 flips these to the ledger; the numbers must
 * survive.
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
    fun `balance overview characterizes stats payment and balances`() = runTest(dispatcher) {
        val transactions = listOf(
            op(1, day = 5, listOf(entry(account, 100.0), entry(incomeAcc, -100.0))),
            op(2, day = 10, listOf(entry(account, -30.0), entry(expenseAcc, 30.0))),
            op(3, day = 15, listOf(entry(account, 40.0), entry(equityAcc, -40.0))),
            // ASSET out + LIABILITY in → label PAYMENT: excluded from stats, counted in payment.
            op(4, day = 20, listOf(entry(account, -80.0), entry(cardAcc, 80.0))),
        )

        val vm = TransactionsViewModel(
            filterType = null, category = null, filterTarget = null,
            transactionRepository = FakeTransactionRepository(transactions),
            categoryRepository = FakeCategoryRepository(),
            // Ledger opening/final balance: 0 up to the previous month, 30 up to the month
            // (Σ the account's signed legs 100 − 30 + 40 − 80).
            calculateBalanceUseCase = CalculateBalanceUseCase(LedgerBalance(month = month, balance = 30.0)),
            calculateTransactionStatsUseCase = CalculateTransactionStatsUseCase(),
        )

        vm.uiState.test {
            // Skip the empty initialValue of stateIn; assert on the computed state.
            var overview = awaitItem().balanceOverview
            while (overview.income == 0.0) overview = awaitItem().balanceOverview
            assertEquals(100.0, overview.income)
            assertEquals(30.0, overview.expense)
            assertEquals(40.0, overview.adjustment)
            assertEquals(80.0, overview.payment, "Σ amount of PAYMENT-kind transactions in the month")
            assertEquals(0.0, overview.openingBalance)
            assertEquals(30.0, overview.finalBalance, "Σ signed account legs up to the month")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeTransactionRepository(transactions: List<Transaction>) : ITransactionRepository {
    private val flow = MutableStateFlow(transactions)
    override fun observeAllTransactions(): Flow<List<Transaction>> = flow
    override fun observeTransactionsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}

private class FakeCategoryRepository : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = getAllCategories()
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = observeAllCategories()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class LedgerBalance(private val month: YearMonth, private val balance: Double) : IEntryRepository {
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = if (target == month) balance else 0.0
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}

private object ThrowingEntryRepository : IEntryRepository {
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}
