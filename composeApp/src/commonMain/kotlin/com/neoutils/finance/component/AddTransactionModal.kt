@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddTransactionModal : Modal {

    private val dateFormat = LocalDate.Format {
        byUnicodePattern("dd/MM/yyyy")
    }

    @Composable
    override fun Content() {

        val repository = koinInject<TransactionRepository>()
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = {
                manager.dismiss()
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {

                var type by remember { mutableStateOf(TransactionEntry.Type.INCOME) }
                val amount = rememberTextFieldState()
                val description = rememberTextFieldState()
                val date = rememberTextFieldState(dateFormat.format(currentDate()))

                TypeToggle(
                    selectedType = type,
                    onTypeSelected = {
                        type = it
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(text = "Amount")
                    },
                    inputTransformation = MoneyInputTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = description,
                    label = {
                        Text(text = "Description")
                    },
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = date,
                    label = {
                        Text(text = "Date")
                    },
                    inputTransformation = DateInputTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                manager.show(
                                    DatePickerModal(
                                        initialDate = dateFormat.parse(date.text.toString()),
                                        onDateSelected = { selectedDate ->
                                            date.edit {
                                                replace(0, length, dateFormat.format(selectedDate))
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
                        scope.launch {
                            repository.insert(
                                TransactionEntry(
                                    type = type,
                                    amount = parseMoneyToDouble(amount.text.toString()),
                                    description = description.text.toString(),
                                    date = dateFormat.parse(date.text.toString()),
                                )
                            )
                            manager.dismiss()
                        }
                    },
                    enabled = showSaveButton(
                        amount = amount.text.toString(),
                        description = description.text.toString(),
                        date = date.text.toString(),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Salvar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    private fun showSaveButton(
        amount: String,
        date: String,
        description: String,
    ): Boolean {

        if (amount.isEmpty()) return false

        if (date.isEmpty()) return false

        if (description.isEmpty()) return false

        return true
    }

    @Composable
    fun TypeToggle(
        selectedType: TransactionEntry.Type,
        onTypeSelected: (TransactionEntry.Type) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(TransactionEntry.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                TransactionEntry.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                TransactionEntry.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Despesa",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = { onTypeSelected(TransactionEntry.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                TransactionEntry.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                TransactionEntry.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Receita",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    private fun currentDate(): LocalDate {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
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
