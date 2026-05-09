@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.modal.date.DatePickerModal
import com.neoutils.finsight.core.ui.util.rememberMoneyInputTransformation
import com.neoutils.finsight.core.utils.util.dayMonthYear
import com.neoutils.finsight.feature.accounts.component.AccountSelector
import com.neoutils.finsight.feature.creditCards.component.CreditCardSelector
import com.neoutils.finsight.feature.creditCards.component.InvoiceSelector
import com.neoutils.finsight.feature.recurring.resources.*
import com.neoutils.finsight.feature.transactions.component.TargetSelector
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class ConfirmRecurringModal(
    private val recurringId: Long,
    private val targetDate: LocalDate,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ConfirmRecurringViewModel> {
            parametersOf(recurringId, targetDate)
        }
        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            ConfirmRecurringUiState.Loading -> LoadingContent()
            is ConfirmRecurringUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
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
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun Content(
        state: ConfirmRecurringUiState.Content,
        onAction: (ConfirmRecurringAction) -> Unit,
    ) {
        val modalManager = LocalModalManager.current
        val form = state.form

        val amount = rememberTextFieldState(form.amount)
        val dateText = rememberTextFieldState(dayMonthYear.format(form.date))

        val typeLabel = if (form.type.isIncome) {
            stringResource(Res.string.recurring_income)
        } else {
            stringResource(Res.string.recurring_expense)
        }

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .drop(1)
                .collect { onAction(ConfirmRecurringAction.AmountChanged(it)) }
        }

        LaunchedEffect(form.date) {
            val formatted = dayMonthYear.format(form.date)
            if (dateText.text.toString() != formatted) {
                dateText.edit { replace(0, length, formatted) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = form.label,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = typeLabel,
                onValueChange = {},
                label = { Text(text = stringResource(Res.string.view_recurring_type_label)) },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(form.type.isExpense) {
                TargetSelector(
                    selectedTarget = form.target,
                    onTargetSelected = { target ->
                        onAction(ConfirmRecurringAction.TargetSelected(target))
                    },
                    availableTargets = state.targets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            AnimatedVisibility(form.target.isAccount || form.type.isIncome) {
                AccountSelector(
                    selectedAccount = form.account,
                    accounts = state.accounts,
                    onAccountSelected = { account ->
                        onAction(ConfirmRecurringAction.AccountSelected(account))
                    },
                    label = stringResource(Res.string.view_recurring_account_label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            AnimatedVisibility(form.target.isCreditCard && form.type.isExpense) {
                CreditCardSelector(
                    creditCards = state.creditCards,
                    creditCard = form.creditCard,
                    onCreditCardSelected = { card ->
                        onAction(ConfirmRecurringAction.CreditCardSelected(card))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            AnimatedVisibility(form.target.isCreditCard && form.type.isExpense) {
                InvoiceSelector(
                    invoices = state.invoices,
                    invoice = form.invoice,
                    onInvoiceSelected = {
                        onAction(ConfirmRecurringAction.InvoiceSelected(it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            OutlinedTextField(
                state = amount,
                label = { Text(text = stringResource(Res.string.recurring_confirm_amount_label)) },
                inputTransformation = rememberMoneyInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = dateText,
                label = { Text(text = stringResource(Res.string.recurring_confirm_date_label)) },
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = form.date,
                                    maxDate = currentDate,
                                    onDateSelected = { date ->
                                        onAction(ConfirmRecurringAction.DateChanged(date))
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
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { onAction(ConfirmRecurringAction.Skip) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_skip),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = { onAction(ConfirmRecurringAction.Confirm) },
                    enabled = form.isValid(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
