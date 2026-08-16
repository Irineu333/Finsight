package com.neoutils.finsight.mcp

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
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
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.domain.usecase.spendingBreakdown
import com.neoutils.finsight.extension.displaySign
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The facades an agent asks about, held in memory.
 *
 * The **ledger** in these tests is the real one — the production `EntryRepository` over a real
 * `AppDatabase` — because every figure the questions family answers is a ledger read, and a fake
 * ledger would test the tools against a second opinion of what money does. What is faked is the
 * other half: the rows of the facade tables, which this module cannot reach the real repositories
 * for (they live in each feature's `impl`, which an `impl` may not depend on).
 *
 * The use cases whose implementation is likewise out of reach are rebuilt here **over the real
 * ledger and the real consolidation layer**, so that what the tools consume behaves as the app's
 * does rather than returning canned answers.
 */
/**
 * @param chart the **whole chart of accounts**, which is a different list from the facade's:
 * `getAllLedgerAccounts` answers every row the ledger holds — the cards' `LIABILITY` rows and the
 * nominal ones included — and a caller resolving "every account money can sit in" reads it. Backed
 * by the real `accounts` table, because a fake that answered only the facade would make a card
 * purchase invisible to anything asking that question.
 */
internal class FakeAccounts(
    private val accounts: MutableList<Account>,
    private val chart: suspend () -> List<Account> = { accounts },
    private val onInsert: suspend (Account) -> Long = { 0L },
) : IAccountRepository {
    override suspend fun getAllAccounts(): List<Account> = accounts.filterNot { it.isArchived }
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun hasYieldingAccount(): Boolean = accounts.any { it.yieldsInterest }
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = chart()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flow { emit(chart()) }
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override fun observeDefaultAccount(): Flow<Account?> = flowOf(accounts.firstOrNull { it.isDefault })
    override suspend fun insert(account: Account): Long = onInsert(account).also {
        accounts += account.copy(id = it)
    }

    override suspend fun update(account: Account) {
        accounts.replaceAll { if (it.id == account.id) account else it }
    }

    override suspend fun delete(account: Account) {
        accounts.removeAll { it.id == account.id }
    }

    override suspend fun reopen(accountId: Long) {
        accounts.replaceAll { if (it.id == accountId) it.copy(isArchived = false) else it }
    }
}

internal class FakeCategories(
    private val categories: MutableList<Category>,
    private val onInsert: suspend (Category) -> Category = { it },
) : ICategoryRepository {
    override suspend fun getAllCategories(): List<Category> = categories.filterNot { it.isArchived }
    override suspend fun getAllCategoriesIncludingClosed(): List<Category> = categories
    override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun getCategoryByDimensionId(dimensionId: Long): Category? =
        categories.firstOrNull { it.dimensionId == dimensionId }

    override suspend fun getCategoryBySystemKey(systemKey: String): Category? =
        categories.firstOrNull { it.systemKey == systemKey }

    override fun observeAllCategories(): Flow<List<Category>> = flowOf(categories)
    override fun observeAllCategoriesIncludingClosed(): Flow<List<Category>> = flowOf(categories)
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> =
        flowOf(categories.filter { it.type == type })

    override fun observeCategoryById(id: Long): Flow<Category?> =
        flowOf(categories.firstOrNull { it.id == id })

    override suspend fun archive(id: Long) {
        categories.replaceAll { if (it.id == id) it.copy(isArchived = true) else it }
    }

    override suspend fun unarchive(id: Long) {
        categories.replaceAll { if (it.id == id) it.copy(isArchived = false) else it }
    }

    override suspend fun existsByName(name: String, ignoreId: Long): Boolean =
        categories.any { it.id != ignoreId && it.name.equals(name, ignoreCase = true) }

    override suspend fun insert(category: Category): Long =
        onInsert(category).also { categories += it }.id

    override suspend fun insertAll(categories: List<Category>) = throw NotImplementedError()

    override suspend fun update(category: Category) {
        categories.replaceAll { if (it.id == category.id) category else it }
    }

    override suspend fun delete(category: Category) {
        categories.removeAll { it.id == category.id }
    }
}

internal class FakeCards(
    private val cards: MutableList<CreditCard>,
    private val onInsert: suspend (CreditCard, String) -> Long = { _, _ -> 0L },
) : ICreditCardRepository {
    override suspend fun getAllCreditCards(): List<CreditCard> = cards.filterNot { it.isArchived }
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        cards.firstOrNull { it.id == creditCardId }

    override fun observeAllCreditCards(): Flow<List<CreditCard>> = flowOf(cards)
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = flowOf(cards)
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> =
        flowOf(cards.firstOrNull { it.id == creditCardId })

    override suspend fun insert(creditCard: CreditCard, currency: String): Long =
        onInsert(creditCard, currency)

    override suspend fun update(creditCard: CreditCard) {
        cards.replaceAll { if (it.id == creditCard.id) creditCard.copy(currency = it.currency) else it }
    }

    override suspend fun delete(creditCard: CreditCard) {
        cards.removeAll { it.id == creditCard.id }
    }

    override suspend fun unarchive(accountId: Long) {
        cards.replaceAll { if (it.accountId == accountId) it.copy(isArchived = false) else it }
    }

    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

internal class FakeInvoices(private val invoices: MutableList<Invoice>) : IInvoiceRepository {
    override suspend fun getAllInvoices(): List<Invoice> = invoices
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        invoices.filter { it.creditCard.id == creditCardId }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        invoices.filter { it.creditCard.id == creditCardId && !it.status.isPaid }

    override suspend fun getUnpaidInvoicesByCreditCards(
        creditCardIds: Collection<Long>,
    ): Map<Long, List<Invoice>> = creditCardIds.associateWith { getUnpaidInvoicesByCreditCard(it) }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? =
        invoices.firstOrNull { it.creditCard.id == creditCardId && it.status.isOpen }

    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(invoices)
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> =
        flowOf(invoices.filter { it.creditCard.id == creditCardId })

    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> =
        flowOf(invoices.firstOrNull { it.id == invoiceId })

    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> =
        flowOf(invoices.firstOrNull { it.creditCard.id == creditCardId && it.status.isOpen })

    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> =
        flowOf(invoices.filter { it.creditCard.id == creditCardId })

    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> =
        flowOf(invoices.firstOrNull { it.creditCard.id == creditCardId && !it.status.isPaid })

    override fun observeUnpaidInvoices(): Flow<List<Invoice>> =
        flowOf(invoices.filterNot { it.status.isPaid })

    /**
     * The store's two assignments in one place: the identity and the ledger dimension its legs
     * are tagged with. A fixture that handed out neither would let an invoice exist that no
     * posting could ever land on.
     */
    var onInsert: suspend (Invoice) -> Invoice = { it }

    override suspend fun insert(invoice: Invoice): Invoice =
        onInsert(invoice).also { invoices += it }

    override suspend fun update(invoice: Invoice) {
        invoices.replaceAll { if (it.id == invoice.id) invoice else it }
    }

    override suspend fun deleteById(id: Long) {
        invoices.removeAll { it.id == id }
    }
}

internal class FakeBudgets(private val budgets: MutableList<Budget>) : IBudgetRepository {
    private var nextId = 1_000L

    override suspend fun getAllBudgets(): List<Budget> = budgets
    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(budgets)

    override suspend fun insert(budget: Budget): Long = nextId++.also {
        budgets += budget.copy(id = it)
    }

    override suspend fun update(budget: Budget) {
        budgets.replaceAll { if (it.id == budget.id) budget else it }
    }

    override suspend fun delete(budget: Budget) {
        budgets.removeAll { it.id == budget.id }
    }

    override suspend fun hasBudgetForCategory(categoryId: Long): Boolean =
        budgets.any { budget -> budget.categories.any { it.id == categoryId } }

    override suspend fun hasBudgetForRecurring(recurringId: Long): Boolean =
        budgets.any { it.recurringId == recurringId }
}

internal class FakeRecurring(
    private val recurringList: MutableList<Recurring>,
    /** Whether any posting names a template — the ledger's own `recurringId`, not a copy. */
    private val postedRecurringIds: suspend () -> Set<Long> = { emptySet() },
    private val budgets: List<Budget> = emptyList(),
) : IRecurringRepository {
    private var nextId = 2_000L

    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(recurringList)
    override fun observeRecurringById(id: Long): Flow<Recurring?> =
        flowOf(recurringList.firstOrNull { it.id == id })

    override suspend fun getRecurringById(id: Long): Recurring? = recurringList.firstOrNull { it.id == id }

    override suspend fun hasRecurringForAccount(accountId: Long): Boolean =
        recurringList.any { it.account?.id == accountId }

    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean =
        recurringList.any { it.creditCard?.id == creditCardId }

    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean =
        recurringList.any { it.category?.id == categoryId }

    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean =
        recurringId in postedRecurringIds()

    override suspend fun insert(recurring: Recurring): Long = nextId++.also {
        recurringList += recurring.copy(id = it)
    }
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()

    override suspend fun update(recurring: Recurring) {
        recurringList.replaceAll { if (it.id == recurring.id) recurring else it }
    }

    override suspend fun delete(recurring: Recurring) {
        recurringList.removeAll { it.id == recurring.id }
    }
}

internal class FakeOccurrences(
    private val occurrences: List<RecurringOccurrence>,
) : IRecurringOccurrenceRepository {
    override suspend fun getAllOccurrences(): List<RecurringOccurrence> = occurrences
    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = flowOf(occurrences)
    override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence? =
        occurrences.firstOrNull { it.recurringId == recurringId && it.yearMonth == yearMonth }

    override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence? =
        occurrences.firstOrNull { it.recurringId == recurringId && it.cycleNumber == cycleNumber }

    override suspend fun save(occurrence: RecurringOccurrence): Long = throw NotImplementedError()
    override suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()
}

internal class FakeInstallments(private val installments: MutableList<Installment>) : IInstallmentRepository {
    private var nextId = 3_000L

    override fun observeAllInstallments(): Flow<List<Installment>> = flowOf(installments)
    override suspend fun getAllInstallments(): List<Installment> = installments
    override suspend fun getInstallmentById(id: Long): Installment? =
        installments.firstOrNull { it.id == id }

    override suspend fun createInstallment(count: Int, totalAmount: Double): Long = nextId++.also {
        installments += Installment(id = it, count = count, totalAmount = totalAmount)
    }

    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) {
        installments.replaceAll {
            if (it.id == id) it.copy(count = count, totalAmount = totalAmount) else it
        }
    }

    override suspend fun deleteInstallmentById(id: Long) {
        installments.removeAll { it.id == id }
    }
}

// ----------------------------------------------------------------------------------
// The use cases whose implementation lives in a feature `impl`, rebuilt over the real ledger
// ----------------------------------------------------------------------------------

/**
 * The same composition `CalculateCategorySpendingUseCaseImpl` performs: the month's per-dimension
 * totals from the real ledger, translated into subjects, handed to the **real** single breakdown
 * builder — which owns the sign, the ranking, the scale and the share.
 *
 * It stands in for an implementation this module cannot depend on, and it is written this way so
 * that what the tools consume is the app's own behaviour rather than a canned list.
 */
internal class LedgerCategoryTotals(
    private val nominalType: AccountType,
    private val categories: List<Category>,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : CalculateCategorySpendingUseCase, CalculateCategoryIncomeUseCase {

    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> {
        val byDimension = categories.associateBy { it.dimensionId }
        val totals = entryRepository
            .totalsByDimensionInMonthByCurrency(forYearMonth, nominalType)
            .mapNotNull { (dimensionId, natural) ->
                val subject = when (dimensionId) {
                    null -> SpendingSubject.Uncategorized
                    else -> byDimension[dimensionId]?.let(SpendingSubject::Categorized)
                        ?: return@mapNotNull null
                }
                subject to natural
            }
            .toMap()

        return consolidateMoney.spendingBreakdown(
            totals = totals,
            displaySign = nominalType.displaySign,
            on = forYearMonth.safeOnDay(forYearMonth.numberOfDays),
        )
    }
}

/** Owed on an invoice = Σ the entries carrying its dimension, read positive — from the real ledger. */
internal class LedgerInvoiceOwed(
    private val entryRepository: IEntryRepository,
) : CalculateInvoiceUseCase {
    override suspend fun invoke(invoices: Collection<Invoice>): Map<Long, Double> {
        val owed = entryRepository.owedByDimensionByCurrency(invoices.mapNotNull { it.dimensionId })
        return invoices.associate { invoice ->
            invoice.id to (invoice.dimensionId?.let { owed[it] }?.singleOrNull()?.value ?: 0.0)
        }
    }
}

/** What a card's unpaid invoices owe together, and what is left of its limit. */
internal class LedgerAvailableLimit(
    private val cards: List<CreditCard>,
    private val invoices: List<Invoice>,
    private val owed: CalculateInvoiceUseCase,
) : CalculateAvailableLimitUseCase {
    override suspend fun invoke(creditCardIds: Collection<Long>): Map<Long, Limit> {
        val unpaid = invoices.filterNot { it.status.isPaid }
        val amounts = owed(unpaid)
        return creditCardIds.mapNotNull { id ->
            val card = cards.firstOrNull { it.id == id } ?: return@mapNotNull null
            val used = unpaid.filter { it.creditCard.id == id }.sumOf { amounts[it.id] ?: 0.0 }
            id to Limit(
                totalUnpaidAmount = used,
                available = (card.limit - used).coerceAtLeast(0.0),
                usage = if (card.limit > 0) used / card.limit else 0.0,
            )
        }.toMap()
    }
}

/** The report's figures, from the real ledger aggregate, with the perspective resolved as the app does. */
internal class LedgerReportStats(
    private val accounts: List<Account>,
    private val cards: List<CreditCard>,
    private val entryRepository: IEntryRepository,
) : CalculateReportStatsUseCase {
    override suspend fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency {
        val scope = when (perspective) {
            is ReportPerspective.AccountPerspective -> perspective.accountIds
                .ifEmpty { accounts.map { it.id } }

            is ReportPerspective.CreditCardPerspective ->
                listOfNotNull(cards.firstOrNull { it.id == perspective.creditCardId }?.accountId)
        }
        return entryRepository.scopeStatsByCurrency(scope, startDate, endDate)
    }
}

// ----------------------------------------------------------------------------------
// The consolidation layer, with the rates the test states
// ----------------------------------------------------------------------------------

internal class FixedBaseCurrency(base: String) : IBaseCurrencyRepository {
    private val state = MutableStateFlow(base)
    override fun observe(): StateFlow<String> = state
    override suspend fun set(code: String) = error("the surface never moves the base")
}

internal class FixedRates(
    private val base: String,
    private val rates: Map<String, Double>,
) : IExchangeRateRepository {

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> =
        rates.mapValues { (currency, rate) ->
            ExchangeRate(
                currency = currency,
                counterCurrency = base,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.USER,
            )
        }

    override suspend fun rateAsOf(currency: String, date: LocalDate) = ratesAsOf(date)[currency]
    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
    override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
    override suspend fun save(rate: ExchangeRate) = error("the surface never writes a rate")
    override suspend fun remove(rate: ExchangeRate) = error("the surface never writes a rate")
    override suspend fun countNaming(currency: String): Int = 0
    override suspend fun removeAllNaming(currency: String) = error("the surface never writes a rate")
}

internal class CurrenciesInUse(private val inUse: List<String>) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}
