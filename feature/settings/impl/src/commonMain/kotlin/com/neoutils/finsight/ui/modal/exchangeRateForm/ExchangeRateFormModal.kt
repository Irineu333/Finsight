package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.formatRate
import com.neoutils.finsight.util.stringUiText
import com.neoutils.finsight.util.toRateOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Registers a rate, corrects one, or removes it.
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

        var rateText by remember {
            mutableStateOf(uiState.rate?.let { formatRate(it, separator) }.orEmpty())
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
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

            // The currency of an existing rate is not editable: changing it would not
            // correct that observation, it would silently reassign it to another
            // currency. Removing and registering again is the honest path.
            if (!uiState.isEditing) {
                val pickerTitle = stringResource(Res.string.exchange_rate_form_currency)
                val options = uiState.selectableCurrencies.map {
                    CurrencyOption(code = it.code, symbol = it.symbol, name = stringUiText(it.name))
                }

                SelectorRow(
                    label = stringResource(Res.string.exchange_rate_form_currency),
                    value = uiState.currency,
                    onClick = {
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
                    },
                )
            }

            OutlinedTextField(
                value = rateText,
                onValueChange = { text ->
                    rateText = text
                    viewModel.onAction(ExchangeRateFormAction.ChangeRate(text.toRateOrNull()))
                },
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
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            SelectorRow(
                label = stringResource(Res.string.exchange_rate_form_date),
                value = LocalDateFormats.current.monthDayYear.format(uiState.date),
                icon = true,
                onClick = {
                    modalManager.show(
                        DatePickerModal(
                            initialDate = uiState.date,
                            onDateSelected = {
                                viewModel.onAction(ExchangeRateFormAction.SelectDate(it))
                            },
                        )
                    )
                },
            )

            Button(
                onClick = {
                    viewModel.onAction(ExchangeRateFormAction.Submit)
                    modalManager.dismiss(this@ExchangeRateFormModal)
                },
                enabled = uiState.canSubmit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.exchange_rate_form_save))
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

@Composable
private fun SelectorRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    icon: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = false,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        trailingIcon = if (icon) {
            {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
