package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.feature.creditCards.resources.*
import com.neoutils.finsight.feature.accounts.component.AccountSelector
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.ModalErrorContent
import com.neoutils.finsight.core.ui.modal.date.DatePickerModal
import com.neoutils.finsight.core.ui.util.DateInputTransformation
import com.neoutils.finsight.core.utils.util.dayMonthYear
import com.neoutils.finsight.feature.creditCards.model.form.PayInvoiceForm
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class PayInvoiceModal(
    private val invoiceId: Long,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<PayInvoiceViewModel> {
            parametersOf(invoiceId)
        }

        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            PayInvoiceUiState.Loading -> LoadingContent()
            PayInvoiceUiState.Error -> ErrorContent()
            is PayInvoiceUiState.Content -> ContentContent(state, viewModel)
        }
    }

    @Composable
    private fun ErrorContent() {
        val manager = LocalModalManager.current
        ModalErrorContent(
            message = stringResource(Res.string.pay_invoice_unavailable),
            onClose = { manager.dismiss() },
        )
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.pay_invoice_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun ContentContent(
        state: PayInvoiceUiState.Content,
        viewModel: PayInvoiceViewModel,
    ) {
        val manager = LocalModalManager.current
        val form = state.form

        val amount = LocalCurrencyFormatter.current.format(form.outstandingDebt)

        val date = rememberTextFieldState(dayMonthYear.format(form.date))

        LaunchedEffect(form.date) {
            val formatted = dayMonthYear.format(form.date)
            if (date.text.toString() != formatted) {
                date.edit { replace(0, length, formatted) }
            }
        }

        LaunchedEffect(viewModel) {
            snapshotFlow { date.text.toString() }
                .collect { text ->
                    val date = runCatching {
                        dayMonthYear.parse(text)
                    }.getOrNull() ?: return@collect

                    if (date != state.form.date) {
                        viewModel.onAction(PayInvoiceAction.SelectDate(date))
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
                text = stringResource(Res.string.pay_invoice_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.pay_invoice_message),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { },
                label = {
                    Text(text = stringResource(Res.string.pay_invoice_amount_label))
                },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AccountSelector(
                selectedAccount = form.account,
                accounts = state.accounts,
                onAccountSelected = {
                    viewModel.onAction(PayInvoiceAction.SelectAccount(it))
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = {
                    Text(text = stringResource(Res.string.pay_invoice_date_label))
                },
                inputTransformation = DateInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            manager.show(
                                DatePickerModal(
                                    initialDate = form.date,
                                    minDate = form.minDate,
                                    maxDate = form.maxDate,
                                    onDateSelected = { selectedDate ->
                                        viewModel.onAction(PayInvoiceAction.SelectDate(selectedDate))
                                    }
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onAction(PayInvoiceAction.Submit) },
                enabled = canSubmit(form, date.text.toString()),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.pay_invoice_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    private fun canSubmit(form: PayInvoiceForm, rawDateText: String): Boolean {
        if (rawDateText.isEmpty()) return false
        val parsed = runCatching { dayMonthYear.parse(rawDateText) }.getOrNull() ?: return false
        return parsed == form.date && form.isValid()
    }
}
