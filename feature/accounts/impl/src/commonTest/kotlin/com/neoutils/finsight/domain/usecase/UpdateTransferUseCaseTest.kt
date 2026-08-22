@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Correcting a transfer in place.
 *
 * The refusals are not restated as rules here — they belong to
 * [ValidateTransferUseCase], and `ValidateTransferUseCaseTest` is where they are
 * enumerated. What these prove is that the correction consumes the *same* owner, so
 * every refusal the creation gives, the correction gives too.
 */
class UpdateTransferUseCaseTest {

    private val today = LocalDate(2026, 3, 10)

    private val nubank = Account(id = 1, name = "Nubank", currency = "BRL")
    private val inter = Account(id = 2, name = "Inter", currency = "BRL")
    private val chase = Account(id = 3, name = "Chase", currency = "USD")

    private val accounts = listOf(nubank, inter, chase)

    private val transfer = Transaction(
        id = 42,
        title = null,
        date = today,
        entries = listOf(
            Entry(transactionId = 42, account = nubank, amount = -5000),
            Entry(transactionId = 42, account = inter, amount = 5000),
        ),
    )

    private fun useCase(
        transactions: RewriteRecordingTransactions,
        rates: RecordingRates = RecordingRates(),
    ) = UpdateTransferUseCase(
        transactionRepository = transactions,
        validateTransfer = ValidateTransferUseCase(
            accountRepository = StaticAccounts(accounts),
            clock = ClockOn(today),
        ),
        harvestExchangeRate = HarvestExchangeRateUseCase(rates),
    )

    @Test
    fun `correcting the amount rewrites both legs of the operation`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        val result = useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 80.0,
            date = today,
        )

        assertTrue(result.isRight())
        val rewrite = transactions.rewrites.single()
        assertEquals(transfer.id, rewrite.id)
        assertEquals(2, rewrite.legs.size)
        assertEquals(TransactionType.EXPENSE, rewrite.legs[0].type)
        assertEquals(80.0, rewrite.legs[0].amount)
        assertEquals(nubank.id, rewrite.legs[0].accountId)
        assertEquals(TransactionType.INCOME, rewrite.legs[1].type)
        assertEquals(80.0, rewrite.legs[1].amount)
        assertEquals(inter.id, rewrite.legs[1].accountId)
        // Two legs balance on their own; a contra would be a third side.
        assertEquals(null, rewrite.contra)
    }

    @Test
    fun `correcting the destination account points the incoming leg elsewhere`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = chase.id,
            amount = 550.0,
            date = today,
            destinationAmount = 100.0,
        )

        val rewrite = transactions.rewrites.single()
        assertEquals(chase.id, rewrite.legs[1].accountId)
        assertEquals(100.0, rewrite.legs[1].amount)
    }

    @Test
    fun `correcting the date rewrites the row on the new date`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 50.0,
            date = LocalDate(2026, 3, 1),
        )

        assertEquals(LocalDate(2026, 3, 1), transactions.rewrites.single().date)
    }

    @Test
    fun `a correction that crosses currencies states both ends and harvests the rate`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))
        val rates = RecordingRates()

        useCase(transactions, rates)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = chase.id,
            amount = 550.0,
            date = today,
            destinationAmount = 100.0,
        )

        val rewrite = transactions.rewrites.single()
        assertEquals(550.0, rewrite.legs[0].amount)
        assertEquals(100.0, rewrite.legs[1].amount)

        val harvested = rates.saved.single()
        assertEquals("BRL", harvested.currency)
        assertEquals("USD", harvested.counterCurrency)
        assertEquals(today, harvested.date)
        assertEquals(ExchangeRate.Source.DERIVED, harvested.source)
        assertTrue(rates.removed.isEmpty(), "a correction revokes no observation")
    }

    @Test
    fun `a correction preserves the title the form never showed`() = runTest {
        val titled = transfer.copy(title = "Rent, moved across")
        val transactions = RewriteRecordingTransactions(listOf(titled))

        useCase(transactions)(
            transactionId = titled.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 80.0,
            date = today,
        )

        assertEquals("Rent, moved across", transactions.rewrites.single().title)
    }

    @Test
    fun `correcting a cross-currency transfer back to one currency neither harvests nor revokes`() = runTest {
        val previouslyHarvested = ExchangeRate(
            currency = "BRL",
            counterCurrency = "USD",
            date = today,
            rate = 0.18,
            source = ExchangeRate.Source.DERIVED,
        )
        val crossCurrency = transfer.copy(
            entries = listOf(
                Entry(transactionId = 42, account = nubank, amount = -55000),
                Entry(transactionId = 42, account = chase, amount = 10000),
            ),
        )
        val transactions = RewriteRecordingTransactions(listOf(crossCurrency))
        val rates = RecordingRates(existing = listOf(previouslyHarvested))

        useCase(transactions, rates)(
            transactionId = crossCurrency.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 550.0,
            date = today,
        )

        assertEquals(1, transactions.rewrites.size)
        assertTrue(rates.saved.isEmpty(), "there is no crossing left to observe")
        assertTrue(rates.removed.isEmpty(), "and the observation it had made stays in the archive")
    }

    // --- The refusals: the same owner, so the same answers as the creation gives ---

    @Test
    fun `a correction to zero is refused, and nothing is written`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        val error = useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 0.0,
            date = today,
        ).leftOrNull()

        assertEquals(TransferError.InvalidAmount, error?.error)
        assertTrue(transactions.rewrites.isEmpty())
    }

    @Test
    fun `a correction with a destination amount of zero is refused`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        val error = useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = chase.id,
            amount = 550.0,
            date = today,
            destinationAmount = 0.0,
        ).leftOrNull()

        assertEquals(TransferError.InvalidAmount, error?.error)
        assertTrue(transactions.rewrites.isEmpty())
    }

    @Test
    fun `a correction pointing both ends at one account is refused`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        val error = useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = nubank.id,
            amount = 80.0,
            date = today,
        ).leftOrNull()

        assertEquals(TransferError.SameAccount, error?.error)
        assertTrue(transactions.rewrites.isEmpty())
    }

    @Test
    fun `a correction to a future date is refused`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        val error = useCase(transactions)(
            transactionId = transfer.id,
            sourceAccountId = nubank.id,
            destinationAccountId = inter.id,
            amount = 80.0,
            date = LocalDate(2026, 3, 11),
        ).leftOrNull()

        assertEquals(TransferError.FutureDate, error?.error)
        assertTrue(transactions.rewrites.isEmpty())
    }

    @Test
    fun `a correction naming an account that does not exist is refused`() = runTest {
        val transactions = RewriteRecordingTransactions(listOf(transfer))

        assertEquals(
            TransferError.SourceAccountNotFound,
            useCase(transactions)(
                transactionId = transfer.id,
                sourceAccountId = 99,
                destinationAccountId = inter.id,
                amount = 80.0,
                date = today,
            ).leftOrNull()?.error,
        )
        assertEquals(
            TransferError.DestinationAccountNotFound,
            useCase(transactions)(
                transactionId = transfer.id,
                sourceAccountId = nubank.id,
                destinationAccountId = 99,
                amount = 80.0,
                date = today,
            ).leftOrNull()?.error,
        )
        assertTrue(transactions.rewrites.isEmpty())
    }
}
