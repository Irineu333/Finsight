@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.report.viewer

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.ui.screen.report.ReportViewerParams
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/**
 * Characterizes the account-perspective stats of [ReportViewerViewModel] (sites
 * :84,87,90): it forwards [CalculateReportStatsUseCase] (itself characterized by
 * CalculateReportStatsUseCaseTest). Task 4.11 keeps the numbers; this pins the
 * ViewModel wiring.
 */
class ReportViewerViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val account = Account(id = 1, name = "A", type = AccountType.ASSET)

    private fun accountLeg(type: Transaction.Type, amount: Double, month: Int, day: Int) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, month, day), account = account,
    )

    private fun op(id: Long, leg: Transaction) = Operation(id = id, title = null, date = leg.date, transactions = listOf(leg))

    @Test
    fun `account perspective forwards the report stats`() = runTest(dispatcher) {
        val operations = listOf(
            op(1, accountLeg(Transaction.Type.INCOME, 100.0, month = 3, day = 5)),
            op(2, accountLeg(Transaction.Type.EXPENSE, 30.0, month = 3, day = 10)),
            op(3, accountLeg(Transaction.Type.EXPENSE, 20.0, month = 2, day = 10)), // prior → opening
        )
        val fakes = Fakes()
        val vm = ReportViewerViewModel(
            params = ReportViewerParams(
                perspectiveType = PerspectiveTab.ACCOUNT,
                accountIds = listOf(1),
                startDate = LocalDate(2026, 3, 1),
                endDate = LocalDate(2026, 3, 31),
                includeSpendingByCategory = false,
                includeIncomeByCategory = false,
                includeTransactionList = false,
            ),
            operationRepository = fakes.operationRepository(operations),
            accountRepository = fakes.accountRepository(listOf(account)),
            creditCardRepository = fakes.creditCardRepository,
            invoiceRepository = fakes.invoiceRepository,
            calculateReportStatsUseCase = CalculateReportStatsUseCase(),
            calculateReportCategorySpendingUseCase = CalculateReportCategorySpendingUseCase(
                entryRepository = fakes.entryRepository,
                categoryRepository = fakes.categoryRepository,
                accountRepository = fakes.accountRepository(listOf(account)),
                creditCardRepository = fakes.creditCardRepository,
            ),
            entryRepository = fakes.entryRepository,
            renderer = fakes.renderer,
            analytics = fakes.analytics,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state !is ReportViewerUiState.Content) state = awaitItem()
            val stats = state.stats as ReportViewerUiState.Stats.Account
            assertEquals(100.0, stats.income)
            assertEquals(30.0, stats.expense)
            assertEquals(70.0, stats.balance)
            assertEquals(-20.0, stats.initialBalance)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class Fakes {
    fun operationRepository(operations: List<Operation>) = object : IOperationRepository {
        override fun observeAllOperations(): Flow<List<Operation>> = MutableStateFlow(operations)
        override fun observeOperationsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Operation>> = throw NotImplementedError()
        override fun observeOperationById(id: Long): Flow<Operation?> = throw NotImplementedError()
        override suspend fun getAllOperations(): List<Operation> = throw NotImplementedError()
        override suspend fun getOperationById(id: Long): Operation? = throw NotImplementedError()
        override suspend fun createOperation(title: String?, date: LocalDate, categoryId: Long?, recurringId: Long?, recurringCycle: Int?, installmentId: Long?, installmentNumber: Int?, transactions: List<Transaction>): Operation = throw NotImplementedError()
        override suspend fun updateOperation(id: Long, transaction: Transaction) = throw NotImplementedError()
        override suspend fun deleteOperationById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
    }

    fun accountRepository(accounts: List<Account>) = object : IAccountRepository {
        override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(accounts)
        override suspend fun getAllAccounts(): List<Account> = accounts
        override suspend fun getAccountById(accountId: Long): Account? = accounts.firstOrNull { it.id == accountId }
        override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
        override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
        override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
        override suspend fun getAccountCount(): Int = throw NotImplementedError()
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
    }

    val creditCardRepository = object : ICreditCardRepository {
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = MutableStateFlow(emptyList())
        override suspend fun getAllCreditCards(): List<CreditCard> = emptyList()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = null
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    val invoiceRepository = object : IInvoiceRepository {
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
        override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
    }

    val categoryRepository = object : ICategoryRepository {
        override fun observeAllCategories(): Flow<List<Category>> = throw NotImplementedError()
        override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
        override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
        override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
        override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
        override suspend fun insert(category: Category) = throw NotImplementedError()
        override suspend fun update(category: Category) = throw NotImplementedError()
        override suspend fun delete(category: Category) = throw NotImplementedError()
    }

    val entryRepository = object : IEntryRepository {
        override suspend fun getEntriesByOperation(operationId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
        override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
        override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    }

    val renderer = object : ReportDocumentRenderer {
        override fun render(layout: ReportLayout): ReportDocument = throw NotImplementedError()
    }

    val analytics = object : Analytics {
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) = Unit
        override fun setUserId(id: String?) = Unit
    }
}
