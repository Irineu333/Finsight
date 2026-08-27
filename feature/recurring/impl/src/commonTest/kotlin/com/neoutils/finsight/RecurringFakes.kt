package com.neoutils.finsight

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

fun recurring(
    id: Long = 1L,
    type: TransactionType = TransactionType.EXPENSE,
    amount: Double = 100.0,
    createdAt: Long = 0L,
    isArchived: Boolean = false,
) = Recurring(
    id = id,
    type = type,
    amount = amount,
    title = "Rec $id",
    dayOfMonth = 5,
    category = null,
    account = null,
    creditCard = null,
    createdAt = createdAt,
    isArchived = isArchived,
)

class FakeCrashlytics : Crashlytics {
    val recorded = mutableListOf<Throwable>()
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) { recorded += e }
}

class FakeRecurringRepository(
    private val hasTransaction: Boolean = false,
    private val updateFailure: Throwable? = null,
) : IRecurringRepository {

    val all = MutableStateFlow<List<Recurring>>(emptyList())
    private val byId = MutableSharedFlow<Recurring?>(replay = 1)

    val updated = mutableListOf<Recurring>()
    val deleted = mutableListOf<Recurring>()

    fun emit(recurring: Recurring?) { byId.tryEmit(recurring) }

    override fun observeAllRecurring(): Flow<List<Recurring>> = all
    override fun observeRecurringById(id: Long): Flow<Recurring?> = byId
    override suspend fun getRecurringById(id: Long): Recurring? = all.value.firstOrNull { it.id == id }
    override suspend fun hasRecurringForAccount(accountId: Long) = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
    override suspend fun hasRecurringForCategory(categoryId: Long) = false
    override suspend fun hasTransactionForRecurring(recurringId: Long) = hasTransaction
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) {
        updateFailure?.let { throw it }
        updated += recurring
    }
    override suspend fun delete(recurring: Recurring) { deleted += recurring }
}

class FakeBudgetRepository(
    private val hasBudget: Boolean = false,
) : IBudgetRepository {
    override fun observeAllBudgets(): Flow<List<Budget>> = throw NotImplementedError()
    override suspend fun getAllBudgets(): List<Budget> = emptyList()
    override suspend fun insert(budget: Budget) = throw NotImplementedError()
    override suspend fun update(budget: Budget) = throw NotImplementedError()
    override suspend fun delete(budget: Budget) = throw NotImplementedError()
    override suspend fun hasBudgetForCategory(categoryId: Long) = false
    override suspend fun hasBudgetForRecurring(recurringId: Long) = hasBudget
}

/**
 * The chart of accounts, for the one question these screens ask of it: what currency a
 * card's account is in. Everything else throws, so a test that starts depending on more
 * says so instead of quietly passing.
 *
 * **The facade reads and the chart reads answer differently here, as they do in the
 * DAO**: `accounts` is `ASSET` and `isArchived = 0`, the "including closed" pair drops
 * only the second condition, and the ledger pair is the whole table. A fake that handed
 * the same list to all three would let a caller read a card's `LIABILITY` account out of
 * the accounts facade — which the real query never returns — and the test would go green
 * on a screen that shows no currency at all.
 */
class FakeAccountRepository(
    private val accounts: List<Account> = emptyList(),
) : IAccountRepository {
    private val facade get() = accounts.filter { it.type == AccountType.ASSET }
    private val openFacade get() = facade.filterNot { it.isArchived }

    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(openFacade)
    override suspend fun getAllAccounts(): List<Account> = openFacade
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = facade
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(facade)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(accounts.firstOrNull { it.isDefault })
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

/**
 * The occurrences of the month, and what the confirmed ones posted.
 *
 * [settled] is stated rather than derived: what the aggregate query computes is verified
 * against a real database in `jvmTest`, and a view-model test that recomputed it here
 * would only be asserting its own arithmetic.
 */
class FakeRecurringOccurrenceRepository(
    var settled: RecurringSettledMoney = RecurringSettledMoney.none,
) : IRecurringOccurrenceRepository {

    val all = MutableStateFlow<List<RecurringOccurrence>>(emptyList())

    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = all
    override suspend fun getAllOccurrences(): List<RecurringOccurrence> = all.value
    override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth) =
        all.value.firstOrNull { it.recurringId == recurringId && it.yearMonth == yearMonth }

    override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int) =
        all.value.firstOrNull { it.recurringId == recurringId && it.cycleNumber == cycleNumber }

    override suspend fun save(occurrence: RecurringOccurrence): Long = throw NotImplementedError()
    override suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()

    override suspend fun settledIn(month: YearMonth): RecurringSettledMoney = settled
}

/** The base currency in force. */
class FakeBaseCurrency(code: String = "BRL") : IBaseCurrencyRepository {
    private val state = MutableStateFlow(code)
    override fun observe(): StateFlow<String> = state
    override suspend fun set(code: String) { state.value = code }
}

/** An archive holding whatever the test registered, and nothing by default. */
class FakeExchangeRates(
    private val rates: Map<String, ExchangeRate> = emptyMap(),
) : IExchangeRateRepository {
    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = rates[currency]
    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = rates
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(rates.values.toList())
    override suspend fun save(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: ExchangeRate) = throw NotImplementedError()
    override suspend fun countNaming(currency: String) = 0
    override suspend fun removeAllNaming(currency: String) = Unit
}

class FakeAccountCurrencies(
    private vararg val inUse: String,
) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() =
        AccountCurrencies(inUse = inUse.toList(), ofDefaultAccount = inUse.firstOrNull())
}

/**
 * The real reducer over the fakes, never a stub: what the summary asserts is the figure a
 * user reads, and a fake reducer would let a wrong policy or a lost term pass.
 */
fun consolidator(
    baseCurrency: String = "BRL",
    rates: Map<String, ExchangeRate> = emptyMap(),
    inUse: Array<String> = arrayOf(baseCurrency),
) = ConsolidateMoneyUseCase(
    baseCurrencyRepository = FakeBaseCurrency(baseCurrency),
    exchangeRateRepository = FakeExchangeRates(rates),
    getAccountCurrencies = FakeAccountCurrencies(*inUse),
)

/**
 * The ledger seen only as the trigger the screen listens to. Every read throws: this
 * screen asks the ledger nothing directly — its one ledger figure comes through
 * `IRecurringOccurrenceRepository.settledIn` — and a fake that starts answering a read
 * says so instead of quietly passing.
 */
class TriggerOnlyLedger : IEntryRepository {
    val changes = MutableStateFlow(Unit)

    override fun observeLedgerChanges(): Flow<Unit> = changes

    override suspend fun getEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate) = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>) = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>) = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long) = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long) = throw NotImplementedError()
    override suspend fun balance(accountId: Long) = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth) = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?) = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long) = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>) = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>) = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth) = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?) = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>) = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonthByCurrency(month: YearMonth, nominalType: AccountType) = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(nominalType: AccountType, scopeDimensionIds: List<Long>) = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate) = throw NotImplementedError()
}

/** The composed invalidation trigger over the same fakes. */
fun consolidationChanges(
    ledger: IEntryRepository = TriggerOnlyLedger(),
    baseCurrency: String = "BRL",
) = ObserveConsolidationChangesUseCase(
    entryRepository = ledger,
    baseCurrencyRepository = FakeBaseCurrency(baseCurrency),
    exchangeRateRepository = FakeExchangeRates(),
)
