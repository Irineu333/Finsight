@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.model.AccountType
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import kotlin.test.assertEquals
import com.neoutils.finsight.testing.FakeCardAccountRepository

/**
 * Characterizes the per-invoice sums of [InvoiceTransactionsViewModel] (sites
 * :102,106,110): expense/advancePayment/adjustment of the card legs, and the owed
 * total read from the ledger (`dimensionOwed`). Task 4.11 flips the sums to the ledger;
 * the numbers must survive.
 */
class InvoiceTransactionsViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1, creditCard = card, dimensionId = 1,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val contraAccount = Account(id = 20, name = "Contra", type = AccountType.EXPENSE, currency = "BRL")

    /** The card's LIABILITY leg — the only one carrying the invoice — plus its contra leg. */
    private fun op(id: Long, type: TransactionType, amount: Double): Transaction {
        val cents = (amount * 100).toLong()
        val signed = if (type == TransactionType.EXPENSE) -cents else cents
        return Transaction(
            id = id,
            title = null,
            date = LocalDate(2026, 3, 10),
            entries = listOf(
                Entry(transactionId = id, account = cardAccount, amount = signed, dimensionId = invoice.dimensionId),
                Entry(transactionId = id, account = contraAccount, amount = -signed),
            ),
        )
    }

    @Test
    fun `invoice summary characterizes the card leg sums and owed total`() = runTest(dispatcher) {
        val transactions = listOf(
            op(1, TransactionType.EXPENSE, 60.0),
            op(2, TransactionType.EXPENSE, 40.0),
            op(3, TransactionType.ADJUSTMENT, 10.0),
            op(4, TransactionType.INCOME, 30.0), // advance payment
        )
        val vm = InvoiceTransactionsViewModel(
            creditCardId = 1,
            creditCardRepository = FakeCreditCardRepository(card),
            accountRepository = FakeCardAccountRepository(),
            invoiceRepository = FakeInvoiceRepository(listOf(invoice)),
            transactionRepository = FakeTransactionRepository(transactions),
            categoryRepository = FakeCategoryRepository(),
            installmentRepository = NoInstallments,
            entryRepository = FakeEntryRepository(
                owedByInvoiceId = mapOf(1L to 70.0),
                flowsByInvoiceId = mapOf(
                    1L to brlFlows(expense = 100.0, advancePayment = 30.0, adjustment = 10.0),
                ),
            ),
            recurringRepository = NoRecurring,
            unarchiveCreditCard = UnarchiveCreditCardUseCase(FakeCreditCardRepository(card)),
            crashlytics = NoCrashlytics,
            clock = Clock.System,
        )

        vm.uiState.test {
            var summary = awaitItem().invoices.firstOrNull()
            while (summary == null) summary = awaitItem().invoices.firstOrNull()
            assertEquals(-100.0, summary.expense.value)
            assertEquals(30.0, summary.advancePayment.value)
            assertEquals(10.0, summary.adjustment.value)
            // Positive-as-debt, and NATURAL rather than OWED: `owedByDimension` already
            // inverted it, and OWED would zero a total that is already positive — taking
            // the payment modal's pre-filled amount down with it.
            assertEquals(70.0, summary.total.value, "owed comes from the ledger's dimensionOwed")
            assertEquals(SignPolicy.NATURAL, summary.total.policy)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
