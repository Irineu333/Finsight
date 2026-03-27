package com.neoutils.finsight.ui.modal.editInvoiceBalance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.InvoiceSelector
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.edit_invoice_balance_label
import com.neoutils.finsight.resources.edit_invoice_balance_save
import com.neoutils.finsight.resources.edit_invoice_balance_title
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
                val balanceState = rememberTextFieldState(
                    formatMoney((state.currentBalance * 100).toLong(), currencyFormatter)
                )

                val newBalance by remember {
                    derivedStateOf {
                        parseMoneyToDouble(balanceState.text.toString())
                    }
                }

                val adjustment by remember {
                    derivedStateOf {
                        newBalance - state.currentBalance
                    }
                }

                LaunchedEffect(state.currentBalance) {
                    balanceState.edit {
                        replace(0, length, formatMoney((state.currentBalance * 100).toLong(), currencyFormatter))
                    }
                }

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
                        label = { Text(stringResource(Res.string.edit_invoice_balance_label)) },
                        state = balanceState,
                        inputTransformation = rememberMoneyInputTransformation(),
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
                                            adjustment = currentAdjustment,
                                            modifier = Modifier.padding(end = 16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.onAction(EditInvoiceBalanceAction.Submit(newBalance)) },
                        enabled = balanceState.text.isNotBlank() && newBalance != state.currentBalance,
                        modifier = Modifier.fillMaxWidth(),
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
        adjustment: Double,
        modifier: Modifier = Modifier
    ) {
        val formatter = LocalCurrencyFormatter.current
        val isPayment = adjustment < 0
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
                text = formatter.formatWithSign(adjustment),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    private fun formatMoney(cents: Long, formatter: com.neoutils.finsight.extension.CurrencyFormatter): String {
        val isNegative = cents < 0
        val formatted = formatter.format(kotlin.math.abs(cents).toDouble() / 100)
        return if (isNegative) "-$formatted" else formatted
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val isNegative = formatted.startsWith("-")
        val digits = formatted.filter { it.isDigit() }
        val cents = digits.toLongOrNull() ?: return 0.0
        return (if (isNegative) -cents else cents).toDouble() / 100
    }
}
