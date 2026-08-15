@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.CreateTransactionUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.mcp.contract.MoneyPayloadFactory
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The clock these tests read, stated rather than taken from the machine.
 *
 * A surface that echoes the date it assumed cannot be asserted against a clock that moves
 * while the suite runs.
 */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** Midday of [date] in UTC — a clock reading exactly that civil date in [TEST_ZONE]. */
fun clockAt(date: LocalDate): Clock = FixedClock(Instant.parse("${date}T12:00:00Z"))

/** The zone the fixtures state their civil dates in. */
val TEST_ZONE: TimeZone = TimeZone.UTC

// ---------------------------------------------------------------------------
// Domain fixtures
// ---------------------------------------------------------------------------

fun account(
    id: Long,
    name: String,
    currency: String = "BRL",
    isArchived: Boolean = false,
    isDefault: Boolean = false,
    type: AccountType = AccountType.ASSET,
) = Account(
    id = id,
    name = name,
    type = type,
    currency = currency,
    isDefault = isDefault,
    createdAt = id,
    isArchived = isArchived,
)

fun category(
    id: Long,
    name: String,
    type: Category.Type = Category.Type.EXPENSE,
    dimensionId: Long = id + 100,
    isArchived: Boolean = false,
) = Category(
    id = id,
    name = name,
    icon = CategoryLazyIcon("cart"),
    type = type,
    createdAt = id,
    isArchived = isArchived,
    dimensionId = dimensionId,
)

fun creditCard(
    id: Long,
    name: String,
    accountId: Long,
    currency: String? = "BRL",
    closingDay: Int = 10,
    dueDay: Int = 20,
    limit: Double = 5_000.0,
) = CreditCard(
    id = id,
    name = name,
    limit = limit,
    closingDay = closingDay,
    dueDay = dueDay,
    accountId = accountId,
    currency = currency,
)

fun invoice(
    id: Long,
    card: CreditCard,
    dueMonth: YearMonth,
    status: Invoice.Status = Invoice.Status.OPEN,
    dimensionId: Long = id + 500,
) = Invoice(
    id = id,
    creditCard = card,
    dimensionId = dimensionId,
    // The bill's own three months, as the domain relates them for a card whose due day
    // is not before its closing day: it closes in the month it falls due in, and it
    // opened the month before.
    openingMonth = dueMonth.minusMonth(),
    closingMonth = dueMonth,
    dueMonth = dueMonth,
    status = status,
)

fun entry(account: Account, cents: Long, dimensionId: Long? = null) =
    Entry(account = account, amount = cents, dimensionId = dimensionId)

fun transaction(
    id: Long,
    date: LocalDate,
    entries: List<Entry>,
    title: String? = null,
    installmentId: Long? = null,
    installmentNumber: Int? = null,
) = Transaction(
    id = id,
    title = title,
    date = date,
    installmentId = installmentId,
    installmentNumber = installmentNumber,
    entries = entries,
)

/** The two nominal accounts every expense and income of the ledger lands on. */
val expensesAccount = account(900, "Despesas", type = AccountType.EXPENSE)
val incomesAccount = account(901, "Receitas", type = AccountType.INCOME)
val conversionAccount = account(902, "Conversão", type = AccountType.CONVERSION)

// ---------------------------------------------------------------------------
// Repository fakes
// ---------------------------------------------------------------------------

