@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.withoutCopy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.CurrencyEntity
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.CurrencyRepository
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.feature.backup.api.ProceedWithoutCopyModal
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_delete_confirm_action
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.deleteCurrency.DeleteCurrencyViewModel
import com.neoutils.finsight.RecordingAnalytics
import com.neoutils.finsight.ui.modal.deleteExchangeRate.DeleteExchangeRateViewModel
import com.neoutils.finsight.util.UiText
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferTerms

/**
 * A preventive capture that fails **interrupts the action and asks**, and this is where the
 * asking half is pinned.
 *
 * Every assertion is made against the archive rather than against the exception, because an
 * exception caught is not the requirement: a screen that swallowed the refusal and deleted
 * anyway would satisfy "it was caught" and destroy the very thing the copy existed for. So
 * each test says what is still in the tables while the question is up, and what is in them
 * after each of the three answers — yes, no, and walking away.
 */
class ProceedWithoutCopyTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    /**
     * Every view model these tests build, held so it can be cleared at the end.
     *
     * A view model that collects for as long as it lives ends, in the app, when its sheet is
     * dismissed. A test that never ends one leaves the collector resuming on a main
     * dispatcher that has already been reset, which fails whichever test happens to run next
     * rather than the one that leaked.
     */
    private val store = ViewModelStore()

    private var built = 0

    private fun <T : ViewModel> T.tracked(): T = also { store.put("vm-${built++}", it) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        db.close()
    }

    private class FixedBase(code: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(code)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) { state.value = code }
    }

    private class FakeSyncState : IRateSyncStateRepository {
        private val flow = MutableStateFlow(RateSyncState())
        override fun observe(): StateFlow<RateSyncState> = flow
        override suspend fun record(state: RateSyncState) { flow.value = state }
    }

    /**
     * A vault with nowhere to write, counting the times it was asked.
     *
     * The count is what says the second attempt really went **without** a copy: a screen
     * that answered the question by simply retrying would ask twice and be refused twice,
     * and would never destroy anything.
     */
    private class Refusing : PreventiveBackup {

        var asked = 0
            private set

        override suspend fun captureBefore(action: DestructiveAction) {
            asked++
            throw PreventiveCaptureException(
                reason = UiText.Raw("nowhere to write"),
                message = "nowhere to write",
            )
        }
    }

    /**
     * A vault that is off, can be turned on, and remembers both answers it was given.
     *
     * What turning the vault on actually does is `StandingVaultOffer`'s and is pinned in the
     * backup module. What is asserted here is the half these screens own: that the offer is
     * asked for, and that a box left ticked is acted on **before** anything is destroyed.
     */
    private class OfferingVault : VaultOffer {

        var accepted = false
            private set

        var declined = false
            private set

        // Accepting is what turns the vault on, and a vault that is on has nothing left to
        // offer — which is the only thing that stops the offer being made.
        override fun offer(): VaultOfferTerms? {
            if (accepted) return null

            return VaultOfferTerms(
                intervalLabel = UiText.Raw("3 days"),
                wasDeclined = declined,
                decline = { declined = true },
            ) { accepted = true }
        }
    }

    /** Reads the offer's answer at the one moment it has to be true already. */
    private class WatchingBackup(private val vault: OfferingVault) : PreventiveBackup {

        var acceptedWhenAsked: Boolean? = null
            private set

        override suspend fun captureBefore(action: DestructiveAction) {
            acceptedWhenAsked = vault.accepted
        }
    }

    /**
     * A vault that covers one action and no other, and remembers which it was asked about.
     *
     * It is what pins the half a screen owns: each sheet asks about the action it is a
     * confirmation of, and shows what it is told. Which actions are worth a copy is decided
     * behind this and asserted in the backup module.
     */
    private class RecordingCoverage(private val covered: DestructiveAction) : PreventiveCoverage {

        var asked: DestructiveAction? = null
            private set

        override fun keepsCopyBefore(action: DestructiveAction): Boolean {
            asked = action
            return action == covered
        }
    }

    private val base = FixedBase("BRL")

    private val currencies = CurrencyRepository(
        database = db,
        dao = db.currencyDao(),
        exchangeRateDao = db.exchangeRateDao(),
    )

    private fun rates(backup: PreventiveBackup) = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = base,
        preventiveBackup = backup,
    )

    private fun deleteCurrency(backup: PreventiveBackup) = DeleteCurrencyUseCase(
        repository = currencies,
        exchangeRateRepository = rates(backup),
        rateSyncStateRepository = FakeSyncState(),
        accountDao = db.accountDao(),
        budgetDao = db.budgetDao(),
        preventiveBackup = backup,
    )

    private fun deleteViewModel(
        code: String,
        backup: PreventiveBackup,
        vaultOffer: VaultOffer = VaultOffer.None,
        coverage: PreventiveCoverage = PreventiveCoverage.None,
    ) = DeleteCurrencyViewModel(
        code = code,
        deleteCurrency = deleteCurrency(backup),
        modalManager = ModalManager(),
        analytics = RecordingAnalytics(),
        vaultOffer = vaultOffer,
        coverage = coverage,
    ).tracked()

    /**
     * The sheet the form's remove button opens. The removal lives here and nowhere else,
     * so this is what the questions below are asked of.
     */
    private fun removeRateViewModel(
        rate: ExchangeRate,
        backup: PreventiveBackup,
        vaultOffer: VaultOffer = VaultOffer.None,
        coverage: PreventiveCoverage = PreventiveCoverage.None,
    ) = DeleteExchangeRateViewModel(
        rate = rate,
        exchangeRateRepository = rates(backup),
        modalManager = ModalManager(),
        analytics = RecordingAnalytics(),
        vaultOffer = vaultOffer,
        coverage = coverage,
    ).tracked()

    private val march = LocalDate(2026, 3, 14)

    private suspend fun seedCurrencies(vararg codes: String) {
        codes.forEach { db.currencyDao().upsert(CurrencyEntity(code = it, symbol = it)) }
    }

    private suspend fun seedRate(from: String, to: String, value: Double): ExchangeRate {
        val archive = rates(PreventiveBackup.None)

        archive.save(
            ExchangeRate(
                currency = from,
                counterCurrency = to,
                date = march,
                rate = value,
                source = ExchangeRate.Source.USER,
            )
        )

        return archive.observeAll().first().first { it.currency == from }
    }

    // --- deleting a currency ---

    @Test
    fun `a refused copy stops the deletion and asks, with the currency still there`() = runTest {
        seedCurrencies("BRL", "USD")
        seedRate("USD", "BRL", 5.5)

        val viewModel = deleteViewModel("USD", Refusing())
        viewModel.delete()

        val reason = viewModel.captureRefusal.first { it != null }

        assertNotNull(reason, "the question says why there is no copy")
        assertTrue(db.currencyDao().exists("USD"), "nothing may be destroyed before the answer")
        assertEquals(1, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
    }

    @Test
    fun `saying yes deletes the currency, and takes no copy on the way`() = runTest {
        seedCurrencies("BRL", "USD")
        seedRate("USD", "BRL", 5.5)

        val vault = Refusing()
        val viewModel = deleteViewModel("USD", vault)
        viewModel.delete()
        viewModel.captureRefusal.first { it != null }

        viewModel.deleteWithoutCopy()

        // Awaited through the registry the screen observes: the write lands on another
        // dispatcher, and the row disappearing is what the user is shown.
        currencies.observeAll().first { all -> all.none { it.code == "USD" } }

        assertEquals(0, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
        assertNull(viewModel.captureRefusal.value, "there is nothing left to ask")
        assertEquals(1, vault.asked, "the second attempt went without a copy, not for another one")
    }

    @Test
    fun `saying no leaves the currency and its observations exactly as they were`() = runTest {
        seedCurrencies("BRL", "USD")
        seedRate("USD", "BRL", 5.5)

        val vault = Refusing()
        val viewModel = deleteViewModel("USD", vault)
        viewModel.delete()
        viewModel.captureRefusal.first { it != null }

        viewModel.abandonDeletion()

        assertNull(viewModel.captureRefusal.first { it == null })
        assertTrue(db.currencyDao().exists("USD"))
        assertEquals(1, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
        assertEquals(1, vault.asked)
    }

    /**
     * Walking away from the question is answering no. The sheet routes its own dismissal to
     * the same answer the cancel button gives, so a scrim tap cannot become permission.
     */
    @Test
    fun `dismissing the question is answering no`() = runTest {
        seedCurrencies("BRL", "USD")

        val viewModel = deleteViewModel("USD", Refusing())
        viewModel.delete()
        val reason = viewModel.captureRefusal.first { it != null }

        ProceedWithoutCopyModal(
            reason = requireNotNull(reason),
            action = Res.string.currencies_delete_confirm_action,
            onProceed = viewModel::deleteWithoutCopy,
            onAbandon = viewModel::abandonDeletion,
        ).onDismissed()

        assertNull(viewModel.captureRefusal.first { it == null })
        assertTrue(db.currencyDao().exists("USD"))
    }

    // --- removing an observation ---

    @Test
    fun `a refused copy stops the removal and asks, with the observation still there`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)

        val viewModel = removeRateViewModel(rate, Refusing())
        viewModel.remove()

        assertNotNull(viewModel.captureRefusal.first { it != null })
        assertEquals(1, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
    }

    @Test
    fun `saying yes removes the observation, and takes no copy on the way`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)

        val vault = Refusing()
        val viewModel = removeRateViewModel(rate, vault)
        viewModel.remove()
        viewModel.captureRefusal.first { it != null }

        viewModel.removeWithoutCopy()

        rates(PreventiveBackup.None).observeAll().first { it.isEmpty() }

        assertNull(viewModel.captureRefusal.value)
        assertEquals(1, vault.asked, "the second attempt went without a copy, not for another one")
    }

    // --- the vault, offered in place ---

    /**
     * The offer rides on whichever destructive confirmation the person reaches first, and a
     * currency taking every rate that names it is one of the five that carry it. Accepting
     * is acted on **before** the removal asks for the copy.
     */
    @Test
    fun `taking the offer beside the currency turns the vault on before it goes`() = runTest {
        seedCurrencies("BRL", "USD")
        val vault = OfferingVault()
        val backup = WatchingBackup(vault)

        val viewModel = deleteViewModel("USD", backup, vaultOffer = vault)

        assertNotNull(viewModel.offer.terms, "a vault that is off is offered beside the risk")
        assertTrue(viewModel.offer.isAccepted.value, "the offer is made, not merely displayed")

        // Joined rather than watched through the registry: what is asserted below is the
        // order of two things inside this one coroutine, so the test waits for all of it.
        viewModel.delete().join()

        assertTrue(vault.accepted, "one yes, and the whole vault is on")
        assertEquals(
            true,
            backup.acceptedWhenAsked,
            "a vault turned on after the deletion has nothing left to copy",
        )
    }

    /**
     * The gate is the vault's and not a screen's, and one yes closes it everywhere: the
     * confirmation reached after an acceptance carries nothing — across features as much as
     * within one.
     */
    @Test
    fun `an offer accepted beside the currency leaves the rate removal nothing to show`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)
        val vault = OfferingVault()

        val first = deleteViewModel("USD", PreventiveBackup.None, vaultOffer = vault)
        assertNotNull(first.offer.terms, "the confirmation reached first carries it")
        first.delete().join()

        val second = removeRateViewModel(rate, PreventiveBackup.None, vaultOffer = vault)

        assertNull(second.offer.terms, "the vault is on; the next one carries nothing")
    }

    /**
     * A refusal changes what the offer looks like, never whether it is put: the next
     * removal still carries it, with the box empty and the reminder in place of the
     * proposal.
     */
    @Test
    fun `a removal after a refusal still offers, unticked`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)
        val vault = OfferingVault()

        val first = deleteViewModel("USD", PreventiveBackup.None, vaultOffer = vault)
        first.offer.setAccepted(false)
        first.delete().join()

        val second = removeRateViewModel(rate, PreventiveBackup.None, vaultOffer = vault)

        val terms = assertNotNull(second.offer.terms, "the offer stands while the vault is off")

        assertFalse(second.offer.isAccepted.value, "and it arrives with the answer given last")
        assertTrue(terms.wasDeclined, "worded as the reminder it is")
    }

    /**
     * The offer is made beside a risk or not at all, and the confirmation is where the risk
     * is: it exists only because something is about to be removed. A form opened to
     * register a rate makes no offer because it holds none at all — which the compiler
     * enforces now that the removal, the copy and the offer have left it together.
     */
    @Test
    fun `the offer rides on the confirmation, which only a removal opens`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)
        val vault = OfferingVault()

        val removing = removeRateViewModel(rate, PreventiveBackup.None, vaultOffer = vault)

        assertNotNull(removing.offer.terms, "the removal it confirms is a risk")
        assertTrue(removing.offer.isAccepted.value, "the offer is made, not merely displayed")
    }

    @Test
    fun `taking the offer beside the rate turns the vault on before it goes`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)
        val vault = OfferingVault()
        val backup = WatchingBackup(vault)

        val viewModel = removeRateViewModel(rate, backup, vaultOffer = vault)
        viewModel.remove().join()

        rates(PreventiveBackup.None).observeAll().first { it.isEmpty() }

        assertTrue(vault.accepted, "one yes, and the whole vault is on")
        assertEquals(
            true,
            backup.acceptedWhenAsked,
            "a vault turned on after the removal has nothing left to copy",
        )
    }

    // --- what the sheets say about undoing it ---

    /**
     * Each sheet asks about the action it confirms and says what it is told. Neither
     * carries a list of its own: handed a vault that covers something else, both stop
     * promising a copy without a line of them changing (design D7).
     */
    @Test
    fun `the two settings confirmations say what they are told about their own action`() =
        runTest {
            seedCurrencies("BRL", "USD")
            val rate = seedRate("USD", "BRL", 5.5)

            val currencyCovered = RecordingCoverage(DestructiveAction.DELETE_CURRENCY)
            val currency = deleteViewModel(
                code = "USD",
                backup = PreventiveBackup.None,
                coverage = currencyCovered,
            )

            assertTrue(currency.keepsCopy.value, "a copy is kept and the sheet was told otherwise")
            assertEquals(DestructiveAction.DELETE_CURRENCY, currencyCovered.asked)

            val rateCovered = RecordingCoverage(DestructiveAction.REMOVE_EXCHANGE_RATE)
            val rateRemoval = removeRateViewModel(
                rate = rate,
                backup = PreventiveBackup.None,
                coverage = rateCovered,
            )

            assertTrue(rateRemoval.keepsCopy.value, "a copy is kept and the sheet was told otherwise")
            assertEquals(DestructiveAction.REMOVE_EXCHANGE_RATE, rateCovered.asked)

            val elsewhere = deleteViewModel(
                code = "USD",
                backup = PreventiveBackup.None,
                coverage = RecordingCoverage(DestructiveAction.DELETE_TRANSACTION),
            )

            assertFalse(elsewhere.keepsCopy.value, "no copy is kept and the sheet still promised one")
        }

    @Test
    fun `saying no leaves the observation in the archive`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)

        val vault = Refusing()
        val viewModel = removeRateViewModel(rate, vault)
        viewModel.remove()
        viewModel.captureRefusal.first { it != null }

        viewModel.abandonRemoval()

        assertNull(viewModel.captureRefusal.first { it == null })
        assertEquals(1, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
        assertEquals(1, vault.asked)
    }
}
