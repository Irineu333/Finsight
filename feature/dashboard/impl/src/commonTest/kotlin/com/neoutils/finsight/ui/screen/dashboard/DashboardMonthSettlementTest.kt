package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.GetRecurringCyclesUseCase
import com.neoutils.finsight.domain.usecase.GetUnhandledRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settlement figure is a pair over two disjoint sources — this month's untreated
 * recurring templates and the invoices whose due month has arrived — and what these tests
 * pin is the arithmetic of that pair: which source feeds which class, that nothing is
 * counted twice, and that confirming a card recurring moves value between the sources
 * without moving the total.
 */
class DashboardMonthSettlementTest {

    private val march = YearMonth(2026, 3)
    private val february = YearMonth(2026, 2)
    private val today = LocalDate(2026, 3, 20)

    private val wallet = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val cardAccount = Account(id = 2, name = "Nubank", type = AccountType.LIABILITY, currency = "BRL")
    private val euroCardAccount = Account(id = 3, name = "Revolut", type = AccountType.LIABILITY, currency = "EUR")
    private val otherCardAccount = Account(id = 4, name = "Itau", type = AccountType.LIABILITY, currency = "BRL")

    private val card = CreditCard(id = 1, name = "Nubank", limit = 5_000.0, closingDay = 20, dueDay = 28, accountId = 2)
    private val euroCard = CreditCard(id = 2, name = "Revolut", limit = 2_000.0, closingDay = 20, dueDay = 28, accountId = 3)
    // Same currency as `card` on purpose: a credit that fails to offset because the two
    // figures are in different currencies would prove nothing about the flooring.
    private val otherCard = CreditCard(id = 3, name = "Itau", limit = 3_000.0, closingDay = 20, dueDay = 28, accountId = 4)

