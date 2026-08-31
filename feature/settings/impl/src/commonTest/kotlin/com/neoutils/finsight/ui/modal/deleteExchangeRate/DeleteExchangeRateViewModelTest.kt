@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.deleteExchangeRate

import androidx.compose.runtime.Composable
import com.neoutils.finsight.database.repository.RateArchive
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

/**
 * **Removing a rate is confirmed, and the confirmation is what removes it.**
 *
 * It was the one deletion of the user's own data this app performed straight from a button.
 * What pins that here is where the write lives: building the sheet touches the archive not
 * at all, and only its own button reaches it — so a press that opens the sheet can be
 * walked away from, which is the whole of what a confirmation is.
 *
 * The dismissal is asserted by holding the archive suspended rather than by its outcome.
 * Dismissing a `ModalBottomSheet` clears its `ViewModelStore`, which cancels
 * [androidx.lifecycle.viewModelScope]: a button that both removes and dismisses would
 * cancel its own write at the first suspension point, and an assertion that the rate is
 * gone would pass just as well if the write happened to escape the cancellation.
 */
class DeleteExchangeRateViewModelTest {

    private val rate = ExchangeRate(
        id = 1,
        currency = "USD",
        counterCurrency = "BRL",
        date = LocalDate(2026, 8, 1),
        rate = 5.4,
        source = ExchangeRate.Source.USER,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The press that opens the sheet is not the removal, and the archive proves it. */
    @Test
    fun `the sheet coming up removes nothing`() = runTest {
        val archive = FakeRateArchive()

        viewModel(archive, ModalManager())
        archive.release()

        assertTrue(archive.removed.isEmpty(), "a confirmation that removed on sight is not one")
    }

    @Test
    fun `removing dismisses only after the write returns`() = runTest {
        val archive = FakeRateArchive()
        val modal = RecordingModal()
        val manager = ModalManager().apply { show(modal) }

        viewModel(archive, manager).remove()

        assertFalse(modal.isDismissed, "dismissed before the write returned")

        archive.release()

        assertTrue(modal.isDismissed)
        assertEquals(listOf(rate), archive.removed)
    }

    /**
     * The form underneath goes with it. It is open on an observation that no longer exists,
     * and only [ModalManager.dismissAll] takes both sheets down.
     */
    @Test
    fun `the form the sheet was opened from is dismissed too`() = runTest {
        val archive = FakeRateArchive()
        val form = RecordingModal()
        val confirmation = RecordingModal()
        val manager = ModalManager().apply { show(form); show(confirmation) }

        viewModel(archive, manager).remove()
        archive.release()

        assertTrue(form.isDismissed, "the form is open on a rate that has just gone")
        assertTrue(confirmation.isDismissed)
    }

    private fun viewModel(archive: RateArchive, manager: ModalManager) =
        DeleteExchangeRateViewModel(
            rate = rate,
            exchangeRateRepository = archive,
            modalManager = manager,
            vaultOffer = VaultOffer.None,
            coverage = PreventiveCoverage.None,
        )
}

private class RecordingModal : Modal() {

    var isDismissed = false
        private set

    override fun onDismissed() {
        isDismissed = true
    }

    @Composable
    override fun Content() = Unit
}

/** Suspends every write until [release], so the order of the two steps is observable. */
private class FakeRateArchive : RateArchive {

    private val gate = CompletableDeferred<Unit>()

    val removed = mutableListOf<ExchangeRate>()

    fun release() = gate.complete(Unit)

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> = emptyMap()

    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null

    override fun observeAll(): Flow<List<ExchangeRate>> = MutableStateFlow(emptyList())

    override suspend fun save(rate: ExchangeRate) = error("nothing here saves a rate")

    override suspend fun remove(rate: ExchangeRate) = remove(rate, withoutCopy = false)

    override suspend fun remove(rate: ExchangeRate, withoutCopy: Boolean) {
        gate.await()
        removed += rate
    }

    override suspend fun countNaming(currency: String) = 0
}