class FakeAccountRepository(
    private val userAccounts: List<Account> = emptyList(),
    /** The whole chart, system rows included — what the ledger really holds. */
    private val chart: List<Account> = userAccounts,
) : IAccountRepository {

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(userAccounts.filter { !it.isArchived })
    override suspend fun getAllAccounts(): List<Account> = userAccounts.filter { !it.isArchived }
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = userAccounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(userAccounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = chart
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(chart)
    override suspend fun getAccountById(accountId: Long): Account? = chart.find { it.id == accountId }
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(getAccountByIdBlocking(accountId))
    override suspend fun getDefaultAccount(): Account? = userAccounts.find { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(userAccounts.find { it.isDefault })
    override suspend fun getAccountCount(): Int = userAccounts.size
    override suspend fun hasYieldingAccount(): Boolean = userAccounts.any { it.yieldsInterest }
    override suspend fun insert(account: Account): Long = unsupported()
    override suspend fun update(account: Account) = unsupported()
    override suspend fun delete(account: Account) = unsupported()
    override suspend fun reopen(accountId: Long) = unsupported()

    private fun getAccountByIdBlocking(id: Long) = chart.find { it.id == id }
}

class FakeCategoryRepository(private val categories: List<Category> = emptyList()) : ICategoryRepository {

    override fun observeAllCategories(): Flow<List<Category>> = flowOf(categories.filter { !it.isArchived })
    override suspend fun getAllCategories(): List<Category> = categories.filter { !it.isArchived }
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = categories
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(categories)
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> =
        flowOf(categories.filter { it.type == type })

    override suspend fun getCategoryById(id: Long): Category? = categories.find { it.id == id }
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        categories.find { it.dimensionId == dimensionId }

    override suspend fun getCategoryBySystemKey(systemKey: String): Category? =
        categories.find { it.systemKey == systemKey }

    override fun observeCategoryById(id: Long): Flow<Category?> = flowOf(categories.find { it.id == id })
    override suspend fun archive(id: Long) = unsupported()
    override suspend fun unarchive(id: Long) = unsupported()
    override suspend fun existsByName(name: String, ignoreId: Long): Boolean = unsupported()
    override suspend fun insert(category: Category) = unsupported()
    override suspend fun insertAll(categories: List<Category>) = unsupported()
    override suspend fun update(category: Category) = unsupported()
    override suspend fun delete(category: Category) = unsupported()
}

class FakeCreditCardRepository(private val cards: List<CreditCard> = emptyList()) : ICreditCardRepository {

    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(cards.filter { !it.isArchived })
    override suspend fun getAllCreditCards(): List<CreditCard> = cards.filter { !it.isArchived }
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = flowOf(cards)
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = cards.find { it.id == creditCardId }
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> =
        flowOf(cards.find { it.id == creditCardId })

    override suspend fun insert(creditCard: CreditCard, currency: String): Long = unsupported()
    override suspend fun update(creditCard: CreditCard) = unsupported()
    override suspend fun delete(creditCard: CreditCard) = unsupported()
    override suspend fun unarchive(accountId: Long) = unsupported()
    override suspend fun currencyForNewCard(): String = "BRL"
}

class FakeInvoiceRepository(private val invoices: List<Invoice> = emptyList()) : IInvoiceRepository {

    override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(invoices)
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> =
        flowOf(invoices.filter { it.creditCard.id == creditCardId })

    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = flowOf(invoices.find { it.id == invoiceId })
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = flowOf(openOf(creditCardId))
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> =
        flowOf(invoices.filter { it.creditCard.id == creditCardId })

    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = flowOf(openOf(creditCardId))
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = flowOf(invoices.filter { !it.status.isPaid })
    override suspend fun getAllInvoices(): List<Invoice> = invoices
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        invoices.filter { it.creditCard.id == creditCardId }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        invoices.filter { it.creditCard.id == creditCardId && !it.status.isPaid }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = openOf(creditCardId)
    override suspend fun getOpenInvoices(): List<Invoice> = invoices.filter { it.status.isOpen }
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.find { it.id == id }
    override suspend fun insert(invoice: Invoice): Invoice = unsupported()
    override suspend fun update(invoice: Invoice) = unsupported()
    override suspend fun deleteById(id: Long) = unsupported()

    private fun openOf(creditCardId: Long) =
        invoices.find { it.creditCard.id == creditCardId && it.status.isOpen }
}

class FakeTransactionRepository(private val transactions: List<Transaction> = emptyList()) : ITransactionRepository {

    override fun observeAllTransactions(): Flow<List<Transaction>> = flowOf(transactions)

    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?) =
        flowOf(transactions.filter { matches(it, date, date, dimensionId, accountId) })

    override suspend fun getTransactionsBy(
        startDate: LocalDate?,
        endDate: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): List<Transaction> = transactions
        .filter { matches(it, startDate, endDate, dimensionId, accountId) }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })

    override fun observeTransactionById(id: Long): Flow<Transaction?> = flowOf(transactions.find { it.id == id })
    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionById(id: Long): Transaction? = transactions.find { it.id == id }
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = unsupported()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = unsupported()
    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) = unsupported()

    override suspend fun deleteTransactionById(id: Long) = unsupported()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = unsupported()

    private fun matches(
        transaction: Transaction,
        start: LocalDate?,
        end: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ) = (start == null || transaction.date >= start) &&
        (end == null || transaction.date <= end) &&
        (dimensionId == null || transaction.entries.any { it.dimensionId == dimensionId }) &&
        (accountId == null || transaction.entries.any { it.account.id == accountId })
}

/**
 * The ledger reads, answered from a table the test states.
 *
 * Only the members the tools of this module actually call are answered; the rest refuse
 * loudly rather than returning a zero that would look like a real figure.
 */
