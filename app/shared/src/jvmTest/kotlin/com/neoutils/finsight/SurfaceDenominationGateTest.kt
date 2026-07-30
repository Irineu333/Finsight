@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import androidx.room.Room
import androidx.room.RoomDatabase
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.InvoiceEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.LAST_RESORT_CURRENCY
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.ui.component.InstallmentState
import com.neoutils.finsight.ui.screen.accounts.AccountsUiState
import com.neoutils.finsight.ui.screen.accounts.AccountsViewModel
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The surface-level repeat of `CurrencyDenominationGateTest` (task 3.10). That one proves the
 * *reads* carry the currency of their own account; this one proves the figures a user actually
 * looks at do too — the account card, the statement list, the invoice modal and the instalment
 * counter — over the real Koin graph and a real database.
 *
 * The two are not redundant. A read can be right and its surface still hand the formatter the
 * base currency, and for a single-currency user the two texts coincide, so nothing would show.
 * With every account in a currency that is **not** the base, the mistake becomes visible: the
 * assertion is that each figure carries the account's or the card's own currency, and that the
 * base's symbol appears in none of them.
 */
class SurfaceDenominationGateTest {

    // `Unconfined` rather than a scheduled dispatcher: the figures come from Room, whose
    // queries run on their own context, so there is no virtual time to advance — only real
    // reads to wait for. A scheduled main dispatcher just adds a second clock to the wait.
    private val dispatcher = UnconfinedTestDispatcher()

    private val file: File = File.createTempFile("finsight-surface", ".db").also { it.delete() }

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    private var koin: Koin? = null

    /**
     * The container is closed, and that is not tidiness: Koin caches a `single` inside the
     * `Module` object itself, and `appModules` is one global list of them. Leaving a
     * container open leaves those instances behind, so the next test in the same JVM gets
     * the previous test's database — its DAOs already resolved against a file that is gone.
     */
    @AfterTest
    fun tearDown() {
        koin?.close()
        Dispatchers.resetMain()
        file.delete()
    }

    private val formatter = CurrencyFormatter()

    /**
     * One test and not four, deliberately. The claim is conjunctive — *no* figure of these
     * surfaces spells the base — and four tests would be four claims. It also keeps the whole
     * gate on one seeded container: `appModules` is a global list of Koin `Module`s that cache
     * their singletons inside themselves, so a container per test is a container per test
     * racing over the same cache.
     */
    @Test
    fun `every surface reads in the account's or the card's own currency`() = runBlockingTest {
        val koin = seeded()

        assertAccountCard(koin)
        assertStatementList(koin)
        assertInvoiceModal(koin)
        assertInstalmentCounter(koin)
    }

    private suspend fun assertAccountCard(koin: Koin) {
        val viewModel = koin.get<AccountsViewModel> { parametersOf(null) }

        // Waits for the state that holds the seeded account rather than for the first
        // `Content`: the screen combines the account list with a ledger-change signal, so the
        // first content it emits is not guaranteed to be the one with the reads in it.
        val content = viewModel.uiState
            .first { it is AccountsUiState.Content && it.accounts.any { card -> card.id == ACCOUNT_ID } }
                as AccountsUiState.Content
        val card = content.accounts.single { it.id == ACCOUNT_ID }

        listOf(
            "opening balance" to card.openingBalance,
            "balance" to card.balance,
            "income" to card.income,
            "expense" to card.expense,
            "adjustment" to card.adjustment,
            "settlement" to card.settlement,
        ).forEach { (figure, amount) -> assertDenominatedInForeign(figure, amount) }
    }

    private suspend fun assertStatementList(koin: Koin) {
        val viewModel = koin.get<AccountsViewModel> { parametersOf(ACCOUNT_ID) }

        val content = viewModel.uiState
            .first { it is AccountsUiState.Content && it.listState is AccountsUiState.ListState.Content }
                as AccountsUiState.Content
        val items = (content.listState as AccountsUiState.ListState.Content)
            .transactions.values.flatten()

        assertTrue(items.isNotEmpty(), "the seed posts on this account, so the list is not empty")
        items.forEach { assertDenominatedInForeign("statement item ${it.id}", it.amount) }
    }

    private suspend fun assertInvoiceModal(koin: Koin) {
        val viewModel = koin.get<InvoiceTransactionsViewModel> { parametersOf(CARD_ID) }

        val summary = viewModel.uiState.first { it.invoices.isNotEmpty() }.invoices.single()

        listOf(
            "invoice expense" to summary.expense,
            "invoice advance payment" to summary.advancePayment,
            "invoice adjustment" to summary.adjustment,
            "invoice total" to summary.total,
        ).forEach { (figure, amount) -> assertDenominatedInForeign(figure, amount) }
    }

    private suspend fun assertInstalmentCounter(koin: Koin) {
        val card = koin.get<ICreditCardRepository>().getCreditCardById(CARD_ID)!!

        // An instalment plan is denominated by the card it is charged to (D17), and the card
        // mirrors the currency of its LIABILITY row — which is what the counter reads.
        assertEquals(FOREIGN, card.currency)

        val counter = InstallmentState(
            count = 3,
            total = DisplayAmount.natural(300.0, Denomination.exact(card.currency)),
        )

        val text = counter.format(formatter)
        assertTrue(text.contains(formatter.format(100.0, FOREIGN)), "counter reads $text")
        assertFalse(text.contains(baseSymbol()), "counter must not spell the base: $text")
    }

    /**
     * Waits on real reads, with a ceiling: the ViewModels observe Room, so what a test is
     * waiting for is a query coming back, not virtual time being advanced.
     */
    private fun runBlockingTest(body: suspend CoroutineScope.() -> Unit) =
        runBlocking { withTimeout(30_000) { body() } }

