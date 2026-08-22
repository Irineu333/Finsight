package com.neoutils.finsight.ui.modal.invoicePayment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.CounterpartAmountField
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.InvoiceSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Paying an invoice — the only sheet that does, whatever state the invoice is in.
 *
 * [invoiceId] is a **pre-selection**, not the subject: opened from an invoice in view it
 * arrives with that one chosen, and opened without context it lets the user choose. What
 * is owed is read from whichever invoice is selected, so switching invoice re-denominates
 * and re-bounds the form instead of leaving a stale figure behind.
 *
 * The amount field means one thing throughout — how much of this invoice is being paid
 * now. What changes with the mode is whether it accepts typing (design D6).
 */
class InvoicePaymentModal(
    private val invoiceId: Long? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<InvoicePaymentViewModel> {
            parametersOf(invoiceId)
        }

        val uiState by viewModel.uiState.collectAsState()
        val manager = LocalModalManager.current
        val formatter = LocalCurrencyFormatter.current

        when (val state = uiState) {
            InvoicePaymentUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
            }

            is InvoicePaymentUiState.Content -> {
                val amount = rememberTextFieldState()
                val paidAmount = rememberTextFieldState()

                // An editing buffer, not the form: it reports to the ViewModel, which
                // owns the date, and takes back what the ViewModel decides when the
                // invoice reprojects it.
                val dateState = rememberTextFieldState(state.date)

                LaunchedEffect(Unit) {
                    snapshotFlow { dateState.text.toString() }
                        .drop(1)
                        .collect { viewModel.onAction(InvoicePaymentAction.ChangeDate(it)) }
                }

                LaunchedEffect(state.date) {
                    if (state.date != dateState.text.toString()) {
                        dateState.edit { replace(0, length, state.date) }
                    }
                }

                // What is stated goes to the view model, which is where the archive is
                // asked what the other end is worth. The screen never multiplies by a rate.
                LaunchedEffect(Unit) {
                    snapshotFlow { amount.text.toString() }.collect {
                        viewModel.onAction(InvoicePaymentAction.ChangeAmount(it.moneyToDouble()))
                    }
                }

                // Changing card or invoice withdraws both figures: digits denominated in
                // one currency do not survive under another's symbol, and a ceiling
                // inherited from another invoice is the wrong ceiling. Where the payment
                // discharges the invoice the field then states what is owed — which is
                // what "only the whole of it" means on screen.
                LaunchedEffect(
                    state.selectedCreditCard?.id,
                    state.selectedInvoice?.id,
                    state.settles,
                    state.debtAmount,
                ) {
                    paidAmount.clearText()

                    if (state.settles && state.debtAmount != null) {
                        amount.setTextAndPlaceCursorAtEnd(formatter.format(state.debtAmount))
                    } else {
                        amount.clearText()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // One head, and it is the verb: it already names the mode in force,
                    // so a line under it saying the same thing is a second title.
                    Text(
                        text = stringResource(state.label),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Who takes part, then how much, then when (design D24) — and the
                    // hierarchy inside the first: card governs invoice, invoice governs
                    // date.
                    CreditCardSelector(
                        creditCards = state.creditCards,
                        creditCard = state.selectedCreditCard,
                        onCreditCardSelected = {
                            viewModel.onAction(InvoicePaymentAction.SelectCreditCard(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        valueTestTag = "invoice_payment_card",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    InvoiceSelector(
                        invoices = state.invoices,
                        invoice = state.selectedInvoice,
                        onInvoiceSelected = {
                            viewModel.onAction(InvoicePaymentAction.SelectInvoice(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        valueTestTag = "invoice_payment_invoice",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AccountSelector(
                        selectedAccount = state.selectedAccount,
                        accounts = state.accounts,
                        onAccountSelected = {
                            viewModel.onAction(InvoicePaymentAction.SelectAccount(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        valueTestTag = "invoice_payment_account",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // One field, one meaning: how much of this invoice is being paid now,
                    // in the **card's** currency — which is what keeps the ceiling a
                    // comparison between two figures denominated the same way. Where the
                    // payment discharges the invoice it states the figure instead of
                    // accepting one.
                    OutlinedTextField(
                        state = amount,
                        label = {
                            Text(text = stringResource(Res.string.invoice_payment_amount_label))
                        },
                        inputTransformation = rememberMoneyInputTransformation(
                            currency = state.invoiceCurrency.orEmpty(),
                            state = amount,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        readOnly = state.settles,
                        enabled = !state.settles,
                        supportingText = if (state.selectedInvoice != null && !state.hasDebt) {
                            { Text(text = stringResource(Res.string.invoice_payment_no_debt)) }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_payment_amount"),
                    )

                    CounterpartAmountField(
                        visible = state.isCrossCurrency,
                        state = paidAmount,
                        label = stringResource(
                            Res.string.cross_currency_leaves_label,
                            state.selectedAccount?.name.orEmpty(),
                        ),
                        currency = state.selectedAccount?.currency
                            ?: state.invoiceCurrency.orEmpty(),
                        counterpartAmount = amount.text.toString().moneyToDouble(),
                        counterpartCurrency = state.invoiceCurrency.orEmpty(),
                        suggestion = state.suggestion,
                        date = runCatching { dayMonthYear.parse(dateState.text.toString()) }
                            .getOrDefault(state.today),
                        modifier = Modifier.testTag("invoice_payment_paid_amount"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        state = dateState,
                        label = {
                            Text(text = stringResource(Res.string.invoice_payment_date_label))
                        },
                        inputTransformation = DateInputTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    manager.show(
                                        DatePickerModal(
                                            initialDate = runCatching {
                                                dayMonthYear.parse(dateState.text.toString())
                                            }.getOrNull(),
                                            // The same window the field is bound by: a
                                            // date the domain would refuse is never on
                                            // offer here.
                                            minDate = state.window?.start,
                                            maxDate = state.window?.endInclusive,
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
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_payment_date"),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.onAction(
                                InvoicePaymentAction.Submit(
                                    amount = amount.text.toString().moneyToDouble(),
                                    paidAmount = paidAmount.text.toString().moneyToDouble(),
                                    account = state.selectedAccount,
                                )
                            )
                        },
                        enabled = canSubmitInvoicePayment(
                            amount = amount.text.toString(),
                            paidAmount = paidAmount.text.toString(),
                            isCrossCurrency = state.isCrossCurrency,
                            settles = state.settles,
                            date = dateState.text.toString(),
                            window = state.window,
                            outstandingDebt = state.outstandingDebt,
                        ) && state.selectedAccount != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_payment_confirm"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(state.label),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
