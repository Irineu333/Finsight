@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.payInvoice

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
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.safeOnDay
import com.neoutils.finance.ui.component.AccountSelector
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.DatePickerModal
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.DateInputTransformation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class PayInvoiceModal(
    private val invoice: Invoice,
    private val currentBillAmount: Double // TODO: improve this 
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<PayInvoiceViewModel> {
            parametersOf(invoice.id)
        }

        val uiState by viewModel.uiState.collectAsState()
        val manager = LocalModalManager.current

        val amount = formatMoneyFromDouble(currentBillAmount)

        val maxDate = invoice.dueDate.coerceAtMost(currentDate)

        val date = rememberTextFieldState(formats.dayMonthYear.format(currentDate.coerceIn(invoice.closingDate, maxDate)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Pagar Fatura",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "O pagamento será do valor total da fatura.",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { },
                label = {
                    Text(text = "Valor da Fatura")
                },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // TODO: Múltiplas Contas - Descomentar quando implementar gerenciamento de contas
            /*
            Spacer(modifier = Modifier.height(8.dp))

            AccountSelector(
                selectedAccount = uiState.selectedAccount,
                accounts = uiState.accounts,
                onAccountSelected = { viewModel.selectAccount(it) },
                modifier = Modifier.fillMaxWidth()
            )
            */

            OutlinedTextField(
                state = date,
                label = {
                    Text(text = "Data do Pagamento")
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
                                    minDate = invoice.closingDate,
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
                    viewModel.payInvoice(
                        date = formats.dayMonthYear.parse(date.text.toString()),
                    )
                },
                enabled = isValidPayment(
                    date = date.text.toString(),
                    minDate = invoice.closingDate,
                    maxDate = maxDate
                ) && uiState.selectedAccount != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Pagar Fatura",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    private fun isValidPayment(
        date: String,
        minDate: LocalDate,
        maxDate: LocalDate
    ): Boolean {
        if (date.isEmpty()) return false

        if (currentBillAmount < 0.0) return false

        val parsedDate = runCatching { formats.dayMonthYear.parse(date) }.getOrElse { return false }

        return parsedDate in minDate..maxDate
    }

    private fun formatMoneyFromDouble(value: Double): String {
        val intValue = (value * 100).toInt()
        val reais = intValue / 100
        val centavos = (intValue % 100).toString().padStart(2, '0')
        return "R$ $reais,$centavos"
    }
}


