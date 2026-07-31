package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.decimal_separator
import com.neoutils.finsight.resources.exchange_rate_form_currency
import com.neoutils.finsight.resources.exchange_rate_form_date
import com.neoutils.finsight.resources.exchange_rate_form_rate
import com.neoutils.finsight.resources.exchange_rate_form_rate_helper
import com.neoutils.finsight.resources.exchange_rate_form_rate_placeholder
import com.neoutils.finsight.resources.exchange_rate_form_remove
import com.neoutils.finsight.resources.exchange_rate_form_save
import com.neoutils.finsight.resources.exchange_rate_form_title_edit
import com.neoutils.finsight.resources.exchange_rate_form_title_new
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyOption
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyPickerModal
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.RateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.formatRate
import com.neoutils.finsight.util.stringUiText
import com.neoutils.finsight.util.toRateOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Registers a rate, corrects one, or removes it.
 *
 * **It is a form, and it is built like every other form of this app** — centred title,
 * fields that are fields, a full-width primary button. The currency is an
 * `ExposedDropdownMenuBox` because that is what choosing among a list inside a form
 * looks like here (`AccountSelector`), not the 52dp `CurrencyRow`, which states a
 * permanent attribute of an account. And the date is typed and validated like every
 * other date in the app, with the calendar as a button beside it — not a read-only box
 * that can only be filled by a picker.
 *
 * **This is the only place an external suggestion may ever appear** (design D11), and
 * in v1 it appears as nothing more than the field's placeholder. No read of this app
 * waits on the network, shows a loading state, or fails because a source is
 * unreachable — so there is nothing here to block on either.
 */
class ExchangeRateFormModal(
    private val existing: ExchangeRate? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ExchangeRateFormViewModel> { parametersOf(existing) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current
        val separator = stringResource(Res.string.decimal_separator)

        val rate = rememberTextFieldState(
            uiState.rate?.let { formatRate(it, separator) }.orEmpty()
        )

        LaunchedEffect(Unit) {
            snapshotFlow { rate.text.toString() }
                .collect { viewModel.onAction(ExchangeRateFormAction.ChangeRate(it.toRateOrNull())) }
        }

        val date = rememberTextFieldState(dayMonthYear.format(uiState.date))

        // The typed date is the source of truth of the field, and the state only hears
        // about it once it parses. A half-typed `15/0` is not an error to report — it is
        // a date the user has not finished writing — so it simply does not submit.
        val typedDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull()

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .collect { text ->
                    runCatching { dayMonthYear.parse(text) }.getOrNull()?.let {
                        viewModel.onAction(ExchangeRateFormAction.SelectDate(it))
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (uiState.isEditing) {
                        Res.string.exchange_rate_form_title_edit
                    } else {
                        Res.string.exchange_rate_form_title_new
                    }
                ),
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The currency of an existing rate is not editable: changing it would not
            // correct that observation, it would silently reassign it to another
            // currency. Removing and registering again is the honest path.
            if (!uiState.isEditing) {
                val pickerTitle = stringResource(Res.string.exchange_rate_form_currency)
                val options = uiState.selectableCurrencies.map {
                    CurrencyOption(code = it.code, symbol = it.symbol, name = stringUiText(it.name))
                }
                val selected = options.firstOrNull { it.code == uiState.currency }

                val openCurrencies = {
                    modalManager.show(
                        CurrencyPickerModal(
                            title = pickerTitle,
                            currencies = options,
                            selectedCode = uiState.currency,
                            onCurrencySelected = {
                                viewModel.onAction(ExchangeRateFormAction.SelectCurrency(it.code))
                            },
                        )
                    )
                }

                // A field of the form that opens the shared sheet — the same shape
                // `ConfirmRecurringModal` uses for its date. The sheet is where the
                // currencies are chosen everywhere in the app, and a list of twenty is
                // not a dropdown's job.
                //
                // **The whole field opens it, and `Modifier.clickable` is not how.** A
                // text field consumes the gesture to place its own cursor, so a
                // `clickable` around it fires unreliably and the field takes focus it has
                // nothing to do with — a tap that sometimes did nothing. The press comes
                // from the field's own `interactionSource`, which reports it even while
                // read-only.
                val interactions = remember { MutableInteractionSource() }

                LaunchedEffect(interactions) {
                    interactions.interactions.collect {
                        if (it is PressInteraction.Release) openCurrencies()
                    }
                }

                OutlinedTextField(
                    value = selected?.let { "${it.symbol} · ${it.name} (${it.code})" }
                        ?: uiState.currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.exchange_rate_form_currency)) },
                    trailingIcon = {
                        IconButton(onClick = { openCurrencies() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = colorScheme.primary,
                            )
                        }
                    },
                    interactionSource = interactions,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                state = rate,
                label = { Text(stringResource(Res.string.exchange_rate_form_rate)) },
                placeholder = { Text(stringResource(Res.string.exchange_rate_form_rate_placeholder)) },
                supportingText = {
                    Text(
                        stringResource(
                            Res.string.exchange_rate_form_rate_helper,
                            uiState.baseCurrency,
                            uiState.currency,
                        )
                    )
                },
                // A rate is typed left to right, so the field keeps what was typed and
                // only refuses what a rate cannot be — one separator, four decimals, no
                // stray characters. The separator the keyboard emits becomes the
                // language's, which is what keeps `5,32` from becoming `532`.
                inputTransformation = RateInputTransformation(separator),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = { Text(stringResource(Res.string.exchange_rate_form_date)) },
                inputTransformation = DateInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = typedDate,
                                    onDateSelected = { selected ->
                                        date.edit {
                                            replace(0, length, dayMonthYear.format(selected))
                                        }
                                    },
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.CalendarToday,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.onAction(ExchangeRateFormAction.Submit)
                    modalManager.dismiss(this@ExchangeRateFormModal)
                },
                // A rate the field cannot read, or a date it cannot parse, is not an
                // error to report — it is a form that is not finished.
                enabled = uiState.canSubmit && typedDate != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.exchange_rate_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Removal is the obligatory corollary of a rate outliving its transaction
            // (design D27), not a convenience: a rate observed by mistake from an
            // operation since deleted has no other path that reaches it.
            if (uiState.isEditing) {
                TextButton(
                    onClick = {
                        viewModel.onAction(ExchangeRateFormAction.Remove)
                        modalManager.dismiss(this@ExchangeRateFormModal)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.exchange_rate_form_remove),
                        color = colorScheme.error,
                    )
                }
            }
        }
    }
}
