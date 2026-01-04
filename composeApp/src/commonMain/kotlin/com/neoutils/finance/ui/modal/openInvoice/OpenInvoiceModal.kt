@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.openInvoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.DatePickerModal
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.DateInputTransformation
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class OpenInvoiceModal(
    private val creditCard: CreditCard
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<OpenInvoiceViewModel> {
            parametersOf(creditCard.id)
        }

        val manager = LocalModalManager.current

        val defaultDate = currentDate()
        val date = rememberTextFieldState(formats.dayMonthYear.format(defaultDate))

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Abrir Fatura",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Informe a data de abertura da fatura. O fechamento será no mês seguinte.",
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                state = date,
                label = { Text(text = "Data de Abertura") },
                inputTransformation = DateInputTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            manager.show(
                                DatePickerModal(
                                    initialDate = formats.dayMonthYear.parse(date.text.toString()),
                                    maxDate = currentDate(),
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
                    viewModel.openInvoice(
                        formats.dayMonthYear.parse(date.text.toString()).yearMonth
                    )
                },
                enabled = isValidDate(date.text.toString()),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Abrir Fatura",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    private fun isValidDate(date: String): Boolean {
        if (date.isEmpty()) return false
        val parsedDate = runCatching { formats.dayMonthYear.parse(date) }.getOrElse { return false }
        return parsedDate <= currentDate()
    }

    private fun currentDate(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
}