class FakeEntryRepository(
    private val accountBalances: Map<Long, Double> = emptyMap(),
    private val assetBalance: MoneyByCurrency = MoneyByCurrency.zero,
    private val liabilityBalance: MoneyByCurrency = MoneyByCurrency.zero,
    private val dimensionOwed: Map<Long, MoneyByCurrency> = emptyMap(),
    private val dimensionTotals: Map<Long?, MoneyByCurrency> = emptyMap(),
    private val scopeStats: ScopeStatsByCurrency = ScopeStatsByCurrency.zero,
) : IEntryRepository {

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = unsupported()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = unsupported()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)

    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double =
        accountBalances[accountId] ?: 0.0

    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>) = assetBalance

    override suspend fun naturalBalanceUpToByCurrency(
        target: YearMonth,
        type: AccountType,
        excludedAccountIds: Set<Long>,
    ) = when (type) {
        AccountType.LIABILITY -> liabilityBalance
        AccountType.ASSET -> assetBalance
        else -> MoneyByCurrency.zero
    }

    override suspend fun hasEntries(accountId: Long): Boolean = unsupported()
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = unsupported()
    override suspend fun balance(accountId: Long): Double = accountBalances[accountId] ?: 0.0

    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
        dimensionOwed[dimensionId] ?: MoneyByCurrency.zero

    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows =
        unsupported()

    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = unsupported()

    override suspend fun dimensionOwedByCurrency(dimensionId: Long) =
        dimensionOwed[dimensionId] ?: MoneyByCurrency.zero

    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = unsupported()

    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>) =
        dimensionIds.mapNotNull { id -> dimensionOwed[id]?.let { id to it } }.toMap()

    override suspend fun flowsByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, DimensionFlowsByCurrency> = unsupported()

    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency =
        unsupported()

    override suspend fun assetMonthFlowsByCurrency(
        month: YearMonth,
        yieldDimensionId: Long?,
    ): AssetMonthFlowsByCurrency = unsupported()

    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ) = dimensionTotals

    override suspend fun totalsByDimensionInMonthByCurrency(month: YearMonth, nominalType: AccountType) =
        dimensionTotals

    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ) = dimensionTotals

    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ) = scopeStats
}

class FakeBudgetRepository(private val budgets: List<Budget> = emptyList()) : IBudgetRepository {
    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(budgets)
    override suspend fun getAllBudgets(): List<Budget> = budgets
    override suspend fun insert(budget: Budget) = unsupported()
    override suspend fun update(budget: Budget) = unsupported()
    override suspend fun delete(budget: Budget) = unsupported()
    override suspend fun hasBudgetForCategory(categoryId: Long): Boolean = false
    override suspend fun hasBudgetForRecurring(recurringId: Long): Boolean = false
}

class FakeRecurringRepository(private val templates: List<Recurring> = emptyList()) : IRecurringRepository {
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(templates)
    override fun observeRecurringById(id: Long): Flow<Recurring?> = flowOf(templates.find { it.id == id })
    override suspend fun getRecurringById(id: Long): Recurring? = templates.find { it.id == id }
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = false
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = false
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = false
    override suspend fun insert(recurring: Recurring) = unsupported()
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = unsupported()

    override suspend fun update(recurring: Recurring) = unsupported()
    override suspend fun delete(recurring: Recurring) = unsupported()
}

class FakeRecurringOccurrenceRepository(
    private val occurrences: List<RecurringOccurrence> = emptyList(),
) : IRecurringOccurrenceRepository {

    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = flowOf(occurrences)
    override suspend fun getAllOccurrences(): List<RecurringOccurrence> = occurrences
    override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence? =
        occurrences.find { it.recurringId == recurringId && it.yearMonth == yearMonth }

    override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence? =
        occurrences.find { it.recurringId == recurringId && it.cycleNumber == cycleNumber }

    override suspend fun save(occurrence: RecurringOccurrence): Long = unsupported()
    override suspend fun confirmCycle(intent: TransactionIntent, occurrence: RecurringOccurrence): Transaction =
        unsupported()
}

class FakeInstallmentRepository(private val installments: List<Installment> = emptyList()) : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(installments)
    override suspend fun getAllInstallments(): List<Installment> = installments
    override suspend fun getInstallmentById(id: Long): Installment? = installments.find { it.id == id }
    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = unsupported()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = unsupported()
    override suspend fun deleteInstallmentById(id: Long) = unsupported()
}

class FakeBaseCurrency(code: String = "BRL") : IBaseCurrencyRepository {
    private val state = MutableStateFlow(code)
    override fun observe(): StateFlow<String> = state
    override suspend fun set(code: String) {
        state.value = code
    }
}

