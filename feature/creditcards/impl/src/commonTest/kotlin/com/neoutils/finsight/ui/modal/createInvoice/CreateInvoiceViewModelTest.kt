@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.createInvoice

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCaseImpl
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Creating declares the cycle and stops there: the sheet closes, the caller is handed the
 * new invoice so the screen can go to it, and no further form is opened. Chaining the
 * balance adjustment here would put back through the UI the coupling that having a
 * creation operation of its own removed (design D6).
 */
class CreateInvoiceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    /** Opens 5 January, closes 5 February, falls due in February. */
    private val openInvoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 1,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 2),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `creating hands the new invoice back and opens nothing else`() = runTest(dispatcher) {
        val store = InvoiceStore(openInvoice)
        val manager = ModalManager()
        var created: Invoice? = null

        val viewModel = viewModel(store, manager) { created = it }

        viewModel.onAction(CreateInvoiceAction.SelectDueMonth(YearMonth(2025, 11)))
        viewModel.onAction(CreateInvoiceAction.Submit)
        advanceUntilIdle()

        assertEquals(YearMonth(2025, 11), created?.dueMonth)
        assertEquals(Invoice.Status.RETROACTIVE, created?.status)
        // Nothing was pushed over the sheet — neither an error nor a follow-up form.
        assertNull(manager.top)
    }

    @Test
    fun `an occupied month is refused and nothing is written`() = runTest(dispatcher) {
        val store = InvoiceStore(openInvoice)
        val manager = ModalManager()
        var created: Invoice? = null

        val viewModel = viewModel(store, manager) { created = it }

        // The month the open invoice already occupies.
        viewModel.onAction(CreateInvoiceAction.Submit)
        advanceUntilIdle()

        assertNull(created)
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun `the selected month reports whether it is still free`() = runTest(dispatcher) {
        val viewModel = viewModel(InvoiceStore(openInvoice), ModalManager()) {}

        viewModel.uiState.test {
            advanceUntilIdle()
            // It opens on the invoice the sheet was opened from, which is occupied.
            assertEquals(false, expectMostRecentItem().canSubmit)

            viewModel.onAction(CreateInvoiceAction.SelectDueMonth(YearMonth(2026, 5)))
            advanceUntilIdle()
            assertEquals(true, expectMostRecentItem().canSubmit)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(
        store: InvoiceStore,
        manager: ModalManager,
        onCreated: (Invoice) -> Unit,
    ) = CreateInvoiceViewModel(
        creditCard = card,
        initialDueMonth = openInvoice.dueMonth,
        invoiceRepository = store,
        createInvoiceUseCase = CreateInvoiceUseCaseImpl(OneCard(card), store),
        onCreated = onCreated,
        modalManager = manager,
        analytics = MuteAnalytics,
        crashlytics = MuteCrashlytics,
    )
}

private class OneCard(private val card: CreditCard) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        card.takeIf { it.id == creditCardId }
    override suspend fun getAllCreditCards(): List<CreditCard> = listOf(card)
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = listOf(card)
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

/** Holds the card's invoices and re-emits them, so the sheet sees what it just created. */
private class InvoiceStore(vararg seed: Invoice) : IInvoiceRepository {

    private val rows = MutableStateFlow(seed.toList())

    val inserted = mutableListOf<Invoice>()

    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = rows

    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        rows.value.filter { it.creditCard.id == creditCardId }

    override suspend fun insert(invoice: Invoice): Invoice {
        val stored = invoice.copy(id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1)
        rows.value += stored
        inserted += stored
        return stored
    }

    override suspend fun getInvoiceById(id: Long): Invoice? = rows.value.firstOrNull { it.id == id }
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? =
        rows.value.firstOrNull { it.status.isOpen }

    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> =
        creditCardIds.associateWith { getUnpaidInvoicesByCreditCard(it) }.filterValues { it.isNotEmpty() }
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private object MuteAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

private object MuteCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}
