package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.BudgetEntity
import com.neoutils.finsight.database.entity.CurrencyEntity
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.error.CurrencyError
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.domain.usecase.SaveCurrencyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The registry over a real database: what may be registered, what may be archived, what
 * may be deleted — and, just as much, what each of those does **not** do.
 */
class CurrencyRegistryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private class MovableBase(base: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) {
            state.value = code
        }
    }

    private val base = MovableBase("BRL")

    private val repository = CurrencyRepository(dao = db.currencyDao())

    private val rates = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = base,
    )

    private val save = SaveCurrencyUseCase(repository = repository)

    private val delete = DeleteCurrencyUseCase(
        repository = repository,
        exchangeRateRepository = rates,
        accountDao = db.accountDao(),
        budgetDao = db.budgetDao(),
    )

    private val archive = ArchiveCurrencyUseCase(
        repository = repository,
        baseCurrencyRepository = base,
    )

    private val march = LocalDate(2026, 3, 14)

    private suspend fun seed(vararg codes: String) {
        codes.forEach { db.currencyDao().upsert(CurrencyEntity(code = it, symbol = it)) }
    }

    private suspend fun account(currency: String) {
        db.accountDao().insert(
            AccountEntity(
                name = "Conta $currency",
                type = AccountEntity.Type.ASSET,
                currency = currency,
                iconKey = "wallet",
            )
        )
    }

    private suspend fun budget(currency: String) {
        db.budgetDao().insert(
            BudgetEntity(
                iconCategoryId = 0,
                iconKey = "shopping",
                title = "Orçamento $currency",
                amount = 100.0,
                currency = currency,
                period = "MONTHLY",
            )
        )
    }

    private suspend fun rate(from: String, to: String, value: Double) = rates.save(
        ExchangeRate(
            currency = from,
            counterCurrency = to,
            date = march,
            rate = value,
            source = ExchangeRate.Source.USER,
        )
    )

    // --- Registering ------------------------------------------------------------

    @Test
    fun `a repeated code is refused with the reason, and nothing is altered`() = runTest {
        seed("BRL")
        db.currencyDao().upsert(CurrencyEntity(code = "BRL", symbol = "R$", name = "Meu real"))

        val result = save(code = "BRL", symbol = "X", name = "Outro")

        assertEquals(CurrencyError.CODE_EXISTS, result.leftOrNull())
        assertEquals("R$", repository.get("BRL")?.symbol)
        assertEquals("Meu real", repository.get("BRL")?.name)
    }

    @Test
    fun `a currency of other than two decimal places is refused with the reason`() = runTest {
        assertEquals(CurrencyError.UNSUPPORTED_DECIMALS, save("JPY", "¥", null).leftOrNull())
        assertEquals(CurrencyError.UNSUPPORTED_DECIMALS, save("KWD", "د.ك", null).leftOrNull())

        assertTrue(repository.getAll().isEmpty(), "no row may be written by a refused registration")
    }

    /**
     * An invented code is exactly what this form exists to allow: the platform has
     * nothing to say about it, and nothing to contradict.
     */
    @Test
    fun `an invented code is accepted with the symbol and the name the user wrote`() = runTest {
        save(code = "MILHAS", symbol = "MI", name = "Milhas do cartão")

        val stored = repository.get("MILHAS")

        assertEquals("MI", stored?.symbol)
        assertEquals("Milhas do cartão", stored?.name)
    }

    @Test
    fun `registering creates no account, no rate and no budget`() = runTest {
        save(code = "CLP", symbol = "$", name = null)

        assertTrue(db.accountDao().getAllLedgerAccounts().isEmpty())
        assertTrue(rates.observeAll().first().isEmpty())
        assertEquals(0, db.budgetDao().countByCurrency("CLP"))
    }

    // --- The name (design D2) ---------------------------------------------------

    /**
     * A row that stores no name is named by the platform, at every read, in the current
     * language — which is what keeps it from freezing in the language of the run that
     * wrote it.
     */
    @Test
    fun `an unnamed currency follows the language`() = runTest {
        seed("BRL")

        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("en", "US"))
            assertEquals("Brazilian Real", repository.get("BRL")?.name)

            Locale.setDefault(Locale("pt", "BR"))
            assertEquals("Real brasileiro", repository.get("BRL")?.name)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a name the user wrote is his, whatever the language`() = runTest {
        save(code = "BRL", symbol = "R$", name = "Dinheiro")

        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("en", "US"))
            assertEquals("Dinheiro", repository.get("BRL")?.name)

            Locale.setDefault(Locale("pt", "BR"))
            assertEquals("Dinheiro", repository.get("BRL")?.name)
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** No name, and a code the platform cannot name either: the code stands in, without error. */
    @Test
    fun `a code the platform cannot name reads as itself`() = runTest {
        seed("MILHAS")

        val stored = repository.get("MILHAS")

        assertNull(stored?.name)
        assertEquals("MILHAS", stored?.code)
    }

    // --- Deleting ---------------------------------------------------------------

    @Test
    fun `an account denominating the currency refuses the deletion, and nothing is removed`() =
        runTest {
            seed("USD")
            account("USD")
            rate("USD", "BRL", 5.5)

            val result = delete("USD")

            assertEquals(CurrencyError.DENOMINATED_BY_ACCOUNT, result.leftOrNull())
            assertTrue(repository.exists("USD"))
            assertEquals(1, rates.observeAll().first().size)
        }

    @Test
    fun `a budget denominating the currency refuses the deletion, and nothing is removed`() =
        runTest {
            seed("USD")
            budget("USD")
            rate("USD", "BRL", 5.5)

            val result = delete("USD")

            assertEquals(CurrencyError.DENOMINATED_BY_BUDGET, result.leftOrNull())
            assertTrue(repository.exists("USD"))
            assertEquals(1, rates.observeAll().first().size)
        }

    /**
     * A rate never blocks a deletion — it goes with it, in the same write. Leaving it
     * behind would keep a conversion path through a currency that exists nowhere in the
     * interface.
     */
    @Test
    fun `the observations go with the currency, on either end`() = runTest {
        seed("PEN")
        rate("PEN", "BRL", 1.4)
        rate("USD", "PEN", 3.7)
        rate("USD", "BRL", 5.5)

        assertEquals(2, delete.ratesToRemove("PEN"), "the number is stated before it happens")

        delete("PEN")

        assertEquals(listOf("USD" to "BRL"), rates.observeAll().first().map { it.currency to it.counterCurrency })
        assertTrue(!repository.exists("PEN"))
    }

    /**
     * The pivot cannot survive the currency: with `USD → PEN → BRL` gone, the dollar has
     * no path to the real left, and the figure goes back to being its own term.
     */
    @Test
    fun `no triangulation survives the currency it pivoted on`() = runTest {
        seed("PEN", "USD")
        rate("USD", "PEN", 3.7)
        rate("PEN", "BRL", 1.4)

        assertTrue(rates.rateBetween("USD", "BRL", march) != null, "it triangulates before")

        delete("PEN")

        assertNull(rates.rateBetween("USD", "BRL", march))
    }

    // --- Archiving --------------------------------------------------------------

    @Test
    fun `the base currency is refused with the reason, and stays offered`() = runTest {
        seed("BRL", "USD")

        val result = archive.archive("BRL")

        assertEquals(CurrencyError.BASE_CURRENCY_NOT_ARCHIVABLE, result.leftOrNull())
        assertTrue(repository.getOffered().any { it.code == "BRL" })
    }

    @Test
    fun `archiving is reversible, and unarchiving gives it back to every form`() = runTest {
        seed("BRL", "USD")

        archive.archive("USD")
        assertEquals(listOf("BRL"), repository.getOffered().map { it.code })
        assertEquals(listOf("BRL", "USD"), repository.getAll().map { it.code })

        archive.unarchive("USD")
        assertEquals(listOf("BRL", "USD"), repository.getOffered().map { it.code })
    }

    /**
     * What archiving does **not** do. The observations stay, and go on being read: a
     * conversion that triangulates over an archived currency answers exactly what it
     * answered before.
     */
    @Test
    fun `archiving removes no observation and still pivots`() = runTest {
        seed("BRL", "USD", "PEN")
        rate("USD", "PEN", 3.7)
        rate("PEN", "BRL", 1.4)

        val before = rates.rateBetween("USD", "BRL", march)?.rate

        archive.archive("PEN")

        assertEquals(2, rates.observeAll().first().size, "no observation is removed")
        assertEquals(before, rates.rateBetween("USD", "BRL", march)?.rate)
    }

    /**
     * An account in an archived currency stays active and goes on taking entries: the
     * ledger knows neither the registry nor this flag, and adding the veto there would
     * break the module boundary. One line of defence, deliberately.
     */
    @Test
    fun `an account in an archived currency stays active`() = runTest {
        seed("BRL", "USD")
        account("USD")

        archive.archive("USD")

        val accounts = db.accountDao().getAllLedgerAccounts()
        assertEquals(1, accounts.size)
        assertTrue(!accounts.first().isArchived, "archiving a currency archives no account")
        assertEquals("USD", accounts.first().currency)
    }
}
