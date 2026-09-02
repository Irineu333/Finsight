@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.currencyForm

import com.neoutils.finsight.FakeCurrencyRepository
import com.neoutils.finsight.RecordingAnalytics
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.usecase.SaveCurrencyUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What registering a currency reports. The registry is the app's own — an invented unit
 * is exactly what the form exists to allow — so whether anyone registers one is a
 * question only these events can answer.
 */
class CurrencyFormViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        analytics: RecordingAnalytics,
        existing: CurrencyInfo? = null,
        repository: FakeCurrencyRepository = FakeCurrencyRepository(),
    ) = CurrencyFormViewModel(
        existing = existing,
        saveCurrency = SaveCurrencyUseCase(repository),
        modalManager = ModalManager(),
        analytics = analytics,
    )

    @Test
    fun `a code the platform knows is registered as a known one`() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics)

        viewModel.onAction(CurrencyFormAction.ChangeCode("gbp"))
        viewModel.onAction(CurrencyFormAction.ChangeSymbol("£"))
        viewModel.onAction(CurrencyFormAction.Submit)

        assertEquals(listOf("create_currency"), analytics.events.map { it.name })
        // The code as the use case normalised it, not as it was typed.
        assertEquals(
            mapOf("code" to "GBP", "is_custom" to "false"),
            analytics.events.single().params,
        )
    }

    @Test
    fun `an invented unit says so`() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics)

        viewModel.onAction(CurrencyFormAction.ChangeCode("PTS"))
        viewModel.onAction(CurrencyFormAction.ChangeSymbol("pts"))
        viewModel.onAction(CurrencyFormAction.Submit)

        assertEquals(
            mapOf("code" to "PTS", "is_custom" to "true"),
            analytics.events.single().params,
        )
    }

    @Test
    fun `editing a row is told apart from registering one`() = runTest {
        val analytics = RecordingAnalytics()
        val viewModel = viewModel(analytics, existing = CurrencyInfo("USD", "US$", "Dólar"))

        viewModel.onAction(CurrencyFormAction.ChangeSymbol("$"))
        viewModel.onAction(CurrencyFormAction.Submit)

        assertEquals(listOf("edit_currency"), analytics.events.map { it.name })
        assertEquals(mapOf("code" to "USD"), analytics.events.single().params)
    }
}
