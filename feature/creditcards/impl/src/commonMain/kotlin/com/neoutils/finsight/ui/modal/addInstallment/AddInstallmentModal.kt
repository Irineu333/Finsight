@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addInstallment

import com.neoutils.finsight.extension.today
import com.neoutils.finsight.feature.categories.api.CategoriesEntry

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddInstallmentModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<AddInstallmentViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        val modalManager = LocalModalManager.current
        val categoriesEntry = koinInject<CategoriesEntry>()


        // The only state left here: a `TextFieldState` is Compose's editing buffer, not the
        // form. Each reports to the ViewModel, which owns what the field means.
        val title = rememberTextFieldState(uiState.form.title.orEmpty())
        val amount = rememberTextFieldState(uiState.form.amount)
        val date = rememberTextFieldState(uiState.form.date)

        LaunchedEffect(Unit) {
            snapshotFlow { title.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddInstallmentAction.ChangeTitle(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddInstallmentAction.ChangeAmount(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddInstallmentAction.ChangeDate(it)) }
        }

        // The buffer above only ever reports to the ViewModel; this brings back what the
        // ViewModel decides — the date the selected invoice places. The equality guard is
        // what closes the loop: a value that came from typing arrives back identical and
        // is not rewritten, so nothing fights the text being typed.
        LaunchedEffect(uiState.form.date) {
            if (uiState.form.date != date.text.toString()) {
                date.edit { replace(0, length, uiState.form.date) }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_installment_title"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                CategorySelector(
                    selectedCategory = uiState.form.category,
                    categories = uiState.categories,
                    onCategorySelected = {
                        viewModel.onAction(AddInstallmentAction.SelectCategory(it))
                    },
                    onEmpty = { modalManager.show(categoriesEntry.categoryFormModal()) },
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
                    // The amount is typed in the selected card's currency. Until a card
                    // is chosen there is nothing to denominate the field with, and the
                    // form already refuses to submit that state.
                    inputTransformation = uiState.currency?.let {
                        rememberMoneyInputTransformation(it, amount)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        uiState.currency?.let { currency ->
                            InstallmentCounter(
                                state = InstallmentState(
                                    count = uiState.form.installments,
                                    total = uiState.form.amount.moneyToDouble(),
                                    currency = currency,
                                ),
                                onInstallmentsChange = {
                                    viewModel.onAction(AddInstallmentAction.ChangeInstallments(it))
                                },
                                minCount = 2,
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_installment_amount"),
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
                                        maxDate = uiState.today,
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
                        viewModel.onAction(AddInstallmentAction.Submit)
                    },
                    enabled = uiState.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_installment_save"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.add_installment_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

        }
    }
}
