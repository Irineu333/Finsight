@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.advancePayment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.DatePickerModal
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.MoneyInputTransformation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AdvancePaymentModal(
    private val invoice: Invoice,
    private val currentBillAmount: Double
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AdvancePaymentViewModel> {
            parametersOf(invoice.id)
        }

        val uiState by viewModel.uiState.collectAsState()
        val manager = LocalModalManager.current

        val amount = rememberTextFieldState()

        val maxDate = invoice.closingDate.coerceAtMost(currentDate)

        val date = rememberTextFieldState(formats.dayMonthYear.format(currentDate.coerceIn(invoice.openingDate, maxDate)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Antecipar Pagamento",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Pague parte da fatura antes do fechamento.",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                state = amount,
                label = {
                    Text(text = "Valor")
                },
                inputTransformation = MoneyInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AccountSelector(
                selectedAccount = uiState.selectedAccount,
                accounts = uiState.accounts,
                onAccountSelected = { viewModel.selectAccount(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = {
                    Text(text = "Data")
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
                                    initialDate = formats.dayMonthYear.parse(date.text.toString()),
                                    minDate = invoice.openingDate,
                                    maxDate = maxDate,
                                    onDateSelected = { selectedDate ->
                                        date.edit {
                                            replace(0, length, formats.dayMonthYear.format(selectedDate))
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
                    viewModel.advancePayment(
                        amount = parseMoneyToDouble(amount.text.toString()),
                        date = formats.dayMonthYear.parse(date.text.toString()),
                    )
                },
                enabled = isValidPayment(
                    amount = amount.text.toString(),
                    date = date.text.toString(),
                    minDate = invoice.openingDate,
                    maxDate = maxDate,
                    outstandingDebt = if (currentBillAmount < 0.0) {
                        -currentBillAmount
                    } else {
                        currentBillAmount
                    }.coerceAtLeast(0.0),
                ) && uiState.selectedAccount != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Antecipar Pagamento",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    private fun isValidPayment(
        amount: String,
        date: String,
        minDate: LocalDate,
        maxDate: LocalDate,
        outstandingDebt: Double,
    ): Boolean {
        if (amount.isEmpty()) return false
        val parsedAmount = parseMoneyToDouble(amount)
        if (parsedAmount <= 0.0) return false
        if (outstandingDebt <= 0.0) return false
        if (parsedAmount > outstandingDebt) return false
        if (date.isEmpty()) return false
        val parsedDate = runCatching { formats.dayMonthYear.parse(date) }.getOrElse { return false }
        return parsedDate in minDate..maxDate
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }
}
