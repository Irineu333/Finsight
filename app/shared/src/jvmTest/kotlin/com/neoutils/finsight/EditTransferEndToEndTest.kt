package com.neoutils.finsight

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.extension.deriveTransactionLabel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Correcting a transfer end to end, through the real graph — and the crossing is the
 * case that exercises everything at once: four legs rewritten, `Σ = 0` per currency
 * re-established by the boundary, two balances moved, and a rate observed again.
 *
 * The correction **keeps the operation**. That is what separates it from deleting and
 * registering another, and it is the whole reason this change exists.
 *
 * The archive is where the second half of the design shows: a rate is an observation
 * about a day, not a property of the operation, so the correction writes one and revokes
 * none. Same pair, same date and same origin is the same key, and the archive replaces
 * the row by itself; move the date and the earlier observation simply stays where it was.
 */
class EditTransferEndToEndTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)
    private val earlier = LocalDate(2026, 3, 10)

    private suspend fun AppLedgerHarness.derivedRates(): List<ExchangeRate> =
        get<IExchangeRateRepository>().observeAll().first()
            .filter { it.source == ExchangeRate.Source.DERIVED }

    @Test
    fun `correcting a cross-currency transfer keeps the operation and replaces its rate`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 1_000.0, date = day)

            get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 550.0,
                date = day,
                destinationAmount = 100.0,
            ).onLeft { error("the cross-currency transfer was refused: $it") }

            val original = transactions.getAllTransactions()
                .single { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } }
            val originalLegIds = entries.getEntriesByTransaction(original.id).map { it.id }.toSet()

            get<UpdateTransferUseCase>()(
                transactionId = original.id,
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 600.0,
                date = day,
                title = null,
                destinationAmount = 110.0,
            ).onLeft { error("the correction was refused: $it") }

            // The identity survives: one operation, the same one, not a second.
            assertEquals(
                1,
                transactions.getAllTransactions()
                    .count { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } },
                "correcting must not leave a second operation behind",
            )
            val corrected = requireNotNull(transactions.getTransactionById(original.id))
            assertEquals(original.id, corrected.id)

            val legs = entries.getEntriesByTransaction(corrected.id)
            assertEquals(4, legs.size, "two legs of the user and one conversion leg per currency")
            assertTrue(
                legs.none { it.id in originalLegIds },
                "the legs are rewritten, not amended in place",
            )
            legs.groupBy { it.currency }.forEach { (currency, group) ->
                assertEquals(0L, group.sumOf { it.amount }, "the $currency side does not sum to zero")
            }
            // Still a transfer: the conversion legs never change what the operation is.
            assertEquals(TransactionLabel.TRANSFER, legs.deriveTransactionLabel())

            // Both ends read the corrected figures, each in its own currency.
            val balances = get<CalculateBalanceUseCase>()
            assertEquals(400.0, balances.forAccount(nubank.id, march))
            assertEquals(110.0, balances.forAccount(chase.id, march))

            // Same pair, same date, same origin — the archive replaces the row by
            // itself, so there are not two derived observations competing over one day.
            val derived = derivedRates()
            assertEquals(1, derived.size, "the correction duplicated the observation")
            val rate = requireNotNull(get<IExchangeRateRepository>().rateAsOf("USD", day))
            assertEquals(600.0 / 110.0, rate.rate, absoluteTolerance = 1e-9)
            assertEquals(ExchangeRate.Source.DERIVED, rate.source)
        }

    @Test
    fun `correcting the date leaves the earlier observation standing on the earlier day`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 1_000.0, date = earlier)

            get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 550.0,
                date = day,
                destinationAmount = 100.0,
            ).onLeft { error("the cross-currency transfer was refused: $it") }

            val original = transactions.getAllTransactions()
                .single { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } }

            get<UpdateTransferUseCase>()(
                transactionId = original.id,
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 550.0,
                date = earlier,
                title = null,
                destinationAmount = 110.0,
            ).onLeft { error("the correction was refused: $it") }

            assertEquals(earlier, requireNotNull(transactions.getTransactionById(original.id)).date)

            // A different date is a different key, so the archive now holds two
            // observations — and the earlier operation's is *not* the one revoked,
            // because a correction revokes nothing.
            val archive = get<IExchangeRateRepository>()
            assertEquals(2, derivedRates().size)
            assertEquals(
                5.5,
                requireNotNull(archive.rateAsOf("USD", day)).rate,
                absoluteTolerance = 1e-9,
            )
            assertEquals(
                5.0,
                requireNotNull(archive.rateAsOf("USD", earlier)).rate,
                absoluteTolerance = 1e-9,
            )
        }

    @Test
    fun `the correction writes the title the form shows, emptied included`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val savings = account("Poupança", currency = "BRL")
            income(nubank, amount = 1_000.0, date = day)

            val registered = get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = nubank.id,
                destinationAccountId = savings.id,
                amount = 200.0,
                date = day,
                title = "Reserva de emergência",
            ).getOrNull() ?: error("the transfer was refused")

            assertEquals(
                "Reserva de emergência",
                requireNotNull(transactions.getTransactionById(registered.id)).title,
                "a title stated on registration must reach the ledger",
            )

            get<UpdateTransferUseCase>()(
                transactionId = registered.id,
                sourceAccountId = nubank.id,
                destinationAccountId = savings.id,
                amount = 200.0,
                date = day,
                title = "Acerto com o Pedro",
            ).onLeft { error("the correction was refused: $it") }

            assertEquals(
                "Acerto com o Pedro",
                requireNotNull(transactions.getTransactionById(registered.id)).title,
            )

            // The field is on screen, so clearing it is a statement about the operation
            // and not a value the form failed to carry over.
            get<UpdateTransferUseCase>()(
                transactionId = registered.id,
                sourceAccountId = nubank.id,
                destinationAccountId = savings.id,
                amount = 200.0,
                date = day,
                title = null,
            ).onLeft { error("the correction was refused: $it") }

            assertEquals(
                null,
                requireNotNull(transactions.getTransactionById(registered.id)).title,
            )
            // Still one operation, and still a transfer: naming it changed nothing else.
            assertEquals(
                TransactionLabel.TRANSFER,
                entries.getEntriesByTransaction(registered.id).deriveTransactionLabel(),
            )
        }
}
