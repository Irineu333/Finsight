@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCurrency

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CurrencyEntity
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.CurrencyRepository
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.ui.model.RetireAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The screen a row of the registry opens — and the reason it exists: it states what
 * denominates the currency **before** offering an action, and offers the action the
 * domain would actually accept.
 */
class ViewCurrencyViewModelTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private class MovableBase(base: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) {
            state.value = code
        }
    }

    private object StubCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private val base = MovableBase("BRL")
    private val repository = CurrencyRepository(
        database = db,
        dao = db.currencyDao(),
        exchangeRateDao = db.exchangeRateDao(),
    )
    private val rates = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = base,
        preventiveBackup = PreventiveBackup.None,
    )

    private val delete = DeleteCurrencyUseCase(
        repository = repository,
        rateSyncStateRepository = object : IRateSyncStateRepository {
            private val flow = MutableStateFlow(RateSyncState())
            override fun observe(): StateFlow<RateSyncState> = flow
            override suspend fun record(state: RateSyncState) { flow.value = state }
        },
        exchangeRateRepository = rates,
        accountDao = db.accountDao(),
        budgetDao = db.budgetDao(),
        preventiveBackup = PreventiveBackup.None,
    )

    private fun viewModel(code: String) = ViewCurrencyViewModel(
        code = code,
        currencyRepository = repository,
        baseCurrencyRepository = base,
        deleteCurrency = delete,
        archiveCurrency = ArchiveCurrencyUseCase(
            repository = repository,
            baseCurrencyRepository = base,
        ),
        crashlytics = StubCrashlytics,
    )

    private suspend fun content(code: String) = viewModel(code).uiState
        .first { it is ViewCurrencyUiState.Content } as ViewCurrencyUiState.Content

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

    @Test
    fun `a currency nothing denominates offers deleting`() = runTest {
        seed("BRL", "PEN")

        val state = content("PEN")

        assertEquals(RetireAction.DELETE, state.retireAction)
        assertTrue(state.usage.isDeletable)
    }

    /**
     * The screen must never offer an action the use case refuses — which is why the rule
     * is read from its owner rather than derived here a second time.
     */
    @Test
    fun `a currency an account denominates offers archiving instead`() = runTest {
        seed("BRL", "USD")
        account("USD")

        val state = content("USD")

        assertEquals(RetireAction.ARCHIVE, state.retireAction)
        assertEquals(1, state.usage.accounts)
    }

    /** The number the deletion will state, read before the user reaches for it. */
    @Test
    fun `the observations that would go with it are counted upfront`() = runTest {
        seed("BRL", "PEN")
        rates.save(
            ExchangeRate(
                currency = "PEN",
                counterCurrency = "BRL",
                date = LocalDate(2026, 3, 14),
                rate = 1.4,
                source = ExchangeRate.Source.USER,
            )
        )

        assertEquals(1, content("PEN").usage.rates)
    }

    /**
     * The base offers no retirement: archiving it is refused outright, and deleting it is
     * refused by the account it denominates. The absence lives in the state and not in
     * the composable precisely so this test can reach it.
     */
    @Test
    fun `the base offers no retirement at all`() = runTest {
        seed("BRL", "USD")

        assertNull(content("BRL").retireAction)
        assertEquals(RetireAction.DELETE, content("USD").retireAction, "only the base loses it")
    }

    @Test
    fun `the base is stated, and an archived currency says so`() = runTest {
        seed("BRL", "USD")
        repository.archive("USD")

        val brl = content("BRL")
        assertTrue(brl.isBase)
        assertFalse(brl.isArchived)

        val usd = content("USD")
        assertFalse(usd.isBase)
        assertTrue(usd.isArchived)
    }

    /** Reversible and innocuous, so it happens without a confirmation. */
    @Test
    fun `unarchiving from here gives the currency back to the forms`() = runTest {
        seed("BRL", "USD")
        repository.archive("USD")

        val viewModel = viewModel("USD")
        viewModel.uiState.first { it is ViewCurrencyUiState.Content }

        viewModel.onAction(ViewCurrencyAction.Unarchive)

        // Awaited through the screen's own state rather than read straight from the
        // table: the write lands on another dispatcher, and what the user sees is the
        // button swapping back — which is the state emitting, not the row changing.
        val state = viewModel.uiState.first {
            it is ViewCurrencyUiState.Content && !it.isArchived
        }

        assertFalse((state as ViewCurrencyUiState.Content).isArchived)
        assertEquals(listOf("BRL", "USD"), repository.getOffered().map { it.code })
    }
}
