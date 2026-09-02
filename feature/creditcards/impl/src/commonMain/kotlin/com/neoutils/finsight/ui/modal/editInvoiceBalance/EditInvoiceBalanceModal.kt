package com.neoutils.finsight.ui.modal.editInvoiceBalance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.InvoiceSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_date_label
import com.neoutils.finsight.resources.edit_invoice_balance_label
import com.neoutils.finsight.resources.edit_invoice_balance_save
import com.neoutils.finsight.resources.edit_invoice_balance_title
import com.neoutils.finsight.resources.transaction_date_outside_invoice
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
class EditInvoiceBalanceModal(
    private val initialInvoice: Invoice,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditInvoiceBalanceViewModel> {
            parametersOf(initialInvoice)
        }

        val uiState by viewModel.uiState.collectAsState()
        val manager = LocalModalManager.current

        val currencyFormatter = LocalCurrencyFormatter.current
        when (val state = uiState) {
            EditInvoiceBalanceUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.edit_invoice_balance_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
            }

            is EditInvoiceBalanceUiState.Content -> {
                // An editing buffer, not the form: it reports to the ViewModel, which owns
                // the date, and takes back what the ViewModel decides when the invoice
                // reprojects it.
                val dateState = rememberTextFieldState(state.date)

                LaunchedEffect(Unit) {
                    snapshotFlow { dateState.text.toString() }
                        .drop(1)
                        .collect { viewModel.onAction(EditInvoiceBalanceAction.ChangeDate(it)) }
                }

                LaunchedEffect(state.date) {
                    if (state.date != dateState.text.toString()) {
                        dateState.edit { replace(0, length, state.date) }
                    }
                }

                val balanceState = rememberTextFieldState(
                    currencyFormatter.format(state.balanceAmount)
                )

                val newBalance by remember {
                    derivedStateOf {
                        balanceState.text.toString().moneyToDouble()
                    }
                }

                val adjustment by remember {
                    derivedStateOf {
                        newBalance - state.currentBalance
                    }
                }

                LaunchedEffect(state.currentBalance) {
                    balanceState.edit {
                        replace(0, length, currencyFormatter.format(state.balanceAmount))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.edit_invoice_balance_title),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CreditCardSelector(
                        creditCards = state.creditCards,
                        creditCard = state.selectedCreditCard,
                        onCreditCardSelected = { creditCard ->
                            viewModel.onAction(EditInvoiceBalanceAction.SelectCreditCard(creditCard))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    InvoiceSelector(
                        invoices = state.editableInvoices,
                        invoice = state.selectedInvoice,
                        onInvoiceSelected = { invoice ->
                            viewModel.onAction(EditInvoiceBalanceAction.SelectInvoice(invoice))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        state = dateState,
                        label = { Text(stringResource(Res.string.add_transaction_date_label)) },
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
                                            initialDate = runCatching {
                                                dayMonthYear.parse(dateState.text.toString())
                                            }.getOrNull(),
                                            // Today and nothing else: a correction happens
                                            // over the cycle, not inside it, so the
                                            // invoice's window bounds it in neither
                                            // direction (design D3).
                                            maxDate = state.today,
                                            onDateSelected = { selectedDate ->
                                                dateState.edit {
                                                    replace(0, length, dayMonthYear.format(selectedDate))
                                                }
                                            }
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        // Said, not corrected: the value reaches this invoice through the
                        // dimension either way, so a date outside its period changes
                        // nothing and blocks nothing.
                        supportingText = if (state.isDateOutsideInvoice) {
                            { Text(text = stringResource(Res.string.transaction_date_outside_invoice)) }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        label = { Text(stringResource(Res.string.edit_invoice_balance_label)) },
                        state = balanceState,
                        inputTransformation = rememberMoneyInputTransformation(state.currency, balanceState),
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = adjustment != 0.0,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                if (adjustment != 0.0) {
                                    AnimatedContent(
                                        targetState = adjustment,
                                        transitionSpec = {
                                            fadeIn() togetherWith fadeOut()
                                        }
                                    ) { currentAdjustment ->
                                        AdjustmentLabel(
                                            // The direction of an adjustment is not in
                                            // its label, so it is spelled out — by the
                                            // type, in the card's currency.
                                            adjustment = DisplayAmount.explicitSign(
                                                value = currentAdjustment,
                                                currency = state.currency,
                                                isApproximate = false,
                                            ),
                                            modifier = Modifier.padding(end = 16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_invoice_balance_amount")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.onAction(EditInvoiceBalanceAction.Submit(newBalance)) },
                        enabled = balanceState.text.isNotBlank() && newBalance != state.currentBalance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_invoice_balance_save"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Adjustment),
                    ) {
                        Text(
                            text = stringResource(Res.string.edit_invoice_balance_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AdjustmentLabel(
        adjustment: DisplayAmount,
        modifier: Modifier = Modifier
    ) {
        val formatter = LocalCurrencyFormatter.current
        val isPayment = adjustment.value < 0
        val color = if (isPayment) Income else Expense
        val icon = if (isPayment) Icons.Default.CreditCard else Icons.Default.ArrowDownward

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = formatter.format(adjustment),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
