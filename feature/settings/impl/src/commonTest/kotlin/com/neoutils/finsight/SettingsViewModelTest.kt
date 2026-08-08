@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight

import app.cash.turbine.test
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.ui.screen.settings.SettingsAction
import com.neoutils.finsight.ui.screen.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Switching the base currency writes the preference — **and nothing else**.
 *
 * That "nothing else" is the whole assertion. An implementation that also re-expressed
 * the archive would be a migration, would destroy the observations it rewrote, and would
 * pass any test that only checked the new code was stored.
 */
class SettingsViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Records every write it receives, so a second one would be visible. */
    private class RecordingBase(base: String = "BRL") : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        val written = mutableListOf<String>()

        override fun observe(): StateFlow<String> = state

        override suspend fun set(code: String) {
            written += code
            state.value = code
        }
    }

    @Test
    fun `switching writes the preference once`() = runTest {
        val repository = RecordingBase()

        SettingsViewModel(repository, FakeCurrencyRepository()).onAction(SettingsAction.SwitchBaseCurrency("USD"))

        assertEquals(listOf("USD"), repository.written)
        assertEquals("USD", repository.observe().value)
    }

    @Test
    fun `the switch reaches the state that every figure observes`() = runTest {
        val repository = RecordingBase()
        val viewModel = SettingsViewModel(repository, FakeCurrencyRepository())

        viewModel.uiState.test {
            assertEquals("BRL", awaitItem().baseCurrencyCode)

            viewModel.onAction(SettingsAction.SwitchBaseCurrency("EUR"))

            assertEquals("EUR", awaitItem().baseCurrencyCode)
        }
    }

    /**
     * The registry whole, minus the archived rows — including currencies no rate reaches
     * (design D6). The switch is a preference over what the app offers, never a question
     * about what the archive can reach.
     */
    @Test
    fun `the registry is offered whole`() = runTest {
        val viewModel = SettingsViewModel(RecordingBase(), FakeCurrencyRepository())

        val state = viewModel.uiState.first { it.selectableCurrencies.isNotEmpty() }

        assertEquals(
            FakeCurrencyRepository.DEFAULT.map { it.code },
            state.selectableCurrencies.map { it.code },
        )
    }

    /** An archived currency is not offered as a base: archiving is a rule about offering. */
    @Test
    fun `an archived currency is not offered`() = runTest {
        val viewModel = SettingsViewModel(
            RecordingBase(),
            FakeCurrencyRepository(archived = setOf("EUR")),
        )

        val state = viewModel.uiState.first { it.selectableCurrencies.isNotEmpty() }

        assertEquals(listOf("BRL", "USD"), state.selectableCurrencies.map { it.code })
    }
}
