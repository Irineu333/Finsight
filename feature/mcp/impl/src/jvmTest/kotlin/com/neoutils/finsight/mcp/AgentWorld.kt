@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.neoutils.finsight.mcp

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.mapper.toDomain
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
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

    /**
     * The production repository over the production DAOs, so what the catalogue lists is the
     * ledger's own answer — the row, its legs, and every account hydrated from the chart.
     *
     * The two ports take their no-op forms because nothing here writes: a facade veto and a removal
     * hook exist for the write path, and the catalogue family has none.
     */
    val transactionRepository = TransactionRepository(
        database = database,
        transactionDao = database.transactionDao(),
        entryDao = database.entryDao(),
        accountDao = database.accountDao(),
        writeGuard = DimensionWriteGuard.None,
        removalHook = TransactionRemovalHook.None,
        transactionMapper = TransactionMapper(),
        ledgerEntryWriter = LedgerEntryWriter(
            entryDao = database.entryDao(),
            accountDao = database.accountDao(),
            dimensionDao = database.dimensionDao(),
        ),
    )

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
    val installments = mutableListOf<Installment>()

    private var nextTransactionId = 0L

    /**
     * The one rate archive of this world: what the test declared, and what the app's own operations
     * harvested. Held as a property because both directions are asserted — the reducer reads the
     * first, and a cross-currency operation writes the second.
     */
    val exchangeRates = FixedRates(baseCurrency, rates)

    val consolidateMoney = ConsolidateMoneyUseCase(
        baseCurrencyRepository = FixedBaseCurrency(baseCurrency),
        exchangeRateRepository = exchangeRates,
        // What the reducer asks for when a figure has nothing to say: a zero is denominated by the
        // currencies the user actually holds, never by the base out of habit.
        getAccountCurrencies = CurrenciesInUse(currenciesInUse),
    )

    /** The app's own harvester, over this world's archive — never a stand-in for it. */
    val harvestExchangeRate = HarvestExchangeRateUseCase(exchangeRates)

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

    /**
     * One balanced posting. What its legs sum to is the caller's business, as in the ledger's own
     * suites.
     *
     * The identity is assigned here, in ascending order, which is what makes a fixture able to say
     * "recorded after": the ledger assigns the same way, and the catalogue's recording order is
     * that identity.
     */
    suspend fun posting(
        date: String,
        vararg legs: Leg,
        title: String? = null,
        installmentId: Long? = null,
        installmentNumber: Int? = null,
        recurringId: Long? = null,
    ): Long {
        val id = ++nextTransactionId
        database.transactionDao().insert(
            TransactionEntity(
                id = id,
                title = title,
                date = LocalDate.parse(date),
                installmentId = installmentId,
                installmentNumber = installmentNumber,
                recurringId = recurringId,
            ),
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

    /**
     * The whole chart of accounts, from the real table — the cards' `LIABILITY` rows and the
     * nominal ones included, which the facade list deliberately does not hold.
     */
    private suspend fun ledgerChart(): List<Account> =
        database.accountDao().getAllLedgerAccounts().map { it.toDomain() }

    // ------------------------------------------------------------------------------
    // The facades, writable — and the ledger rows a creation has to bring with it
    // ------------------------------------------------------------------------------

    /**
     * The facade repositories, built once and shared by every read and every write of a world.
     *
     * They are properties rather than expressions in [dependencies] because the registration
     * family writes to them: two instances over the same list would be two stores, and a tool
     * that created an account would answer with one nothing else could see.
     *
     * Each creation carries the ledger row it needs — an account row for an account, a
     * `LIABILITY` row for a card, a dimension for a category and for an invoice — which is what
     * the real repositories do and what makes a posting on something just created possible.
     */
    val accountRepository = FakeAccounts(
        accounts = accounts,
        chart = { ledgerChart() },
        onInsert = { account ->
            database.accountDao().insert(
                AccountEntity(
                    name = account.name,
                    type = AccountEntity.Type.ASSET,
                    currency = account.currency,
                    isArchived = account.isArchived,
                ),
            )
        },
    )

    val categoryRepository = FakeCategories(
        categories = categories,
        onInsert = { category ->
            category.copy(
                id = ++nextCategoryId,
                dimensionId = database.dimensionDao().emit(DimensionKind.CATEGORY),
            )
        },
    )

    val creditCardRepository = FakeCards(
        cards = cards,
        onInsert = { card, currency ->
            val accountId = database.accountDao().insert(
                AccountEntity(
                    name = card.name,
                    type = AccountEntity.Type.LIABILITY,
                    currency = currency,
                ),
            )
            (++nextCardId).also {
                cards += card.copy(id = it, accountId = accountId, currency = currency)
            }
        },
    )

    val invoiceRepository = FakeInvoices(invoices).apply {
        onInsert = { invoice ->
            invoice.copy(
                id = ++nextInvoiceId,
                dimensionId = database.dimensionDao().emit(DimensionKind.INVOICE),
            )
        }
    }

    val budgetRepository = FakeBudgets(budgets)
    val installmentRepository = FakeInstallments(installments)

    val recurringRepository = FakeRecurring(
        recurringList = recurringList,
        // The ledger's own answer, not a flag a fixture set: a template is in use when a posting
        // names it, and that is what decides whether it may be deleted or must be archived.
        postedRecurringIds = {
            transactionRepository.getAllTransactions().mapNotNull { it.recurringId }.toSet()
        },
    )

    private var nextCategoryId = 500L
    private var nextCardId = 600L
    private var nextInvoiceId = 700L

    val occurrenceRepository = FakeOccurrences(occurrences, transactionRepository)

    /** The write use cases, over the real ledger. See `AgentWorldWrites`. */
    private val createInvoice = WorldCreateInvoice(creditCardRepository, invoiceRepository)

    private val buildTransaction = WorldBuildTransaction(clock, invoiceRepository, createInvoice)

    /** The operation use cases, over the same ledger. See `AgentWorldOperations`. */
    private val calculateInvoice = LedgerInvoiceOwed(entryRepository)

    private val payInvoice = WorldPayInvoice(invoiceRepository, clock)

    private val openInvoice = WorldOpenInvoice(invoiceRepository, creditCardRepository, clock)

    private val addInstallment = WorldAddInstallment(
        transactionRepository = transactionRepository,
        installmentRepository = installmentRepository,
        invoiceRepository = invoiceRepository,
        createInvoice = createInvoice,
        clock = clock,
    )

    fun dependencies() = McpToolDependencies(
        clock = clock,
        entryRepository = entryRepository,
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        creditCardRepository = creditCardRepository,
        invoiceRepository = invoiceRepository,
        installmentRepository = installmentRepository,
        budgetRepository = budgetRepository,
        recurringRepository = recurringRepository,
        recurringOccurrenceRepository = occurrenceRepository,
        baseCurrencyRepository = FixedBaseCurrency(baseCurrency),
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
        calculateAvailableLimit = LedgerAvailableLimit(cards, invoices, calculateInvoice),
        calculateInvoice = calculateInvoice,
        calculateReportStats = LedgerReportStats(accounts, cards, entryRepository),
        registerTransaction = WorldRegisterTransaction(
            transactionRepository = transactionRepository,
            buildTransaction = buildTransaction,
            addInstallment = addInstallment,
            recurringRepository = recurringRepository,
            clock = clock,
        ),
        updateTransaction = WorldUpdateTransaction(transactionRepository, buildTransaction),
        deleteTransaction = WorldDeleteTransaction(transactionRepository),
        createAccount = WorldCreateAccount(accountRepository),
        updateAccount = WorldUpdateAccount(accountRepository),
        deleteAccount = WorldDeleteAccount(accountRepository, entryRepository, recurringRepository),
        createCategory = WorldCreateCategory(categoryRepository),
        updateCategory = WorldUpdateCategory(categoryRepository),
        deleteCategory = WorldDeleteCategory(
            categoryRepository = categoryRepository,
            entryRepository = entryRepository,
            budgetRepository = budgetRepository,
            recurringRepository = recurringRepository,
            accountRepository = accountRepository,
        ),
        addCreditCard = WorldAddCreditCard(creditCardRepository, createInvoice, invoiceRepository, clock),
        updateCreditCard = WorldUpdateCreditCard(creditCardRepository),
        deleteCreditCard = WorldDeleteCreditCard(creditCardRepository, entryRepository, recurringRepository),
        createBudget = WorldCreateBudget(budgetRepository),
        updateBudget = WorldUpdateBudget(budgetRepository),
        deleteBudget = WorldDeleteBudget(budgetRepository),
        saveRecurring = WorldSaveRecurring(recurringRepository),
        deleteRecurring = WorldDeleteRecurring(recurringRepository, budgetRepository),
        addInstallment = addInstallment,
        updateInstallment = WorldUpdateInstallment(installmentRepository),
        deleteInstallment = WorldDeleteInstallment(transactionRepository, installmentRepository),
        createInvoice = createInvoice,
        deleteFutureInvoice = WorldDeleteFutureInvoice(invoiceRepository, transactionRepository),
        payInvoicePayment = WorldPayInvoicePayment(
            clock = clock,
            transactionRepository = transactionRepository,
            invoiceRepository = invoiceRepository,
            accountRepository = accountRepository,
            calculateInvoice = calculateInvoice,
            payInvoice = payInvoice,
            harvestExchangeRate = harvestExchangeRate,
        ),
        advanceInvoicePayment = WorldAdvanceInvoicePayment(
            transactionRepository = transactionRepository,
            invoiceRepository = invoiceRepository,
            accountRepository = accountRepository,
            calculateInvoice = calculateInvoice,
            harvestExchangeRate = harvestExchangeRate,
            clock = clock,
        ),
        closeInvoice = WorldCloseInvoice(
            invoiceRepository = invoiceRepository,
            calculateInvoice = calculateInvoice,
            payInvoice = payInvoice,
            openInvoice = openInvoice,
        ),
        openInvoice = openInvoice,
        reopenInvoice = WorldReopenInvoice(invoiceRepository),
        adjustInvoice = WorldAdjustInvoice(invoiceRepository, transactionRepository, calculateInvoice),
        adjustBalance = WorldAdjustBalance(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            calculateBalance = CalculateBalanceUseCase(entryRepository),
        ),
        transferBetweenAccounts = WorldTransfer(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            harvestExchangeRate = harvestExchangeRate,
            clock = clock,
        ),
        setDefaultAccount = WorldSetDefaultAccount(accountRepository),
        confirmRecurring = WorldConfirmRecurring(
            recurringRepository = recurringRepository,
            occurrenceRepository = occurrenceRepository,
            invoiceRepository = invoiceRepository,
            createInvoice = createInvoice,
            clock = clock,
        ),
        skipRecurring = WorldSkipRecurring(recurringRepository, occurrenceRepository, clock),
        archiveAccount = WorldArchiveAccount(
            accountRepository = accountRepository,
            accountDao = database.accountDao(),
            entryRepository = entryRepository,
        ),
        unarchiveAccount = WorldUnarchiveAccount(accountRepository, database.accountDao()),
        archiveCreditCard = WorldArchiveCreditCard(
            creditCardRepository = creditCardRepository,
            accountDao = database.accountDao(),
            entryRepository = entryRepository,
        ),
        unarchiveCreditCard = WorldUnarchiveCreditCard(creditCardRepository, database.accountDao()),
        archiveCategory = WorldArchiveCategory(categoryRepository),
        unarchiveCategory = WorldUnarchiveCategory(categoryRepository),
        archiveRecurring = WorldArchiveRecurring(recurringRepository),
        unarchiveRecurring = WorldUnarchiveRecurring(recurringRepository),
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
