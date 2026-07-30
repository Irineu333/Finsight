@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.payInvoice

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
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.CounterpartAmountField
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
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

class PayInvoiceModal(
    private val invoice: Invoice,
    private val currentBillAmount: DisplayAmount,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<PayInvoiceViewModel> {
            parametersOf(invoice.id)
        }

        val uiState by viewModel.uiState.collectAsState()
        val manager = LocalModalManager.current

        // The sheet reads the debt, whichever sign the caller's figure carried, and in
        // the card's own currency — which travels with the figure (design D17).
        val outstandingDebt = DisplayAmount.magnitude(
            value = currentBillAmount.value,
            currency = currentBillAmount.currency,
            isApproximate = currentBillAmount.isApproximate,
        )
        val amount = LocalCurrencyFormatter.current.format(outstandingDebt)

        val maxDate = invoice.dueDate.coerceAtMost(currentDate)

        val date = rememberTextFieldState(
             dayMonthYear.format(
                currentDate.coerceIn(invoice.closingDate, maxDate)
            )
        )

        val paidAmount = rememberTextFieldState()

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }.collect { text ->
                runCatching { dayMonthYear.parse(text) }.getOrNull()?.let {
                    viewModel.onAction(PayInvoiceAction.ChangeDate(it))
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

            // Who takes part, then how much, then when (design D24). The account moved
            // above the amount so that revealing a second field never pushes the
            // selector down under a finger already on it.
            AccountSelector(
                selectedAccount = uiState.selectedAccount,
                accounts = uiState.accounts,
                onAccountSelected = {
                    viewModel.onAction(PayInvoiceAction.SelectAccount(it))
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // This field does not change role: it says what is owed, exact, in the
            // card's own currency. What the account gives up is the field below, and it
            // is a new one — making an existing control editable would have swapped its
            // meaning.
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

            CounterpartAmountField(
                visible = uiState.isCrossCurrency,
                state = paidAmount,
                label = stringResource(
                    Res.string.cross_currency_leaves_label,
                    uiState.selectedAccount?.name.orEmpty(),
                ),
                currency = uiState.selectedAccount?.currency ?: outstandingDebt.currency,
                counterpartAmount = outstandingDebt.value,
                counterpartCurrency = outstandingDebt.currency,
                suggestion = uiState.suggestion,
                date = runCatching { dayMonthYear.parse(date.text.toString()) }
                    .getOrDefault(currentDate),
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
                                    initialDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull(),
                                    minDate = invoice.closingDate,
                                    maxDate = maxDate,
                                    onDateSelected = { selectedDate ->
                                        date.edit {
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.onAction(
                        PayInvoiceAction.Submit(
                            date = dayMonthYear.parse(date.text.toString()),
                            account = uiState.selectedAccount,
                            paidAmount = paidAmount.text.toString().moneyToDouble(),
                        )
                    )
                },
                enabled = isValidInvoicePayment(
                    date = date.text.toString(),
                    minDate = invoice.closingDate,
                    maxDate = maxDate,
                    outstandingDebt = outstandingDebt.value,
                    paidAmount = paidAmount.text.toString(),
                    isCrossCurrency = uiState.isCrossCurrency,
                ) && uiState.selectedAccount != null,
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

}

/**
 * Whether the payment may be submitted — and, when the paying account is denominated
 * differently, that what leaves it was stated.
 *
 * Covering the second field is the validation change easiest to forget (design D26): it
 * is what keeps the write boundary's same-sign guard unreachable by any path a user can
 * walk, since a zero is the only way to reach it and this refuses one first.
 *
 * Top-level and `internal` so the rule can be exercised without a screen.
 */
internal fun isValidInvoicePayment(
    date: String,
    minDate: LocalDate,
    maxDate: LocalDate,
    outstandingDebt: Double,
    paidAmount: String,
    isCrossCurrency: Boolean,
): Boolean {
    if (date.isEmpty()) return false

    if (outstandingDebt <= 0.0) return false

    if (isCrossCurrency && paidAmount.moneyToDouble() <= 0.0) return false

    val parsedDate = runCatching { dayMonthYear.parse(date) }.getOrElse { return false }

    return parsedDate in minDate..maxDate
}
