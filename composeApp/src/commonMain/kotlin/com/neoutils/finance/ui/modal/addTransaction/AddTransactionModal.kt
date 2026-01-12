@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addTransaction

import androidx.compose.animation.AnimatedVisibility
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
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.extension.isAccept
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.*
import com.neoutils.finance.ui.modal.DatePickerModal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.util.DateInputTransformation
import com.neoutils.finance.util.MoneyInputTransformation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val formats = DateFormats()

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AddTransactionModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val manager = LocalModalManager.current

        val viewModel = koinViewModel<AddTransactionViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        var type by remember { mutableStateOf(Transaction.Type.EXPENSE) }
        var target by remember { mutableStateOf(Transaction.Target.ACCOUNT) }

        val amount = rememberTextFieldState()
        val title = rememberTextFieldState()
        val date = rememberTextFieldState(formats.dayMonthYear.format(currentDate))

        var selectedCategory by remember { mutableStateOf<Category?>(null) }

        LaunchedEffect(type) {
            selectedCategory = selectedCategory?.takeIf {
                it.type.isAccept(type)
            }
        }

        val form by remember {
            derivedStateOf {
                TransactionForm.from(
                    type = type,
                    amount = amount.text.toString(),
                    title = title.text.toString(),
                    date = date.text.toString(),
                    category = selectedCategory,
                    target = target,
                    creditCard = uiState.selectedCreditCard,
                    invoice = uiState.selectedInvoice,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {

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
                    creditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = { viewModel.selectCreditCard(it) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                )
            }

            AnimatedVisibility(
                type.isExpense && target == Transaction.Target.CREDIT_CARD && uiState.selectedCreditCard != null
            ) {
                InvoiceSelector(
                    invoices = uiState.availableInvoices,
                    selectedInvoice = uiState.selectedInvoice,
                    onInvoiceSelected = { viewModel.selectInvoice(it) },
                    onCreateFutureInvoice = { viewModel.createFutureInvoice() },
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
                    else -> listOf()
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
                                    maxDate = currentDate,
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
                    viewModel.addTransaction(form)
                },
                enabled = form.isValid(),
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
}
