@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.neoutils.finsight.mcp

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.minusMonth
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A user's whole financial life, as far as the questions family can see it.
 *
 * **The ledger is real.** The tools' figures all come through `IEntryRepository`, so the tests open
 * a real `AppDatabase`, seed it through the production DAOs and hand the tools the production
 * `EntryRepository`. That is what makes an assertion like "a transfer is not spending" a statement
 * about the app rather than about a fake that was written to agree.
 *
 * What is in memory is the **facade** side — accounts, cards, invoices, categories, budgets,
 * templates — whose repositories live in feature `impl`s this module may not depend on. Their
 * contents are the caller's to state, and the ledger rows are seeded to match.
 */
internal class AgentWorld(
    val today: LocalDate = LocalDate(2026, 3, 15),
    private val baseCurrency: String = BRL,
    private val rates: Map<String, Double> = emptyMap(),
    private val currenciesInUse: List<String> = listOf(baseCurrency),
) : AutoCloseable {

    private val file: File = File.createTempFile("finsight-agent", ".db")
        .also { it.delete(); it.deleteOnExit() }

    val database: AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val entryRepository = EntryRepository(database.entryDao())

    val clock = object : Clock {
        override fun now(): Instant = today.atTime(NOON, 0).toInstant(TimeZone.currentSystemDefault())
    }

    val accounts = mutableListOf<Account>()
    val categories = mutableListOf<Category>()
    val cards = mutableListOf<CreditCard>()
    val invoices = mutableListOf<Invoice>()
    val budgets = mutableListOf<Budget>()
    val recurringList = mutableListOf<Recurring>()
    val occurrences = mutableListOf<RecurringOccurrence>()

    private var nextTransactionId = 0L

    val consolidateMoney = ConsolidateMoneyUseCase(
        baseCurrencyRepository = FixedBaseCurrency(baseCurrency),
        exchangeRateRepository = FixedRates(baseCurrency, rates),
        // What the reducer asks for when a figure has nothing to say: a zero is denominated by the
        // currencies the user actually holds, never by the base out of habit.
        getAccountCurrencies = CurrenciesInUse(currenciesInUse),
    )

    // ------------------------------------------------------------------------------
    // Seeding
    // ------------------------------------------------------------------------------

    /** An account the user holds, in the chart and in the facade at once. */
    suspend fun account(
        id: Long,
        name: String,
        currency: String = BRL,
        isDefault: Boolean = false,
        isArchived: Boolean = false,
    ): Account {
        ledgerAccount(id, AccountEntity.Type.ASSET, name, currency, isArchived)
        return Account(
            id = id,
            name = name,
            type = AccountType.ASSET,
            currency = currency,
            isDefault = isDefault,
            isArchived = isArchived,
        ).also { accounts += it }
    }

    /** A card and the `LIABILITY` row it projects onto. */
    suspend fun card(
        id: Long,
        accountId: Long,
        name: String,
        limit: Double = 0.0,
        currency: String = BRL,
        closingDay: Int = 20,
        dueDay: Int = 28,
        isArchived: Boolean = false,
    ): CreditCard {
        ledgerAccount(accountId, AccountEntity.Type.LIABILITY, name, currency, isArchived)
        return CreditCard(
            id = id,
            name = name,
            limit = limit,
            closingDay = closingDay,
            dueDay = dueDay,
            accountId = accountId,
            isArchived = isArchived,
            currency = currency,
        ).also { cards += it }
    }

    /** A category, with the dimension the ledger classifies its legs by. */
    suspend fun category(
        id: Long,
        dimensionId: Long,
        name: String,
        type: Category.Type = Category.Type.EXPENSE,
        systemKey: String? = null,
        isArchived: Boolean = false,
    ): Category {
        database.dimensionDao().insert(DimensionEntity(id = dimensionId, kind = DimensionKind.CATEGORY))
        return Category(
            id = id,
            name = name,
            icon = CategoryLazyIcon("tag"),
            type = type,
            createdAt = 0,
            isArchived = isArchived,
            dimensionId = dimensionId,
            systemKey = systemKey,
        ).also { categories += it }
    }

    /** An invoice, with the sub-ledger dimension its card's legs carry. */
    suspend fun invoice(
        id: Long,
        dimensionId: Long,
        card: CreditCard,
        month: YearMonth,
        status: Invoice.Status = Invoice.Status.OPEN,
    ): Invoice {
        database.dimensionDao().insert(DimensionEntity(id = dimensionId, kind = DimensionKind.INVOICE))
        return Invoice(
            id = id,
            creditCard = card,
            dimensionId = dimensionId,
            // A cycle opens in the month before the one it closes and falls due in: the invoice
            // refuses anything else, and a fixture that lied about it would test a shape the app
            // never produces.
            openingMonth = month.minusMonth(),
            closingMonth = month,
            dueMonth = month,
            status = status,
        ).also { invoices += it }
    }

    /** One of the nominal or system rows a posting lands on. */
    suspend fun ledgerAccount(
        id: Long,
        type: AccountEntity.Type,
        name: String = "account-$id",
        currency: String = BRL,
        isArchived: Boolean = false,
    ): Long = database.accountDao().insert(
        AccountEntity(id = id, name = name, type = type, currency = currency, isArchived = isArchived),
    )

    /** One balanced posting. What its legs sum to is the caller's business, as in the ledger's own suites. */
    suspend fun posting(date: String, vararg legs: Leg): Long {
        val id = ++nextTransactionId
        database.transactionDao().insert(
            TransactionEntity(id = id, title = null, date = LocalDate.parse(date)),
        )
        database.entryDao().insertAll(
            legs.map {
                EntryEntity(
                    transactionId = id,
                    accountId = it.accountId,
                    amount = it.cents,
                    currency = it.currency,
                    dimensionId = it.dimensionId,
                )
            },
        )
        return id
    }

    // ------------------------------------------------------------------------------
    // What the tools are handed
    // ------------------------------------------------------------------------------

    fun dependencies() = McpToolDependencies(
        clock = clock,
        entryRepository = entryRepository,
        transactionRepository = FakeTransactions(emptyList()),
        accountRepository = FakeAccounts(accounts),
        categoryRepository = FakeCategories(categories),
        creditCardRepository = FakeCards(cards),
        invoiceRepository = FakeInvoices(invoices),
        budgetRepository = FakeBudgets(budgets),
        recurringRepository = FakeRecurring(recurringList),
        recurringOccurrenceRepository = FakeOccurrences(occurrences),
        consolidateMoney = consolidateMoney,
        calculateBalance = CalculateBalanceUseCase(entryRepository),
        calculateCategorySpending = LedgerCategoryTotals(
            nominalType = AccountType.EXPENSE,
            categories = categories,
            entryRepository = entryRepository,
            consolidateMoney = consolidateMoney,
        ),
        calculateCategoryIncome = LedgerCategoryTotals(
            nominalType = AccountType.INCOME,
            categories = categories,
            entryRepository = entryRepository,
            consolidateMoney = consolidateMoney,
        ),
        calculateBudgetProgress = CalculateBudgetProgressUseCase(entryRepository, consolidateMoney),
        getPendingRecurring = GetPendingRecurringUseCase(),
        calculateAvailableLimit = LedgerAvailableLimit(cards, invoices, LedgerInvoiceOwed(entryRepository)),
        calculateInvoice = LedgerInvoiceOwed(entryRepository),
        calculateReportStats = LedgerReportStats(accounts, cards, entryRepository),
    )

    /** The production registry, over this world. */
    fun tools(): List<McpTool> = mcpTools(dependencies())

    fun tool(name: McpToolName): McpTool = tools().first { it.name == name.wireName }

    override fun close() {
        database.close()
        file.delete()
    }

    companion object {
        const val BRL = "BRL"
        private const val NOON = 12
    }
}

/** One leg of a seeded posting: where it lands, how much, in what currency, classified how. */
internal data class Leg(
    val accountId: Long,
    val cents: Long,
    val dimensionId: Long? = null,
    val currency: String = AgentWorld.BRL,
)

internal infix fun Long.posts(cents: Long) = Leg(accountId = this, cents = cents)

internal fun Leg.taggedWith(dimensionId: Long) = copy(dimensionId = dimensionId)

internal infix fun Leg.inCurrency(currency: String) = copy(currency = currency)
