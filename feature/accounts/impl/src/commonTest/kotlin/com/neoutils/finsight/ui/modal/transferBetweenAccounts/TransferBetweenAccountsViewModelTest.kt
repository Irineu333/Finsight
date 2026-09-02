@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.usecase.ClockOn
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.RecordingRates
import com.neoutils.finsight.domain.usecase.RewriteRecordingTransactions
import com.neoutils.finsight.domain.usecase.StaticAccounts
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransferUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * The form in its two modes: what it opens on, and which use case a submission reaches.
 *
 * The two are one test subject and not two, because the whole design is that they are
 * one form — what changes between them is the seed and the destination of the write.
 */
class TransferBetweenAccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 3, 10)

    private val nubank = Account(id = 1, name = "Nubank", currency = "BRL")
    private val inter = Account(id = 2, name = "Inter", currency = "BRL")
    private val chase = Account(id = 3, name = "Chase", currency = "USD")

    private val accounts = listOf(nubank, inter, chase)

    private val titledTransfer = Transaction(
        id = 7,
        title = "Reserva de emergência",
        date = LocalDate(2026, 3, 4),
        entries = listOf(
            Entry(transactionId = 7, account = nubank, amount = -55000),
            Entry(transactionId = 7, account = chase, amount = 10000),
        ),
    )

    private val crossCurrencyTransfer = Transaction(
        id = 42,
        title = null,
        date = LocalDate(2026, 3, 4),
        entries = listOf(
            Entry(transactionId = 42, account = nubank, amount = -55000),
            Entry(transactionId = 42, account = chase, amount = 10000),
        ),
    )

    private fun viewModel(
        transactions: RewriteRecordingTransactions,
        transaction: Transaction? = null,
        rates: RecordingRates = RecordingRates(),
    ): TransferBetweenAccountsViewModel {
        val validate = ValidateTransferUseCase(
            accountRepository = StaticAccounts(accounts),
            clock = ClockOn(today),
        )
        return TransferBetweenAccountsViewModel(
            initialSourceAccount = transaction?.entries?.first { it.amount < 0 }?.account ?: nubank,
            transaction = transaction,
            transferBetweenAccountsUseCase = TransferBetweenAccountsUseCaseImpl(
                transactionRepository = transactions,
                validateTransfer = validate,
                harvestExchangeRate = HarvestExchangeRateUseCase(rates),
            ),
            updateTransferUseCase = UpdateTransferUseCase(
                transactionRepository = transactions,
                validateTransfer = validate,
                harvestExchangeRate = HarvestExchangeRateUseCase(rates),
            ),
            suggestCrossCurrencyAmount = SuggestCrossCurrencyAmountUseCase(rates),
            accountRepository = StaticAccounts(accounts),
            clock = ClockOn(today),
            modalManager = ModalManager(),
            analytics = MuteAnalytics,
            crashlytics = MuteCrashlytics,
        )
    }

    @Test
    fun `a correction opens seeded with what the operation records`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions(listOf(crossCurrencyTransfer))

        viewModel(transactions, crossCurrencyTransfer).uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()

            assertEquals(true, state.isEditMode)
            assertEquals(nubank.id, state.selectedSourceAccount?.id)
            assertEquals(chase.id, state.selectedDestinationAccount?.id)
            assertEquals(true, state.isCrossCurrency)
        }
    }

    @Test
    fun `registering a transfer opens on the account it was reached from, with no destination`() =
        runTest(dispatcher) {
            val transactions = RewriteRecordingTransactions()

            viewModel(transactions).uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()

                assertEquals(false, state.isEditMode)
                assertEquals(nubank.id, state.selectedSourceAccount?.id)
                assertEquals(null, state.selectedDestinationAccount)
            }
        }

    @Test
    fun `submitting a correction rewrites the operation and creates nothing`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions(listOf(crossCurrencyTransfer))
        val viewModel = viewModel(transactions, crossCurrencyTransfer)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 600.0,
                    destinationAmount = 110.0,
                    date = today,
                    title = "",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val rewrite = transactions.rewrites.single()
        assertEquals(crossCurrencyTransfer.id, rewrite.id)
        assertEquals(600.0, rewrite.legs[0].amount)
        assertEquals(nubank.id, rewrite.legs[0].accountId)
        assertEquals(110.0, rewrite.legs[1].amount)
        assertEquals(chase.id, rewrite.legs[1].accountId)
        assertTrue(transactions.created.isEmpty(), "a correction must not register a second operation")
    }

    @Test
    fun `submitting a registration creates the operation and rewrites nothing`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions()
        val viewModel = viewModel(transactions)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(inter))
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 80.0,
                    destinationAmount = 0.0,
                    date = today,
                    title = "",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val intent = transactions.created.single()
        assertEquals(80.0, intent.legs[0].amount)
        assertEquals(inter.id, intent.legs[1].accountId)
        assertTrue(transactions.rewrites.isEmpty())
    }

    @Test
    fun `a registration opens on the app's clock, which is the one the rule reads`() =
        runTest(dispatcher) {
            // The archive is read as of the operation's date, and the observation seeded
            // here exists on one day only. It reaches the state only if the date the form
            // opens on is the injected clock's — the same one that bounds the picker and
            // that the future-date refusal is stated against. The system's calendar would
            // answer some other day, and the suggestion would be absent.
            val rates = RecordingRates(
                existing = listOf(
                    ExchangeRate(
                        currency = "BRL",
                        counterCurrency = "USD",
                        date = today,
                        rate = 0.2,
                        source = ExchangeRate.Source.DERIVED,
                    ),
                ),
            )
            val viewModel = viewModel(RewriteRecordingTransactions(), rates = rates)

            viewModel.uiState.test {
                advanceUntilIdle()
                viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(chase))
                viewModel.onAction(TransferBetweenAccountsAction.ChangeAmount(550.0))
                advanceUntilIdle()

                assertEquals(today, expectMostRecentItem().suggestion?.asOf)
            }
        }

    @Test
    fun `registering with a title writes it on the operation`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions()
        val viewModel = viewModel(transactions)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(inter))
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 500.0,
                    destinationAmount = 0.0,
                    date = today,
                    title = "  Reserva de emergência  ",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Trimmed, and in one place: what surrounds a name is not part of it.
        assertEquals("Reserva de emergência", transactions.created.single().title)
    }

    @Test
    fun `registering without a title leaves the operation unnamed`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions()
        val viewModel = viewModel(transactions)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(inter))
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 80.0,
                    destinationAmount = 0.0,
                    date = today,
                    title = "   ",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // Blank is an absence, not a name made of spaces.
        assertEquals(null, transactions.created.single().title)
    }

    @Test
    fun `correcting the title writes the new one`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions(listOf(titledTransfer))
        val viewModel = viewModel(transactions, titledTransfer)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 550.0,
                    destinationAmount = 100.0,
                    date = today,
                    title = "Acerto com o Pedro",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("Acerto com o Pedro", transactions.rewrites.single().title)
    }

    @Test
    fun `emptying the title on a correction removes it`() = runTest(dispatcher) {
        val transactions = RewriteRecordingTransactions(listOf(titledTransfer))
        val viewModel = viewModel(transactions, titledTransfer)

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onAction(
                TransferBetweenAccountsAction.Submit(
                    amount = 550.0,
                    destinationAmount = 100.0,
                    date = today,
                    title = "",
                )
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // The field was on screen and was cleared: that is a statement, not a loss.
        assertEquals(null, transactions.rewrites.single().title)
    }
}

private object MuteAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

private object MuteCrashlytics : Crashlytics {
    override fun recordException(e: Throwable) = Unit
    override fun setUserId(id: String?) = Unit
}