    /**
     * A figure is right only if it says the account's currency *and* does not say the base's.
     * The second half is the one that catches the mistake: a `DisplayAmount` built on the base
     * still formats into something perfectly readable.
     */
    private fun assertDenominatedInForeign(figure: String, amount: DisplayAmount) {
        assertEquals(FOREIGN, amount.currency, "$figure must be denominated in $FOREIGN")
        assertFalse(amount.isApproximate, "$figure has one currency, so nothing was reconciled")

        val text = formatter.format(amount)
        assertFalse(text.contains(baseSymbol()), "$figure must not spell the base currency: $text")
    }

    /** What the base would have printed, as the locale prints it. */
    private fun baseSymbol() = formatter.format(0.0, LAST_RESORT_CURRENCY).filterNot {
        it.isDigit() || it.isWhitespace() || it == '.' || it == ','
    }

    /**
     * One month of an ordinary life, wholly in a currency that is not the base: an account and
     * a card, both `USD`, with a salary, a categorised expense, a card purchase and a part
     * payment of that card. Seeded through the DAOs so that no write path has to be asked to
     * choose a currency — the second currency is born in the account form (task 9.7), and
     * until then production has exactly one.
     */
    private suspend fun seeded(): Koin {
        val koin = koinApplication { modules(appModules + temporaryDatabase(file)) }.koin
            .also { this.koin = it }
        val database = koin.get<AppDatabase>()

        val accounts = database.accountDao()
        accounts.insert(account(ACCOUNT_ID, "Bank", AccountEntity.Type.ASSET))
        accounts.insert(account(CARD_ACCOUNT_ID, "Card", AccountEntity.Type.LIABILITY))
        accounts.insert(account(INCOME_ID, "Receitas", AccountEntity.Type.INCOME))
        accounts.insert(account(EXPENSE_ID, "Despesas", AccountEntity.Type.EXPENSE))

        database.creditCardDao().insert(
            CreditCardEntity(
                id = CARD_ID,
                name = "Card",
                limit = 1_000.0,
                closingDay = 25,
                dueDay = 5,
                accountId = CARD_ACCOUNT_ID,
            )
        )

        database.dimensionDao().insert(DimensionEntity(id = CATEGORY_DIMENSION, kind = DimensionKind.CATEGORY))
        database.dimensionDao().insert(DimensionEntity(id = INVOICE_DIMENSION, kind = DimensionKind.INVOICE))

        database.invoiceDao().insert(
            InvoiceEntity(
                id = INVOICE_ID,
                creditCardId = CARD_ID,
                openingMonth = MONTH.minusMonth(),
                closingMonth = MONTH,
                dueMonth = MONTH,
                status = InvoiceEntity.Status.OPEN,
                dimensionId = INVOICE_DIMENSION,
            )
        )

        post(database, day = 5, ACCOUNT_ID to 100_000L, INCOME_ID to -100_000L)
        post(
            database, day = 8,
            ACCOUNT_ID to -10_000L,
            EXPENSE_ID to 10_000L,
            dimensionOf = mapOf(EXPENSE_ID to CATEGORY_DIMENSION),
        )
        post(
            database, day = 12,
            CARD_ACCOUNT_ID to -8_000L,
            EXPENSE_ID to 8_000L,
            dimensionOf = mapOf(CARD_ACCOUNT_ID to INVOICE_DIMENSION),
        )
        post(
            database, day = 20,
            ACCOUNT_ID to -2_000L,
            CARD_ACCOUNT_ID to 2_000L,
            dimensionOf = mapOf(CARD_ACCOUNT_ID to INVOICE_DIMENSION),
        )

        return koin
    }

    private fun account(id: Long, name: String, type: AccountEntity.Type) =
        AccountEntity(id = id, name = name, type = type, currency = FOREIGN)

    private suspend fun post(
        database: AppDatabase,
        day: Int,
        vararg legs: Pair<Long, Long>,
        dimensionOf: Map<Long, Long> = emptyMap(),
    ) {
        val transactionId = database.transactionDao().insert(
            TransactionEntity(title = null, date = LocalDate(MONTH.year, MONTH.month, day))
        )
        database.entryDao().insertAll(
            legs.map { (accountId, amount) ->
                EntryEntity(
                    transactionId = transactionId,
                    accountId = accountId,
                    amount = amount,
                    currency = FOREIGN,
                    dimensionId = dimensionOf[accountId],
                )
            }
        )
    }

    private companion object {
        // Not the base, and that is the whole point: with the two equal the violation is
        // invisible, because both texts read the same.
        const val FOREIGN = "USD"

        // The current month, because the surfaces cut by it: the accounts screen opens on
        // today's month and the invoice pager on the invoice that is open now. A fixed month
        // would leave every list legitimately empty and assert nothing.
        val MONTH: YearMonth = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonth

        const val ACCOUNT_ID = 1L
        const val CARD_ACCOUNT_ID = 2L
        const val INCOME_ID = 100L
        const val EXPENSE_ID = 101L
        const val CARD_ID = 7L
        const val INVOICE_ID = 70L
        const val CATEGORY_DIMENSION = 10L
        const val INVOICE_DIMENSION = 20L
    }
}

/**
 * Keeps the gate off the user's real desktop database file, by binding the **database**
 * rather than its builder: `databaseModule` pulls the platform builder in through
 * `includes(…)`, which Koin applies after the modules of the list, so an override of the
 * builder is itself overridden and the test opens `~/.finance/finsight.db`.
 */
private fun temporaryDatabase(file: File) = module {
    single<AppDatabase> {
        getRoomDatabase(
            Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        )
    } bind RoomDatabase::class
}
