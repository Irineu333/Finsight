@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.RecordingAnalytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCaseImpl
import com.neoutils.finsight.testing.FakeCardAccountRepository
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsUiState.ListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What the deletion confirmation is told an invoice takes with it.
 *
 * Deleting an invoice removes **every transaction posted to it**, and the sheet states how
 * many. The number is the invoice's own and not the list the screen happens to be showing:
 * a chip left on would otherwise make the sheet promise to remove three transactions and
 * remove eight. That is the whole of what is pinned here — the count travels from the
 * screen to the sheet, so it is asserted where it is produced.
 */
class InvoiceDeletionCountTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(
        id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15, accountId = 10,
    )

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val checking = Account(id = 30, name = "Checking", type = AccountType.ASSET, currency = "BRL")
    private val expenseAccount = Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    /** Retroactive, so it is deletable — and so it is the case the sheet used to call future. */
    private val retroactive = Invoice(
        id = 1, creditCard = card, dimensionId = 1,
        openingMonth = YearMonth(2026, 3),
        closingMonth = YearMonth(2026, 4),
        dueMonth = YearMonth(2026, 5),
        status = Invoice.Status.RETROACTIVE,
    )

    private val open = Invoice(
        id = 2, creditCard = card, dimensionId = 2,
        openingMonth = YearMonth(2026, 4),
        closingMonth = YearMonth(2026, 5),
        dueMonth = YearMonth(2026, 6),
        status = Invoice.Status.OPEN,
    )

    private fun purchase(id: Long, dimensionId: Long, day: Int) = Transaction(
        id = id, title = "Purchase $id", date = LocalDate(2026, 3, day),
        entries = listOf(
            Entry(transactionId = id, account = cardAccount, amount = -6_000, dimensionId = dimensionId),
            Entry(transactionId = id, account = expenseAccount, amount = 6_000, dimensionId = 77),
        ),
    )

    /** Money leaves the account and lands on the card — an income, from the card's side. */
    private val payment = Transaction(
        id = 3, title = "Payment", date = LocalDate(2026, 3, 20),
        entries = listOf(
            Entry(transactionId = 3, account = checking, amount = -6_000),
            Entry(transactionId = 3, account = cardAccount, amount = 6_000, dimensionId = 1),
        ),
    )

    private val transactions = listOf(
        purchase(id = 1, dimensionId = 1, day = 10),
        payment,
        // The next invoice's own, on the same card and in the same observed list.
        purchase(id = 4, dimensionId = 2, day = 25),
    )

    private fun viewModel() = InvoiceTransactionsViewModel(
        creditCardId = 1,
        creditCardRepository = FakeCreditCardRepository(card),
        accountRepository = FakeCardAccountRepository(),
        invoiceRepository = FakeInvoiceRepository(listOf(retroactive, open)),
        transactionRepository = FakeTransactionRepository(transactions),
        categoryRepository = FakeCategoryRepository(),
        installmentRepository = NoInstallments,
        entryRepository = FakeEntryRepository(
            owedByInvoiceId = mapOf(1L to 0.0, 2L to 0.0),
            flowsByInvoiceId = mapOf(
                1L to DimensionFlowsByCurrency.zero,
                2L to DimensionFlowsByCurrency.zero,
            ),
        ),
        recurringRepository = NoRecurring,
        unarchiveCreditCard = UnarchiveCreditCardUseCaseImpl(FakeCreditCardRepository(card)),
        analytics = RecordingAnalytics(),
        crashlytics = NoCrashlytics,
        clock = Clock.System,
    )

    /**
     * Drives [actions] and returns the first state that [settled] accepts.
     *
     * The predicate rather than one item per action: this card has two invoices, so the
     * screen picks the open one by itself once the repository answers, and a test counting
     * emissions would read whichever state that race left behind.
     */
    private suspend fun stateWhere(
        vararg actions: InvoiceTransactionsAction,
        settled: (InvoiceTransactionsUiState) -> Boolean,
    ): InvoiceTransactionsUiState {
        val vm = viewModel()
        var result = InvoiceTransactionsUiState()
        vm.uiState.test {
            actions.forEach(vm::onAction)

            var state = awaitItem()
            while (state.listState is ListState.Loading || !settled(state)) state = awaitItem()

            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `each invoice counts the transactions posted to it, and no others`() =
        runTest(dispatcher) {
            val invoices = stateWhere { it.invoices.size == 2 }.invoices

            assertEquals(2, invoices.first { it.invoiceId == 1L }.transactionCount)
            assertEquals(1, invoices.first { it.invoiceId == 2L }.transactionCount)
        }

    /**
     * The number the sheet states is what the deletion removes, and a filter removes
     * nothing. Filtering to income leaves one row on screen out of the invoice's two.
     */
    @Test
    fun `a filter cuts the list and never the count`() = runTest(dispatcher) {
        val state = stateWhere(
            // Page 0 is the retroactive invoice — the deletable one. The screen opens on
            // the open invoice by itself.
            InvoiceTransactionsAction.SelectInvoice(0),
            InvoiceTransactionsAction.SelectType(TransactionType.INCOME),
        ) {
            it.selectedInvoiceIndex == 0 && it.selectedType == TransactionType.INCOME
        }

        val listed = assertIs<ListState.Content>(state.listState).transactions.values.flatten()
        assertEquals(listOf(payment.id), listed.map { it.id }, "the filter did cut the list")
        assertEquals(
            2,
            state.invoices.first { it.invoiceId == 1L }.transactionCount,
            "the deletion still takes both, and the sheet must say both",
        )
    }
}