class FakeExchangeRates(private val rates: Map<String, ExchangeRate> = emptyMap()) : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate) = rates[currency]
    override suspend fun ratesAsOf(date: LocalDate) = rates
    override suspend fun rateBetween(from: String, to: String, date: LocalDate) = rates[from]
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(rates.values.toList())
    override suspend fun save(rate: ExchangeRate) = unsupported()
    override suspend fun remove(rate: ExchangeRate) = unsupported()
    override suspend fun countNaming(currency: String) = 0
    override suspend fun removeAllNaming(currency: String) = unsupported()
}

class FakeAccountCurrencies(private val inUse: List<String> = listOf("BRL")) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}

/** The single reducer of the app, wired for a test. */
fun consolidateMoney(
    base: String = "BRL",
    rates: Map<String, ExchangeRate> = emptyMap(),
    inUse: List<String> = listOf(base),
) = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrency(base),
    exchangeRateRepository = FakeExchangeRates(rates),
    getAccountCurrencies = FakeAccountCurrencies(inUse),
)

/** The one place a ledger figure becomes a payload, wired for a test. */
fun moneyFactory(
    base: String = "BRL",
    rates: Map<String, ExchangeRate> = emptyMap(),
    inUse: List<String> = listOf(base),
): MoneyPayloadFactory {
    val exchangeRates = FakeExchangeRates(rates)
    return MoneyPayloadFactory(
        consolidateMoney = ConsolidateMoneyUseCase(
            baseCurrencyRepository = FakeBaseCurrency(base),
            exchangeRateRepository = exchangeRates,
            getAccountCurrencies = FakeAccountCurrencies(inUse),
        ),
        exchangeRates = exchangeRates,
    )
}

// ---------------------------------------------------------------------------
// Use-case fakes: they record what they were called with
// ---------------------------------------------------------------------------

class RecordingCreateTransaction(
    private val answer: (TransactionForm) -> Either<Throwable, Transaction>,
) : CreateTransactionUseCase {

    var forms: MutableList<TransactionForm> = mutableListOf()
        private set

    override suspend fun invoke(form: TransactionForm): Either<Throwable, Transaction> {
        forms += form
        return answer(form)
    }
}

class RecordingAddInstallment(
    private val answer: (TransactionForm, Int) -> Either<Throwable, List<Transaction>>,
) : AddInstallmentUseCase {

    var calls: MutableList<Pair<TransactionForm, Int>> = mutableListOf()
        private set

    override suspend fun invoke(form: TransactionForm, installments: Int): Either<Throwable, List<Transaction>> {
        calls += form to installments
        return answer(form, installments)
    }
}

class RecordingPayInvoice(
    private val answer: (Long) -> Either<Throwable, Invoice>,
) : PayInvoicePaymentUseCase {

    var calls: MutableList<Triple<Long, LocalDate, Double?>> = mutableListOf()
        private set

    override suspend fun invoke(
        invoiceId: Long,
        date: LocalDate,
        account: Account,
        paidAmount: Double?,
    ): Either<Throwable, Invoice> {
        calls += Triple(invoiceId, date, paidAmount)
        return answer(invoiceId)
    }
}

class RecordingUpdateTransaction(
    private val answer: (Long, TransactionForm) -> Either<Throwable, Unit> = { _, _ -> Either.Right(Unit) },
) : UpdateTransactionUseCase {

    var calls: MutableList<Pair<Long, TransactionForm>> = mutableListOf()
        private set

    override suspend fun invoke(transactionId: Long, form: TransactionForm): Either<Throwable, Unit> {
        calls += transactionId to form
        return answer(transactionId, form)
    }
}

class RecordingDeleteTransaction(
    private val answer: (Transaction) -> Either<Throwable, Unit> = { Either.Right(Unit) },
) : DeleteTransactionUseCase {

    var removed: MutableList<Transaction> = mutableListOf()
        private set

    override suspend fun invoke(transaction: Transaction): Either<Throwable, Unit> {
        removed += transaction
        return answer(transaction)
    }
}

/** The application's journal, kept in memory so a test can read what a call left in it. */
class RecordingJournal : IAgentActivityRepository {

    val records: MutableList<AgentActivity> = mutableListOf()

    private val recent = MutableStateFlow<List<AgentActivity>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = recent

    override suspend fun record(activity: AgentActivity) {
        records += activity
        recent.value = records.toList()
    }

    override suspend fun prune(olderThan: Instant) = Unit
}

/**
 * A member no test of this module reaches.
 *
 * It refuses loudly rather than answering zero: a fake that invented a figure would let a
 * test pass over a read nobody wired.
 */
private fun unsupported(): Nothing = throw UnsupportedOperationException("Not wired for this test")
