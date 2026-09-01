@file:OptIn(ExperimentalTestApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.extension.currencyFormatterOf
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The third kind of number this field handles: the one the operation already records.
 *
 * The archive's offer and the user's keystrokes were already told apart; a recorded value
 * is neither, and the two rules it obeys come one from each — never written over by a
 * suggestion (like a keystroke), always withdrawn when the currency changes (like an
 * offer). Registering a transfer states no recorded value, and must behave exactly as it
 * did before this distinction existed.
 */
class CounterpartAmountFieldTest {

    private val symbols = mapOf("BRL" to "R$", "USD" to "US$", "EUR" to "€")
    private val formatter = currencyFormatterOf(symbols)
    private val date = LocalDate(2026, 3, 10)

    private val dateFormats = DateFormats(
        monthNames = MonthNames.ENGLISH_FULL,
        dayOfWeekNames = DayOfWeekNames.ENGLISH_FULL,
    )

    @Composable
    private fun Host(content: @Composable () -> Unit) = CompositionLocalProvider(
        LocalCurrencyFormatter provides formatter,
        LocalCurrencySymbols provides { code -> symbols[code] ?: code },
        LocalDateFormats provides dateFormats,
        content = content,
    )

    private val recorded = 100.0

    @Test
    fun `a correction with nothing in the archive for that day keeps the recorded value`() =
        runComposeUiTest {
            lateinit var state: TextFieldState

            setContent {
                Host {
                    state = rememberTextFieldState(formatter.format(recorded, "USD"))
                    CounterpartAmountField(
                        visible = true,
                        state = state,
                        label = "Enters in Chase",
                        currency = "USD",
                        counterpartAmount = 550.0,
                        counterpartCurrency = "BRL",
                        // The archive has nothing to say about that day: the branch that
                        // used to reach `clearText()` and wipe the recorded value.
                        suggestion = null,
                        date = date,
                        recordedAmount = recorded,
                    )
                }
            }
            waitForIdle()

            assertEquals(formatter.format(recorded, "USD"), state.text.toString())
        }

    @Test
    fun `a correction with an observation of that day keeps the recorded value, not the implied one`() =
        runComposeUiTest {
            lateinit var state: TextFieldState

            setContent {
                Host {
                    state = rememberTextFieldState(formatter.format(recorded, "USD"))
                    CounterpartAmountField(
                        visible = true,
                        state = state,
                        label = "Enters in Chase",
                        currency = "USD",
                        counterpartAmount = 550.0,
                        counterpartCurrency = "BRL",
                        // Same day, so this would have been written into the field.
                        suggestion = CrossCurrencyAmountSuggestion(amount = 96.0, asOf = date),
                        date = date,
                        recordedAmount = recorded,
                    )
                }
            }
            waitForIdle()

            assertEquals(formatter.format(recorded, "USD"), state.text.toString())
        }

    @Test
    fun `pointing the destination at another currency withdraws the recorded value`() =
        runComposeUiTest {
            lateinit var state: TextFieldState
            var currency by mutableStateOf("USD")

            setContent {
                Host {
                    state = rememberTextFieldState(formatter.format(recorded, "USD"))
                    CounterpartAmountField(
                        visible = true,
                        state = state,
                        label = "Enters somewhere",
                        currency = currency,
                        counterpartAmount = 550.0,
                        counterpartCurrency = "BRL",
                        suggestion = null,
                        date = date,
                        recordedAmount = recorded,
                    )
                }
            }
            waitForIdle()
            assertEquals(formatter.format(recorded, "USD"), state.text.toString())

            currency = "EUR"
            waitForIdle()

            assertTrue(
                state.text.isEmpty(),
                "digits denominated in dollars must not survive under the euro symbol",
            )
        }

    // --- Registering a transfer: no recorded value, and nothing about it changes ---

    @Test
    fun `without a recorded value the archive's offer of that day still fills the field`() =
        runComposeUiTest {
            lateinit var state: TextFieldState

            setContent {
                Host {
                    state = rememberTextFieldState()
                    CounterpartAmountField(
                        visible = true,
                        state = state,
                        label = "Enters in Chase",
                        currency = "USD",
                        counterpartAmount = 550.0,
                        counterpartCurrency = "BRL",
                        suggestion = CrossCurrencyAmountSuggestion(amount = 96.0, asOf = date),
                        date = date,
                    )
                }
            }
            waitForIdle()

            assertEquals(formatter.format(96.0, "USD"), state.text.toString())
        }

    @Test
    fun `without a recorded value an offer is still withdrawn when the currency changes`() =
        runComposeUiTest {
            lateinit var state: TextFieldState
            var currency by mutableStateOf("USD")

            setContent {
                Host {
                    state = rememberTextFieldState()
                    CounterpartAmountField(
                        visible = true,
                        state = state,
                        label = "Enters somewhere",
                        currency = currency,
                        counterpartAmount = 550.0,
                        counterpartCurrency = "BRL",
                        suggestion = remember(currency) {
                            CrossCurrencyAmountSuggestion(amount = 96.0, asOf = date)
                                .takeIf { currency == "USD" }
                        },
                        date = date,
                    )
                }
            }
            waitForIdle()
            assertEquals(formatter.format(96.0, "USD"), state.text.toString())

            currency = "EUR"
            waitForIdle()

            assertTrue(state.text.isEmpty(), "an offer does not survive the currency it was made in")
        }
}