    private fun builder(entryRepository: IEntryRepository) = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = entryRepository),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(entryRepository, reducer()),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(GetRecurringCyclesUseCase(GetUnhandledRecurringUseCase())),
        getUnhandledRecurringUseCase = GetUnhandledRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(
                invoice: Invoice,
                cardInvoices: List<Invoice>,
                limit: Limit,
            ): InvoiceUi = throw NotImplementedError()
        },
        calculateAvailableLimit = object : CalculateAvailableLimitUseCase {
            override suspend fun invoke(creditCardIds: Collection<Long>): Map<Long, Limit> =
                throw NotImplementedError()
        },
        entryRepository = entryRepository,
        accountRepository = FakeAccountRepository(listOf(wallet, cardAccount, euroCardAccount, otherCardAccount)),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private fun recurring(
        id: Long,
        type: TransactionType,
        amount: Double,
        onCard: CreditCard? = null,
    ) = Recurring(
        id = id,
        type = type,
        amount = amount,
        title = null,
        dayOfMonth = 25,
        category = null,
        account = if (onCard == null) wallet else null,
        creditCard = onCard,
        createdAt = 0,
    )

    private fun invoice(
        id: Long,
        creditCard: CreditCard = card,
        dimensionId: Long? = id,
        dueMonth: YearMonth = march,
        status: Invoice.Status = Invoice.Status.CLOSED,
    ) = Invoice(
        id = id,
        creditCard = creditCard,
        dimensionId = dimensionId,
        openingMonth = dueMonth.plus(-2, DateTimeUnit.MONTH),
        closingMonth = dueMonth.plus(-1, DateTimeUnit.MONTH),
        dueMonth = dueMonth,
        status = status,
    )

    private suspend fun settlement(
        unhandledRecurring: List<Recurring> = emptyList(),
        invoicesToSettle: List<Invoice> = emptyList(),
        entryRepository: IEntryRepository = SettlementEntryRepository(),
        config: Map<String, String> = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "false"),
    ) = builder(entryRepository).build(
        key = DashboardComponentType.MONTH_SETTLEMENT.key,
        input = DashboardComponentsInput(
            transactions = emptyList(),
            creditCards = emptyList(),
            invoicesByCreditCardId = emptyMap(),
            invoicesToSettle = invoicesToSettle,
            accounts = emptyList(),
            budgets = emptyList(),
            recurringList = emptyList(),
            occurrences = emptyList(),
            today = today,
            targetMonth = march,
        ),
        context = DashboardBuilderContext(
            pendingRecurring = emptyList(),
            unhandledRecurring = unhandledRecurring,
        ),
        config = config,
    ) as? DashboardComponent.MonthSettlement

    // --- Sources and classes ---------------------------------------------------------

    @Test
    fun `an income template feeds what is coming in`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.INCOME, 3_500.0)),
        )

        assertNotNull(component)
        assertEquals(3_500.0, component.incoming.value)
        assertEquals(0.0, component.outgoing.value)
    }

    @Test
    fun `an expense template feeds what is going out`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 120.0)),
        )

        assertNotNull(component)
        assertEquals(0.0, component.incoming.value)
        assertEquals(120.0, component.outgoing.value)
    }

    /** An invoice has no income counterpart, so it feeds one class and only one. */
    @Test
    fun `an invoice feeds only what is going out`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(3_000.0))),
        )

        assertNotNull(component)
        assertEquals(0.0, component.incoming.value)
        assertEquals(3_000.0, component.outgoing.value)
    }

    /**
     * A template due later in the month is as unsettled as one already past its day: this
     * widget's perimeter is the month, and the day belongs to the pending list instead.
     */
    @Test
    fun `a template whose day has not come yet is in the figure`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 90.0)),
        )

        assertNotNull(component)
        assertEquals(90.0, component.outgoing.value)
    }

    /** Nothing crosses the two sources, so nothing is subtracted between them. */
    @Test
    fun `a card template and that card's invoice are both summed whole`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 50.0, onCard = card)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
        )

        assertNotNull(component)
        assertEquals(1_050.0, component.outgoing.value)
    }

    // --- Invariance under confirmation ------------------------------------------------

    /**
     * Confirming a card recurring writes into the invoice of that very due month, so the
     * value leaves the first source and enters the second. The figure is the same before
     * and after, and it has to be: no money moved.
     */
    @Test
    fun `confirming a card template does not move the total`() = runTest {
        val before = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 50.0, onCard = card)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
        )
        val after = settlement(
            unhandledRecurring = emptyList(),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_050.0))),
        )

        assertNotNull(before)
        assertNotNull(after)
        assertEquals(before.outgoing.value, after.outgoing.value)
    }

    /** An account recurring settles for real, and the figure drops by what left. */
    @Test
    fun `confirming an account template reduces the total`() = runTest {
        val before = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 200.0)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
        )
        val after = settlement(
            unhandledRecurring = emptyList(),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
        )

        assertNotNull(before)
        assertNotNull(after)
        assertEquals(200.0, before.outgoing.value - after.outgoing.value)
    }

    /**
     * A closed invoice takes no new expense, so confirming the template lands the value in
     * the invoice that falls due later — and that one is outside the window by the very
     * cut that defines the window. The value leaves the figure, and the figure is right to
     * let it: it settles in a month this one does not answer for.
     *
     * The April invoice below owes the confirmed 50 in the ledger and is absent from
     * `invoicesToSettle`, which is exactly what the perimeter read does with it.
     */
    @Test
    fun `a confirmation pushed into a later invoice leaves the window`() = runTest {
        val before = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 50.0, onCard = card)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
        )
        val after = settlement(
            unhandledRecurring = emptyList(),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(
                mapOf(7L to brl(1_000.0), 8L to brl(50.0)),
            ),
        )

        assertNotNull(before)
        assertNotNull(after)
        assertEquals(1_050.0, before.outgoing.value)
        assertEquals(1_000.0, after.outgoing.value)
    }

    /**
     * An instalment falling due this month was posted into this month's invoice when the
     * purchase was made, so it reaches the figure through the invoice and by no other
     * route — the widget has no instalment source to count it a second time.
     */
    @Test
    fun `an instalment of the month is counted once, inside its invoice`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(450.0))),
        )

        assertNotNull(component)
        assertEquals(450.0, component.outgoing.value)
    }

    /**
     * The perimeter read lets `RETROACTIVE` through deliberately, and this is the other
     * half of that decision: the builder asks the ledger what the invoice owes and reads
     * the answer, so one carrying no balance adds nothing to either class — it is in the
     * perimeter and contributes zero, which is not the same as being kept out.
     */
    @Test
    fun `a retroactive invoice with no balance leaves both classes alone`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.INCOME, 500.0)),
            invoicesToSettle = listOf(
                invoice(id = 7),
                invoice(id = 8, status = Invoice.Status.RETROACTIVE),
            ),
            entryRepository = SettlementEntryRepository(
                mapOf(7L to brl(1_000.0), 8L to brl(0.0)),
            ),
        )

        assertNotNull(component)
        assertEquals(500.0, component.incoming.value)
        assertEquals(1_000.0, component.outgoing.value)
    }

    // --- Currency ---------------------------------------------------------------------

    @Test
    fun `two currencies are each summed with their own`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7), invoice(id = 8, creditCard = euroCard)),
            entryRepository = SettlementEntryRepository(
                mapOf(7L to brl(300.0), 8L to MoneyByCurrency.of("EUR", 40.0)),
            ),
        )

        assertNotNull(component)
        assertEquals(
            mapOf("BRL" to 300.0, "EUR" to 40.0),
            component.outgoing.terms.associate { it.currency to it.value },
        )
    }

    /**
     * Credit on one card is not a discount on another card's debt: the two invoices below
     * are of different cards in the same currency, so nothing but the per-invoice floor
     * stands between the credit and the debt. The Nubank bill is going out whole.
     */
    @Test
    fun `a credit balance is floored per invoice and never offsets another card`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7), invoice(id = 8, creditCard = otherCard)),
            entryRepository = SettlementEntryRepository(
                mapOf(7L to brl(1_000.0), 8L to brl(-250.0)),
            ),
        )

        assertNotNull(component)
        assertEquals(1_000.0, component.outgoing.value)
    }

    @Test
    fun `many invoices cost a single read`() = runTest {
        val entryRepository = SettlementEntryRepository(
            mapOf(7L to brl(100.0), 8L to brl(200.0), 9L to MoneyByCurrency.of("EUR", 30.0)),
        )

        settlement(
            invoicesToSettle = listOf(
                invoice(id = 7),
                invoice(id = 8, dueMonth = february),
                invoice(id = 9, creditCard = euroCard),
            ),
            entryRepository = entryRepository,
        )

        assertEquals(1, entryRepository.owedReads)
    }

    /** A row from before v10 carries no dimension, so nothing of it can be summed. */
    @Test
    fun `an invoice with no dimension contributes nothing`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7, dimensionId = null)),
        )

        assertNotNull(component)
        assertEquals(0.0, component.outgoing.value)
    }

    // --- Configuration ----------------------------------------------------------------

    @Test
    fun `both sources are on by default`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 120.0)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
            config = DashboardComponentType.MONTH_SETTLEMENT.defaultConfig,
        )

        assertNotNull(component)
        assertEquals(1_120.0, component.outgoing.value)
    }

    @Test
    fun `turning the invoice source off leaves only the templates`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 120.0)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
            config = mapOf(MonthSettlementConfig.INCLUDE_INVOICES to "false"),
        )

        assertNotNull(component)
        assertEquals(120.0, component.outgoing.value)
    }

    /** A class emptied by configuration is still a class, and it reads zero. */
    @Test
    fun `with only the invoice source the incoming class still reads zero`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.INCOME, 3_500.0)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
            config = mapOf(MonthSettlementConfig.INCLUDE_RECURRING to "false"),
        )

        assertNotNull(component)
        assertEquals(0.0, component.incoming.value)
        assertEquals(1_000.0, component.outgoing.value)
    }

    /**
     * The widget being configured cannot vanish under the user's hands: an empty
     * **perimeter** is not empty **data**, and only the latter is what hiding is about.
     */
    @Test
    fun `with both sources off the widget reads zero and stays`() = runTest {
        val component = settlement(
            unhandledRecurring = listOf(recurring(1, TransactionType.EXPENSE, 120.0)),
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
            config = mapOf(
                MonthSettlementConfig.INCLUDE_RECURRING to "false",
                MonthSettlementConfig.INCLUDE_INVOICES to "false",
                DashboardComponentConfig.HIDE_WHEN_EMPTY to "true",
            ),
        )

        assertNotNull(component)
        assertEquals(0.0, component.incoming.value)
        assertEquals(0.0, component.outgoing.value)
    }

    @Test
    fun `hiding when empty still hides a month with nothing to settle`() = runTest {
        assertNull(
            settlement(config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true")),
        )
    }

    @Test
    fun `hiding when empty does not touch a month with something to settle`() = runTest {
        val component = settlement(
            invoicesToSettle = listOf(invoice(id = 7)),
            entryRepository = SettlementEntryRepository(mapOf(7L to brl(1_000.0))),
            config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
        )

        assertNotNull(component)
        assertEquals(1_000.0, component.outgoing.value)
    }

    // --- The header -------------------------------------------------------------------

    /** Like the other flow widgets, it opens with no caption over the two cards. */
    @Test
    fun `the header is off by default`() {
        assertFalse(DashboardComponentType.MONTH_SETTLEMENT.defaultConfig.showHeader())
        // A preference saved without the key reads the widget's own default, not `true`.
        assertFalse(emptyMap<String, String>().showHeader(DashboardComponentType.MONTH_SETTLEMENT.key))
    }

    @Test
    fun `the header is one preference away`() {
        assertTrue(
            mapOf(DashboardComponentConfig.SHOW_HEADER to "true")
                .showHeader(DashboardComponentType.MONTH_SETTLEMENT.key),
        )
    }

    private fun brl(value: Double) = MoneyByCurrency.of("BRL", value)
}

/**
 * Answers the one read this widget makes of the ledger, and counts how often it is asked:
 * the owed of N invoices is a single grouped query, never one per invoice.
 */
private class SettlementEntryRepository(
    private val owed: Map<Long, MoneyByCurrency> = emptyMap(),
) : IEntryRepository {

    var owedReads = 0
        private set

    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> {
        owedReads++
        return owed.filterKeys { it in dimensionIds }
    }

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun accountBalanceUpTo(accountId: Long, target: LocalDate): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionMonthlySeriesByCurrency(dimensionId: Long, upTo: YearMonth): Map<YearMonth, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun netWorthByCurrency(): MoneyByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInMonthByCurrency(
        month: YearMonth,
        nominalType: AccountType,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}
