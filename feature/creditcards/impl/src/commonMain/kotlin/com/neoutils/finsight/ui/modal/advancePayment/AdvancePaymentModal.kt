@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.advancePayment

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.AmountField
import com.neoutils.finsight.ui.component.CounterpartAmountField
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdvancePaymentModal(
    private val invoice: Invoice,
    private val currentBillAmount: DisplayAmount,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<AdvancePaymentViewModel> {
            parametersOf(invoice.id)
        }

        val uiState by viewModel.uiState.collectAsState()

        val manager = LocalModalManager.current
        val currentDate = koinInject<Clock>().today()

        val amount = rememberTextFieldState()
        val paidAmount = rememberTextFieldState()

        val maxDate = invoice.closingDate.coerceAtMost(currentDate)

        val date = rememberTextFieldState(
            dayMonthYear.format(
                currentDate.coerceIn(invoice.openingDate, maxDate)
            )
        )

        // What is stated goes to the view model, which is where the archive is asked
        // what the other end is worth. The screen never multiplies by a rate.
        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }.collect {
                viewModel.onAction(AdvancePaymentAction.ChangeAmount(it.moneyToDouble()))
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }.collect { text ->
                runCatching { dayMonthYear.parse(text) }.getOrNull()?.let {
                    viewModel.onAction(AdvancePaymentAction.ChangeDate(it))
                }
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
            Text(
                text = stringResource(Res.string.advance_payment_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.advance_payment_description),
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
                    viewModel.onAction(AdvancePaymentAction.SelectAccount(it))
                },
                modifier = Modifier
                    .fillMaxWidth(),
                valueTestTag = "advance_payment_account",
            )

            Spacer(modifier = Modifier.height(8.dp))

            // This amount settles the invoice, so it is in the **card's** currency and
            // always was — which is what keeps the ceiling below a comparison between
            // two figures denominated the same way.
            AmountField(
                state = amount,
                label = stringResource(Res.string.advance_payment_amount_label),
                currency = currentBillAmount.currency,
                modifier = Modifier.testTag("advance_payment_amount"),
            )

            CounterpartAmountField(
                visible = uiState.isCrossCurrency,
                state = paidAmount,
                label = stringResource(
                    Res.string.cross_currency_leaves_label,
                    uiState.selectedAccount?.name.orEmpty(),
                ),
                currency = uiState.selectedAccount?.currency ?: currentBillAmount.currency,
                counterpartAmount = amount.text.toString().moneyToDouble(),
                counterpartCurrency = currentBillAmount.currency,
                suggestion = uiState.suggestion,
                date = runCatching { dayMonthYear.parse(date.text.toString()) }
                    .getOrDefault(currentDate),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = {
                    Text(text = stringResource(Res.string.advance_payment_date_label))
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
                                    minDate = invoice.openingDate,
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
                        AdvancePaymentAction.Submit(
                            amount = amount.text.toString().moneyToDouble(),
                            date = dayMonthYear.parse(date.text.toString()),
                            account = uiState.selectedAccount,
                            paidAmount = paidAmount.text.toString().moneyToDouble(),
                        )
                    )
                },
                enabled = isValidAdvancePayment(
                    amount = amount.text.toString(),
                    paidAmount = paidAmount.text.toString(),
                    isCrossCurrency = uiState.isCrossCurrency,
                    date = date.text.toString(),
                    minDate = invoice.openingDate,
                    maxDate = maxDate,
                    outstandingDebt = currentBillAmount.value.absoluteValue,
                ) && uiState.selectedAccount != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advance_payment_confirm"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.advance_payment_confirm),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

}

/**
 * Whether the advance payment may be submitted.
 *
 * **The ceiling holds over the card's side only**: `amount <= outstandingDebt` compares
 * two figures denominated the same way, while what leaves the account carries no ceiling
 * at all — a limit there would be a limit expressed in the wrong currency.
 *
 * The second field is still *required* when the two ends differ (design D26), which is
 * what keeps the write boundary's same-sign guard unreachable by any path a user can
 * walk.
 *
 * Top-level and `internal` so the rule can be exercised without a screen.
 */
internal fun isValidAdvancePayment(
    amount: String,
    paidAmount: String,
    isCrossCurrency: Boolean,
    date: String,
    minDate: LocalDate,
    maxDate: LocalDate,
    outstandingDebt: Double,
): Boolean {
    if (amount.isEmpty()) return false
    val parsedAmount = amount.moneyToDouble()
    if (parsedAmount <= 0.0) return false
    if (outstandingDebt <= 0.0) return false
    if (parsedAmount > outstandingDebt) return false
    if (isCrossCurrency && paidAmount.moneyToDouble() <= 0.0) return false
    if (date.isEmpty()) return false
    val parsedDate = runCatching { dayMonthYear.parse(date) }.getOrElse { return false }
    return parsedDate in minDate..maxDate
}
