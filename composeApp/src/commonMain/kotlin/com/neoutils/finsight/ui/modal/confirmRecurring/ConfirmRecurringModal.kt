@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.confirmRecurring

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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_confirm_amount_label
import com.neoutils.finsight.resources.recurring_confirm_button
import com.neoutils.finsight.resources.recurring_confirm_date_label
import com.neoutils.finsight.resources.recurring_confirm_skip
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.view_recurring_account_label
import com.neoutils.finsight.resources.view_recurring_category_label
import com.neoutils.finsight.resources.view_recurring_credit_card_label
import com.neoutils.finsight.resources.view_recurring_type_label
import com.neoutils.finsight.ui.component.InvoiceSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.DatePickerModal
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
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
    private val recurring: Recurring,
    private val targetDate: LocalDate,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val viewModel = koinViewModel<ConfirmRecurringViewModel> {
            parametersOf(recurring, targetDate)
        }
        val uiState by viewModel.uiState.collectAsState()

        val currencyFormatter = LocalCurrencyFormatter.current
        val amount = rememberTextFieldState(currencyFormatter.format(recurring.amount))
        val dateText = rememberTextFieldState(dayMonthYear.format(targetDate))

        val typeLabel = if (recurring.type.isIncome) {
            stringResource(Res.string.recurring_income)
        } else {
            stringResource(Res.string.recurring_expense)
        }

        LaunchedEffect(uiState.confirmDate) {
            val formatted = dayMonthYear.format(uiState.confirmDate)
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = recurring.title ?: recurring.category?.name ?: "",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
            )

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

            recurring.category?.let { category ->
                OutlinedTextField(
                    value = category.name,
                    onValueChange = {},
                    label = { Text(text = stringResource(Res.string.view_recurring_category_label)) },
                    readOnly = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            recurring.account?.let { account ->
                OutlinedTextField(
                    value = account.name,
                    onValueChange = {},
                    label = { Text(text = stringResource(Res.string.view_recurring_account_label)) },
                    readOnly = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            recurring.creditCard?.let { creditCard ->
                OutlinedTextField(
                    value = creditCard.name,
                    onValueChange = {},
                    label = { Text(text = stringResource(Res.string.view_recurring_credit_card_label)) },
                    readOnly = true,
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                InvoiceSelector(
                    invoices = uiState.invoices,
                    invoice = uiState.selectedInvoice,
                    onInvoiceSelected = {
                        viewModel.onAction(ConfirmRecurringAction.InvoiceSelected(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
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

            OutlinedTextField(
                state = dateText,
                label = { Text(text = stringResource(Res.string.recurring_confirm_date_label)) },
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = uiState.confirmDate,
                                    maxDate = currentDate,
                                    onDateSelected = { date ->
                                        viewModel.onAction(ConfirmRecurringAction.DateChanged(date), amount.text.toString())
                                    }
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.CalendarToday,
                            contentDescription = null,
                            tint = colorScheme.primary,
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        viewModel.onAction(ConfirmRecurringAction.Confirm, amount.text.toString())
                    },
                    enabled = amount.text.toString().moneyToDouble() > 0.0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.onAction(ConfirmRecurringAction.Skip) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_skip),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
