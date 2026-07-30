@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rate_form_currency
import com.neoutils.finsight.resources.exchange_rate_form_date
import com.neoutils.finsight.resources.exchange_rate_form_edit_title
import com.neoutils.finsight.resources.exchange_rate_form_last_known
import com.neoutils.finsight.resources.exchange_rate_form_new_title
import com.neoutils.finsight.resources.exchange_rate_form_rate
import com.neoutils.finsight.resources.exchange_rate_form_save
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.dayMonthYear
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Entering or correcting a rate: a number, a currency and a date, and nothing else.
 *
 * **No network.** The spec allows an external source to *suggest* a value here and nowhere
 * else — a MAY this change leaves unexercised, since the app has no HTTP client and adding
 * a quotes provider brings an API key, a data licence and a new axis of failure with it.
 * The slot where such a suggestion would sit is the rate field's placeholder, which for now
 * holds the last rate the app itself knows for that currency, with its date beside it.
 * Either way nothing about this modal waits on anything: it opens, it is filled in, it saves.
 *
 * The currency is chosen only when the rate is new. Editing one moves an existing statement
 * about a day, and the currency is what identifies which statement that is.
 */
class ExchangeRateFormModal(
    private val base: String,
    private val rate: ExchangeRate? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ExchangeRateFormViewModel> { parametersOf(rate, base) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current

        var currencyExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (uiState.isEditMode) {
                        Res.string.exchange_rate_form_edit_title
                    } else {
                        Res.string.exchange_rate_form_new_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.currency,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !uiState.isEditMode,
                    label = { Text(text = stringResource(Res.string.exchange_rate_form_currency)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!uiState.isEditMode) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { currencyExpanded = true }
                    )
                }

                DropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false },
                ) {
                    uiState.currencies.forEach { currency ->
                        DropdownMenuItem(
                            text = { Text(text = currency) },
                            onClick = {
                                viewModel.onAction(ExchangeRateFormAction.CurrencyChanged(currency))
                                currencyExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.rate,
                onValueChange = { viewModel.onAction(ExchangeRateFormAction.RateChanged(it)) },
                label = {
                    Text(text = stringResource(Res.string.exchange_rate_form_rate, uiState.currency))
                },
                placeholder = uiState.suggestion?.let { suggestion ->
                    { Text(text = suggestion.rate.toString()) }
                },
                supportingText = uiState.suggestion?.let { suggestion ->
                    {
                        Text(
                            text = stringResource(
                                Res.string.exchange_rate_form_last_known,
                                dayMonthYear.format(suggestion.date),
                            )
                        )
                    }
                },
                suffix = { Text(text = uiState.base) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dayMonthYear.format(uiState.date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(text = stringResource(Res.string.exchange_rate_form_date)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = uiState.date,
                                    onDateSelected = {
                                        viewModel.onAction(ExchangeRateFormAction.DateChanged(it))
                                    },
                                )
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.onAction(ExchangeRateFormAction.Save) },
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.exchange_rate_form_save))
            }
        }
    }
}
