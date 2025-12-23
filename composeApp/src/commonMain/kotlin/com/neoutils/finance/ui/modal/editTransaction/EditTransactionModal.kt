package com.neoutils.finance.ui.modal.editTransaction

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.component.CategorySelector
import com.neoutils.finance.ui.component.CreditCardSelector
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalBottomSheet
import com.neoutils.finance.ui.component.TargetSelector
import com.neoutils.finance.ui.modal.DatePickerModal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.DateInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import kotlinx.datetime.LocalDate
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
        var target by remember { mutableStateOf(transaction.target) }

        val amount = rememberTextFieldState(formatMoneyFromDouble(transaction.amount))
        val title = rememberTextFieldState(transaction.title.orEmpty())
        val date = rememberTextFieldState(formats.dayMonthYear.format(transaction.date))

        var selectedCategory by remember(type) { mutableStateOf(transaction.category) }

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

            AnimatedVisibility(type.isExpense) {
                TargetSelector(
                    selectedTarget = target,
                    onTargetSelected = { target = it },
                    availableTargets = uiState.targets,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }

            AnimatedVisibility(
                type.isExpense && target == Transaction.Target.CREDIT_CARD
            ) {
                CreditCardSelector(
                    creditCards = uiState.creditCards,
                    selectedCreditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = { viewModel.selectCreditCard(it) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }

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
                                    minDate = uiState.minDate.takeIf { target.isCreditCard },
                                    maxDate = uiState.maxDate.takeIf { target.isCreditCard },
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
                            category = selectedCategory?.takeIf { it.type.isAccept(type) },
                            target = target.takeIf { type.isExpense } ?: Transaction.Target.ACCOUNT,
                            creditCard = uiState.selectedCreditCard?.takeIf { target.isCreditCard && type.isExpense },
                            invoice = uiState.currentInvoice.takeIf { target.isCreditCard && type.isExpense },
                        )
                    )
                },
                enabled = showSaveButton(
                    amount = amount.text.toString(),
                    title = title.text.toString(),
                    date = date.text.toString(),
                    category = selectedCategory,
                    target = target,
                    creditCard = uiState.selectedCreditCard,
                    type = type,
                    invoice = uiState.currentInvoice,
                    minDate = uiState.minDate.takeIf { target.isCreditCard },
                    maxDate = uiState.maxDate.takeIf { target.isCreditCard },
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

                Transaction.Type.INCOME,
                Transaction.Type.ADJUSTMENT,
                Transaction.Type.INVOICE_PAYMENT,
                Transaction.Type.ADVANCE_PAYMENT -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
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

                Transaction.Type.EXPENSE,
                Transaction.Type.ADJUSTMENT,
                Transaction.Type.INVOICE_PAYMENT,
                Transaction.Type.ADVANCE_PAYMENT -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
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

    private fun Category.Type.isAccept(type: Transaction.Type): Boolean {
        return when (this) {
            Category.Type.EXPENSE -> type.isExpense
            Category.Type.INCOME -> type.isIncome
        }
    }

    private fun showSaveButton(
        amount: String,
        date: String,
        title: String,
        type: Transaction.Type,
        target: Transaction.Target,
        category: Category?,
        creditCard: CreditCard?,
        invoice: Invoice?,
        minDate: LocalDate?,
        maxDate: LocalDate?
    ): Boolean {

        if (amount.isEmpty()) return false

        if (parseMoneyToDouble(amount) == 0.0) return false

        if (date.isEmpty()) return false

        if (title.isEmpty() && category == null) return false

        if (target.isAccount) return true

        if (type != Transaction.Type.EXPENSE) return false

        val invoice = invoice ?: return false
        val creditCard = creditCard ?: return false

        if (invoice.status != Invoice.Status.OPEN) return false

        if (creditCard.id != invoice.creditCard.id) return false

        val parsedDate = runCatching { formats.dayMonthYear.parse(date) }.getOrElse { return false }

        if (minDate != null && parsedDate < minDate) return false
        if (maxDate != null && parsedDate > maxDate) return false

        return true
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
