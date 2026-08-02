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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `switching writes the preference, once`() = runTest {
        val repository = RecordingBase()

        SettingsViewModel(repository).onAction(SettingsAction.SwitchBaseCurrency("USD"))

        assertEquals(listOf("USD"), repository.written)
        assertEquals("USD", repository.observe().value)
    }

    @Test
    fun `the switch reaches the state that every figure observes`() = runTest {
        val repository = RecordingBase()
        val viewModel = SettingsViewModel(repository)

        viewModel.uiState.test {
            assertEquals("BRL", awaitItem().baseCurrencyCode)

            viewModel.onAction(SettingsAction.SwitchBaseCurrency("EUR"))

            assertEquals("EUR", awaitItem().baseCurrencyCode)
        }
    }

    /** The whole curated catalog, including currencies no rate reaches (design D6). */
    @Test
    fun `the whole catalog is offered`() = runTest {
        val state = SettingsViewModel(RecordingBase()).uiState.value

        assertTrue(state.selectableCurrencies.size > 1)
        assertTrue(state.selectableCurrencies.any { it.code == "BRL" })
    }
}
