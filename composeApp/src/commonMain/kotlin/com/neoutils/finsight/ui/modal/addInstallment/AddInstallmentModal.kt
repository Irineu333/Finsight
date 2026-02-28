@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addInstallment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.neoutils.finsight.ui.component.CategorySelector
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.InstallmentCounter
import com.neoutils.finsight.ui.component.InstallmentState
import com.neoutils.finsight.ui.component.InvoiceMonthNavigator
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.DatePickerModal
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.MoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_installment_amount_label
import com.neoutils.finsight.resources.add_installment_date_label
import com.neoutils.finsight.resources.add_installment_initial_invoice
import com.neoutils.finsight.resources.add_installment_save
import com.neoutils.finsight.resources.add_installment_title
import com.neoutils.finsight.resources.add_installment_title_label
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val formats = DateFormats()

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
        val date = rememberTextFieldState(formats.dayMonthYear.format(currentDate))

        var selectedCategory by remember { mutableStateOf<Category?>(null) }
        var installments by remember { mutableStateOf(2) }

        LaunchedEffect(Unit) {
            viewModel.errorMessage.collect { message ->
                snackbarHostState.showSnackbar(message)
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
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
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                CreditCardSelector(
                    creditCards = uiState.creditCards,
                    creditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = { viewModel.selectCreditCard(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.invoiceSelection?.let { selection ->
                    Spacer(modifier = Modifier.height(8.dp))

                    InvoiceMonthNavigator(
                        selection = selection,
                        onNavigate = { viewModel.navigateToMonth(it) },
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
                    inputTransformation = MoneyInputTransformation(),
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
                            modifier = Modifier.padding(end = 8.dp),
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
                                        initialDate = formats.dayMonthYear.parse(date.text.toString()),
                                        maxDate = currentDate,
                                        onDateSelected = { selectedDate ->
                                            date.edit {
                                                replace(
                                                    0,
                                                    length,
                                                    formats.dayMonthYear.format(selectedDate),
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
                        viewModel.addInstallment(
                            form = form,
                            installments = installments,
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
