@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addInstallment

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class AddInstallmentModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AddInstallmentViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        val modalManager = LocalModalManager.current

        val snackbarHostState = remember { SnackbarHostState() }

        val title = rememberTextFieldState()
        val amount = rememberTextFieldState()
        val date = rememberTextFieldState(dayMonthYear.format(currentDate))

        var selectedCategory by remember { mutableStateOf<Category?>(null) }
        var installments by remember { mutableStateOf(2) }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is AddInstallmentEvent.ShowError -> {
                        snackbarHostState.showSnackbar(event.message.asString())
                    }
                }
            }
        }

        val form by remember {
            derivedStateOf {
                TransactionForm.from(
                    type = Transaction.Type.EXPENSE,
                    amount = amount.text.toString(),
                    title = title.text.toString(),
                    date = date.text.toString(),
                    category = selectedCategory,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCard = uiState.selectedCreditCard,
                    invoiceDueMonth = uiState.invoiceSelection?.dueMonth,
                    account = null,
                    installments = installments,
                )
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(Res.string.add_installment_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    state = title,
                    label = {
                        Text(text = stringResource(Res.string.add_installment_title_label))
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                CategorySelector(
                    selectedCategory = selectedCategory,
                    categories = uiState.categories,
                    onCategorySelected = { selectedCategory = it },
                    onEmpty = { modalManager.show(CategoryFormModal()) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                CreditCardSelector(
                    creditCards = uiState.creditCards,
                    creditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = {
                        viewModel.onAction(AddInstallmentAction.SelectCreditCard(it))
                    },
                    onEmpty = { modalManager.show(CreditCardFormModal()) },
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.invoiceSelection?.let { selection ->
                    Spacer(modifier = Modifier.height(8.dp))

                    InvoiceMonthNavigator(
                        selection = selection,
                        onNavigate = {
                            viewModel.onAction(AddInstallmentAction.NavigateToMonth(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.add_installment_initial_invoice),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(text = stringResource(Res.string.add_installment_amount_label))
                    },
                    inputTransformation = rememberMoneyInputTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        InstallmentCounter(
                            state = InstallmentState(
                                count = installments,
                                total = amount.text.toString().moneyToDouble(),
                            ),
                            onInstallmentsChange = {
                                installments = it.coerceAtLeast(2)
                            },
                            minCount = 2,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = date,
                    label = {
                        Text(text = stringResource(Res.string.add_installment_date_label))
                    },
                    inputTransformation = DateInputTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                modalManager.show(
                                    DatePickerModal(
                                        initialDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull(),
                                        maxDate = currentDate,
                                        onDateSelected = { selectedDate ->
                                            date.edit {
                                                replace(
                                                    0,
                                                    length,
                                                    dayMonthYear.format(selectedDate),
                                                )
                                            }
                                        },
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.onAction(
                            AddInstallmentAction.Submit(
                                form = form,
                                installments = installments,
                            )
                        )
                    },
                    enabled = form.isValid()
                            && uiState.invoiceSelection != null
                            && !uiState.isInvoiceBlocked
                            && installments > 1,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.add_installment_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
