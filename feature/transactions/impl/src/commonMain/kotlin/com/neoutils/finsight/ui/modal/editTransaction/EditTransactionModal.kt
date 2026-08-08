@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import org.koin.compose.koinInject
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class EditTransactionModal(
    private val transaction: Transaction,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditTransactionViewModel> { parametersOf(transaction) }


        val manager = LocalModalManager.current
        val categoriesEntry = koinInject<CategoriesEntry>()
        val creditCardsEntry = koinInject<CreditCardsEntry>()

        val uiState by viewModel.uiState.collectAsState()

        // The only state left here: a `TextFieldState` is Compose's editing buffer, not the
        // form. Each reports to the ViewModel, which owns what the field means. They are seeded
        // from the transaction the ViewModel already read back.
        val title = rememberTextFieldState(uiState.form.title.orEmpty())
        val amount = rememberTextFieldState(uiState.form.amount)
        val date = rememberTextFieldState(uiState.form.date)

        LaunchedEffect(Unit) {
            snapshotFlow { title.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(EditTransactionAction.ChangeTitle(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(EditTransactionAction.ChangeAmount(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(EditTransactionAction.ChangeDate(it)) }
        }

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.edit_transaction_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                TypeToggle(
                    selectedType = uiState.form.type,
                    onTypeSelected = {
                        viewModel.onAction(EditTransactionAction.ChangeType(it))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    state = title,
                    label = {
                        Text(text = stringResource(Res.string.edit_transaction_title_label))
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

                AnimatedVisibility(uiState.form.type.isExpense) {
                    TargetSelector(
                        selectedTarget = uiState.selectedTarget,
                        onTargetSelected = {
                            viewModel.onAction(EditTransactionAction.ChangeTarget(it))
                        },
                        availableTargets = uiState.targets,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    uiState.form.type.isExpense && uiState.selectedTarget.isCreditCard
                ) {
                    CreditCardSelector(
                        creditCards = uiState.creditCards,
                        creditCard = uiState.selectedCreditCard,
                        onCreditCardSelected = {
                            viewModel.onAction(EditTransactionAction.SelectCreditCard(it))
                        },
                        onEmpty = { manager.show(creditCardsEntry.creditCardFormModal()) },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    visible = uiState.selectedTarget.isAccount || uiState.form.type.isIncome
                ) {
                    AccountSelector(
                        selectedAccount = uiState.selectedAccount,
                        accounts = uiState.accounts,
                        onAccountSelected = {
                            viewModel.onAction(EditTransactionAction.SelectAccount(it))
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        valueTestTag = "edit_transaction_account",
                    )
                }

                AnimatedVisibility(
                    uiState.form.type.isExpense &&
                        uiState.selectedTarget.isCreditCard &&
                        uiState.invoiceSelection != null
                ) {
                    uiState.invoiceSelection?.let { selection ->
                        InvoiceMonthNavigator(
                            selection = selection,
                            onNavigate = {
                                viewModel.onAction(EditTransactionAction.SelectInvoiceMonth(it))
                            },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CategorySelector(
                    selectedCategory = uiState.form.category,
                    categories = uiState.categories,
                    onCategorySelected = {
                        viewModel.onAction(EditTransactionAction.SelectCategory(it))
                    },
                    onEmpty = { manager.show(categoriesEntry.categoryFormModal()) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(text = stringResource(Res.string.edit_transaction_amount_label))
                    },
                    // Keyed by the currency of what the form writes to, so a field
                    // already filled changes symbol when the target does (design D10).
                    // Without a target there is nothing to denominate it with.
                    inputTransformation = uiState.currencyOf(
                        if (uiState.form.type.isExpense) {
                            uiState.selectedTarget
                        } else {
                            TransactionTarget.ACCOUNT
                        }
                    )?.let { rememberMoneyInputTransformation(it, amount) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_transaction_amount"),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = date,
                    label = {
                        Text(text = stringResource(Res.string.edit_transaction_date_label))
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
                                        initialDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull(),
                                        maxDate = uiState.today,
                                        onDateSelected = { selectedDate ->
                                            date.edit {
                                                replace(0, length, dayMonthYear.format(selectedDate))
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
                                modifier = Modifier.size(20.dp),
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
                        viewModel.onAction(EditTransactionAction.Submit)
                    },
                    enabled = uiState.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_transaction_save"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.edit_transaction_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

    }

    @Composable
    fun TypeToggle(
        selectedType: TransactionType,
        onTypeSelected: (TransactionType) -> Unit
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTypeSelected(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                TransactionType.EXPENSE -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Expense,
                        contentColor = Color.White
                    )
                }

                TransactionType.INCOME,
                TransactionType.ADJUSTMENT -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.edit_transaction_expense),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = { onTypeSelected(TransactionType.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                TransactionType.INCOME -> {
                    ButtonDefaults.buttonColors(
                        containerColor = Income,
                        contentColor = Color.White
                    )
                }

                TransactionType.EXPENSE,
                TransactionType.ADJUSTMENT -> {
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.edit_transaction_income),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }


}
