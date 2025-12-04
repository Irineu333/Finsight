@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finance.ui.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.neoutils.finance.util.DateInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.CategorySelector
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.DateFormats
import kotlinx.coroutines.launch
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime

class EditTransactionModal(
    private val transaction: Transaction,
) : ModalBottomSheet {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val repository = koinInject<ITransactionRepository>()
        val manager = LocalModalManager.current
        val scope = rememberCoroutineScope()

        var type by remember { mutableStateOf(transaction.type) }

        val amount = rememberTextFieldState(formatMoneyFromDouble(transaction.amount))
        val title = rememberTextFieldState(transaction.title.orEmpty())
        val date = rememberTextFieldState(formats.dayMonthYear.format(transaction.date))

        var selectedCategory by remember(type) { mutableStateOf<Category?>(null) }

        LaunchedEffect(transaction.category) {
            selectedCategory = transaction.category
        }

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

                CategorySelector(
                    selectedCategory = selectedCategory,
                    categoryType = when(type) {
                        Transaction.Type.INCOME -> Category.Type.INCOME
                        Transaction.Type.EXPENSE -> Category.Type.EXPENSE
                        else -> error("Invalid type")
                    },
                    onCategorySelected = { selectedCategory = it },
                    modifier = Modifier.fillMaxWidth()
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
                                        initialDate = formats.dayMonthYear.parse(date.text.toString()),
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
                                    title = title.text.toString().ifBlank { null },
                                    date = formats.dayMonthYear.parse(date.text.toString()),
                                    category = selectedCategory,
                                )
                            )

                            manager.dismiss()
                        }
                    },
                    enabled = showSaveButton(
                        amount = amount.text.toString(),
                        title = title.text.toString(),
                        date = date.text.toString(),
                        hasCategory = selectedCategory != null
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

    private fun showSaveButton(
        amount: String,
        date: String,
        title: String,
        hasCategory: Boolean
    ): Boolean {
        if (amount.isEmpty()) return false
        if (date.isEmpty()) return false
        if (title.isEmpty() && !hasCategory) return false
        return true
    }

    @Composable
    fun TypeToggle(
        selectedType: Transaction.Type,
        onTypeSelected: (Transaction.Type) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(Transaction.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Transaction.Type.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                Transaction.Type.INCOME, Transaction.Type.ADJUSTMENT -> {
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
            onClick = { onTypeSelected(Transaction.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Transaction.Type.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                Transaction.Type.EXPENSE, Transaction.Type.ADJUSTMENT -> {
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
