@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.component.DateInputTransformation
import com.neoutils.finance.component.MoneyInputTransformation
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.manager.LocalModalManager
import com.neoutils.finance.manager.Modal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.koin.compose.koinInject
import kotlin.collections.sumOf
import kotlin.time.ExperimentalTime

class EditTransactionModal(
    private val transaction: TransactionEntry,
) : Modal {

    private val dateFormat = LocalDate.Format {
        byUnicodePattern("dd/MM/yyyy")
    }

    private val insufficientBalance = @Composable {
        Text("Saldo insuficiente")
    }

    @Composable
    override fun Content() {
        val repository = koinInject<TransactionRepository>()
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        val allTransactions by repository.getAllTransactions().collectAsState(initial = emptyList())

        val currentBalance by remember {
            derivedStateOf {
                calculateBalance(allTransactions.filter { it.id != transaction.id })
            }
        }

        var type by remember {
            mutableStateOf(
                if (transaction.type == TransactionEntry.Type.ADJUSTMENT) {
                    TransactionEntry.Type.EXPENSE
                } else {
                    transaction.type
                }
            )
        }

        val amount = rememberTextFieldState(formatMoneyFromDouble(transaction.amount))
        val title = rememberTextFieldState(transaction.description)
        val date = rememberTextFieldState(dateFormat.format(transaction.date))

        val expenseAmount by remember { derivedStateOf { parseMoneyToDouble(amount.text.toString()) } }

        val isInsufficientBalance by remember {
            derivedStateOf {
                when (type) {
                    TransactionEntry.Type.EXPENSE -> expenseAmount > currentBalance
                    else -> false
                }
            }
        }

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
                Text(
                    text = "Editar Transação",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                TypeToggle(
                    selectedType = type,
                    onTypeSelected = {
                        type = it
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    state = title,
                    label = {
                        Text(text = "Título")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    isError = isInsufficientBalance,
                    supportingText = insufficientBalance.takeIf { isInsufficientBalance },
                    modifier = Modifier.fillMaxWidth(),
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

                            repository.update(
                                transaction.copy(
                                    type = type,
                                    amount = parseMoneyToDouble(amount.text.toString()),
                                    description = title.text.toString(),
                                    date = dateFormat.parse(date.text.toString()),
                                )
                            )

                            manager.dismiss()
                        }
                    },
                    enabled = showSaveButton(
                        amount = amount.text.toString(),
                        title = title.text.toString(),
                        date = date.text.toString(),
                        isInsufficientBalance = isInsufficientBalance
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
        title: String,
        isInsufficientBalance: Boolean
    ): Boolean {
        if (amount.isEmpty()) return false
        if (date.isEmpty()) return false
        if (title.isEmpty()) return false
        if (isInsufficientBalance) return false
        return true
    }

    private fun calculateBalance(transactions: List<TransactionEntry>): Double {
        return transactions.sumOf { transaction ->
            when (transaction.type) {
                TransactionEntry.Type.INCOME -> transaction.amount
                TransactionEntry.Type.EXPENSE -> -transaction.amount
                TransactionEntry.Type.ADJUSTMENT -> transaction.amount
            }
        }
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

                TransactionEntry.Type.INCOME, TransactionEntry.Type.ADJUSTMENT -> {
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

                TransactionEntry.Type.EXPENSE, TransactionEntry.Type.ADJUSTMENT -> {
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

    private fun formatMoneyFromDouble(value: Double): String {
        val cents = (value * 100).toLong()
        val reais = cents / 100
        val centavos = cents % 100

        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        return "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
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
