@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewCreditCard

import app.cash.turbine.test
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewCreditCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) = Unit
    }

    private class FakeCreditCardRepository : ICreditCardRepository {
        private val byId = MutableSharedFlow<CreditCard?>(replay = 1)
        val unarchived = mutableListOf<Long>()
        fun emit(card: CreditCard?) { byId.tryEmit(card) }
        override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = byId
        override suspend fun unarchive(accountId: Long) { unarchived += accountId }
        override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
        override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = throw NotImplementedError()
        override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
        override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = throw NotImplementedError()
        override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
        override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
        override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    }

    private class FakeInvoiceRepository(invoices: List<Invoice>) : IInvoiceRepository {
        private val byCard = MutableStateFlow(invoices)
        override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = byCard
        override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
        override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
        override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
        override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
        override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
        override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
        override suspend fun getInvoiceById(id: Long): Invoice? = throw NotImplementedError()
        override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
        override suspend fun update(invoice: Invoice) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
    }

    private fun card(id: Long = 1L, accountId: Long = 10L, isArchived: Boolean = true) = CreditCard(
        id = id,
        name = "Card",
        limit = 1000.0,
        closingDay = 10,
        dueDay = 20,
        accountId = accountId,
        isArchived = isArchived,
    )

    private fun invoice(card: CreditCard) = Invoice(
        creditCard = card,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.PAID,
    )

    private fun viewModel(
        creditCardRepository: FakeCreditCardRepository,
        invoiceRepository: FakeInvoiceRepository,
    ) = ViewCreditCardViewModel(
        cardId = 1L,
        creditCardRepository = creditCardRepository,
        invoiceRepository = invoiceRepository,
        unarchiveCreditCard = UnarchiveCreditCardUseCase(creditCardRepository),
        crashlytics = FakeCrashlytics(),
    )

    @Test
    fun `an archived card is shown archived, with its invoice count`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val shown = card(accountId = 10L, isArchived = true)
        val vm = viewModel(repository, FakeInvoiceRepository(listOf(invoice(shown), invoice(shown))))

        vm.uiState.test {
            assertEquals(ViewCreditCardUiState.Loading, awaitItem())
            repository.emit(shown)
            val content = assertIs<ViewCreditCardUiState.Content>(awaitItem())
            assertTrue(content.creditCard.isArchived)
            assertEquals(2, content.invoiceCount)
        }
    }

    @Test
    fun `the unarchive action unarchives the shown card by its accountId`() = runTest(dispatcher) {
        val repository = FakeCreditCardRepository()
        val vm = viewModel(repository, FakeInvoiceRepository(emptyList()))

        vm.uiState.test {
            assertEquals(ViewCreditCardUiState.Loading, awaitItem())
            repository.emit(card(accountId = 77L, isArchived = true))
            assertIs<ViewCreditCardUiState.Content>(awaitItem())

            vm.onAction(ViewCreditCardAction.Unarchive)
            runCurrent()

            assertEquals(listOf(77L), repository.unarchived)
        }
    }
}
