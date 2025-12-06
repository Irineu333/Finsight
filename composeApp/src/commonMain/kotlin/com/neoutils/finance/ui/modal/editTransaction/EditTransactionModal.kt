package com.neoutils.finance.ui.modal.editTransaction

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.component.CategorySelector
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.modal.DatePickerModal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.DateInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class EditTransactionModal(
    private val transaction: Transaction,
) : ModalBottomSheet() {

    private val formats = DateFormats()

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditTransactionViewModel>(key = key) { parametersOf(transaction) }
        val manager = LocalModalManager.current
        val uiState by viewModel.uiState.collectAsState()

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
                color = colorScheme.onSurface
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
                categories = when (type) {
                    Transaction.Type.INCOME -> uiState.incomeCategories
                    Transaction.Type.EXPENSE -> uiState.expenseCategories
                    else -> emptyList()
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
                    viewModel.updateTransaction(
                        transaction = transaction.copy(
                            type = type,
                            amount = parseMoneyToDouble(amount.text.toString()),
                            title = title.text.toString().ifBlank { null },
                            date = formats.dayMonthYear.parse(date.text.toString()),
                            category = selectedCategory,
                        )
                    )
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
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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