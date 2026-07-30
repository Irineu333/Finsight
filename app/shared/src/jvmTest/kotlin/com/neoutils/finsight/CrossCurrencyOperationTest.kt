@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import androidx.room.Room
import androidx.room.RoomDatabase
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.InvoiceEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.extension.isBalanced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.core.Koin
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * An operation that crosses currencies, end to end, over the real Koin graph and a real
 * database — the verification of the whole change (task 10.1) in the two flows that reach the
 * ledger with two amounts.
 *
 * What it pins, and each of these is a rule some other layer could have broken alone:
 *
 * - the four entries of a cross-currency transfer **sum to zero in each currency**, with the
 *   residue of each landing on a `CONVERSION` account of that currency and carrying no
 *   dimension;
 * - the label stays what the operation is — `TRANSFER`, `PAYMENT` — and not `ADJUSTMENT`,
 *   which is what a conversion leg typed as `EQUITY` would have produced;
 * - the quote the operation implies is **collected**, on the operation's own date;
 * - and it **outlives** the operation (design D27): deleting the transfer leaves the rate, so
 *   a figure of that period does not move because a typo was corrected.
 *
 * The base currency is bound rather than resolved, because it is otherwise read from the
 * device's locale: a rate is only collected when one end is the base, and a machine in another
 * region would make this test assert nothing.
 */
class CrossCurrencyOperationTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val file: File = File.createTempFile("finsight-cross", ".db").also { it.delete() }

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    private var koin: Koin? = null

    /** See `SurfaceDenominationGateTest`: an open container leaks its singletons globally. */
    @AfterTest
    fun tearDown() {
        koin?.close()
        Dispatchers.resetMain()
        file.delete()
    }

    @Test
    fun `a transfer between currencies balances in each, collects its rate, and the rate outlives it`() =
        runBlockingTest {
            val koin = container()

            val local = koin.get<CreateAccountUseCase>()(
                name = "Nubank",
                isDefault = true,
                iconKey = "wallet",
                currency = BASE,
            ).getOrNull()!!

            val foreign = koin.get<CreateAccountUseCase>()(
                name = "Chase",
                isDefault = false,
                iconKey = "wallet",
                currency = FOREIGN,
            ).getOrNull()!!

            val transfer = koin.get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = local.id,
                destinationAccountId = foreign.id,
                sourceAmount = 550.0,
                destinationAmount = 100.0,
                date = DATE,
            ).getOrNull()

            assertNotNull(transfer, "a cross-currency transfer is written, not refused")

            val entries = transfer.entries
            assertEquals(4, entries.size, "two the user expressed, two the writer completed")
            assertTrue(entries.isBalanced(), "sum zero per currency, with no exception")

            val conversion = entries.filter { it.account.type == AccountType.CONVERSION }
            assertEquals(2, conversion.size, "one residue per currency")
            assertEquals(
                setOf(BASE, FOREIGN),
                conversion.mapTo(mutableSetOf()) { it.currency },
                "a conversion account per currency, not per pair",
            )
            assertTrue(
                conversion.all { it.dimensionId == null },
                "a conversion leg carries no dimension, or the landing rule would refuse it",
            )

            // The type is its own, so the adjustment predicate is untouched: `EQUITY` still
            // means exactly "adjustment", and this is a transfer.
            assertEquals(TransactionLabel.TRANSFER, entries.deriveTransactionLabel())

            val rates = koin.get<IExchangeRateRepository>()
            assertEquals(
                ExchangeRate(FOREIGN, DATE, 5.5, ExchangeRate.Source.OPERATION),
                rates.rateOn(FOREIGN, DATE),
                "550 for 100 is 5.50, on the day of the operation",
            )

            // A rate is an observation about a day, not a property of the transaction that
            // revealed it (design D27). Deleting the transfer must not move March.
            koin.get<ITransactionRepository>().deleteTransactionById(transfer.id)

            assertNull(
                koin.get<ITransactionRepository>().getTransactionById(transfer.id),
                "the operation is gone",
            )
            assertEquals(
                5.5,
                rates.rateOn(FOREIGN, DATE)?.rate,
                "and the rate it taught is still true about that day",
            )
        }

    @Test
    fun `an invoice in another currency is paid from a local account, and reads as a payment`() =
        runBlockingTest {
            val koin = container()
            val database = koin.get<AppDatabase>()

            val local = koin.get<CreateAccountUseCase>()(
                name = "Nubank",
                isDefault = true,
                iconKey = "wallet",
                currency = BASE,
            ).getOrNull()!!

            // The card's LIABILITY row is seeded in the foreign currency through the DAO: what
            // the payment flow needs is a card denominated elsewhere, and the form that chooses
            // that is covered by `CurrencyChoiceSitesTest`.
            val cardAccountId = database.accountDao().insert(
                com.neoutils.finsight.database.entity.AccountEntity(
                    name = "Chase Card",
                    type = com.neoutils.finsight.database.entity.AccountEntity.Type.LIABILITY,
                    currency = FOREIGN,
                )
            )
            val expenseAccountId = database.accountDao().insert(
                com.neoutils.finsight.database.entity.AccountEntity(
                    name = "Despesas",
                    type = com.neoutils.finsight.database.entity.AccountEntity.Type.EXPENSE,
                    currency = FOREIGN,
                )
            )
            val cardId = database.creditCardDao().insert(
                CreditCardEntity(
                    name = "Chase Card",
                    limit = 1_000.0,
                    closingDay = 25,
                    dueDay = 5,
                    accountId = cardAccountId,
                )
            )
            val dimensionId = database.dimensionDao()
                .insert(DimensionEntity(kind = DimensionKind.INVOICE))
            val invoiceId = database.invoiceDao().insert(
                InvoiceEntity(
                    creditCardId = cardId,
                    openingMonth = MONTH.minusMonth(),
                    closingMonth = MONTH,
                    // Due next month, so that paying it today is never after its due date —
                    // the day this test runs on must not decide whether it passes.
                    dueMonth = MONTH.plusMonth(),
                    status = InvoiceEntity.Status.CLOSED,
                    dimensionId = dimensionId,
                )
            )

            // A purchase of US$ 100 on the card, so the invoice owes something.
            val purchaseId = database.transactionDao()
                .insert(TransactionEntity(title = null, date = DATE))
            database.entryDao().insertAll(
                listOf(
                    EntryEntity(
                        transactionId = purchaseId,
                        accountId = cardAccountId,
                        amount = -10_000L,
                        currency = FOREIGN,
                        dimensionId = dimensionId,
                    ),
                    EntryEntity(
                        transactionId = purchaseId,
                        accountId = expenseAccountId,
                        amount = 10_000L,
                        currency = FOREIGN,
                    ),
                )
            )

            koin.get<PayInvoicePaymentUseCase>()(
                invoiceId = invoiceId,
                date = DATE,
                account = koin.get<IAccountRepository>().getAccountById(local.id)!!,
                accountAmount = 550.0,
            ).onLeft { error("a cross-currency payment is written, not refused: $it") }

            val payment = koin.get<ITransactionRepository>().getAllTransactions()
                .single { it.id != purchaseId }
            val entries = payment.entries

            assertEquals(4, entries.size)
            assertTrue(entries.isBalanced())
            assertEquals(
                TransactionLabel.PAYMENT,
                entries.deriveTransactionLabel(),
                "a payment across currencies is still a payment",
            )

            // The invoice's sub-ledger is on the LIABILITY leg alone: a conversion leg
            // carrying it would cancel the very debt the payment settles.
            assertEquals(
                listOf(dimensionId),
                entries.filter { it.dimensionId != null }.map { it.dimensionId },
            )
            assertTrue(entries.filter { it.account.type == AccountType.CONVERSION }.all { it.dimensionId == null })

            // The card is owed exactly what it was owed, in its own currency: the invoice leg
            // settles US$ 100 whatever the reais it cost.
            assertEquals(
                10_000L,
                entries.single { it.account.id == cardAccountId }.amount,
            )

            assertEquals(
                5.5,
                koin.get<IExchangeRateRepository>().rateOn(FOREIGN, DATE)?.rate,
                "the payment taught the same quote a transfer would have",
            )
        }

    private fun container(): Koin =
        koinApplication { modules(appModules + temporaryDatabase(file) + fixedBase()) }.koin
            .also { this.koin = it }

    private fun runBlockingTest(body: suspend CoroutineScope.() -> Unit) =
        runBlocking { withTimeout(30_000) { body() } }

    private companion object {
        const val BASE = "BRL"
        const val FOREIGN = "USD"

        val MONTH: YearMonth = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonth

        val DATE: LocalDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}

/** See `SurfaceDenominationGateTest`: binding the builder is overridden by `includes(…)`. */
private fun temporaryDatabase(file: File) = module {
    single<AppDatabase> {
        getRoomDatabase(Room.databaseBuilder<AppDatabase>(name = file.absolutePath))
    } bind RoomDatabase::class
}

/**
 * The base as a fact of the test rather than of the machine: it is otherwise resolved from the
 * device's locale, and whether a rate is collected at all depends on one end being it.
 */
private fun fixedBase() = module {
    single<IBaseCurrencyRepository> {
        object : IBaseCurrencyRepository {
            override fun observe(): StateFlow<String> = MutableStateFlow("BRL")
            override suspend fun set(currency: String) = Unit
        }
    }
}
