@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.feature.recurring.modal.recurringForm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.ModalErrorContent
import com.neoutils.finsight.core.ui.theme.Expense
import com.neoutils.finsight.core.ui.theme.Income
import com.neoutils.finsight.core.ui.util.DayInputTransformation
import com.neoutils.finsight.core.ui.util.rememberMoneyInputTransformation
import com.neoutils.finsight.feature.accounts.component.AccountSelector
import com.neoutils.finsight.feature.categories.component.CategorySelector
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormModalEntry
import com.neoutils.finsight.feature.creditCards.component.CreditCardSelector
import com.neoutils.finsight.feature.creditCards.modal.CreditCardFormModalEntry
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.resources.Res
import com.neoutils.finsight.feature.recurring.resources.add_transaction_expense
import com.neoutils.finsight.feature.recurring.resources.add_transaction_income
import com.neoutils.finsight.feature.recurring.resources.recurring_form_amount_label
import com.neoutils.finsight.feature.recurring.resources.recurring_form_day_label
import com.neoutils.finsight.feature.recurring.resources.recurring_form_save
import com.neoutils.finsight.feature.recurring.resources.recurring_form_title_label
import com.neoutils.finsight.feature.recurring.resources.recurring_form_unavailable
import com.neoutils.finsight.feature.transactions.component.TargetSelector
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class RecurringFormModal(
    private val recurringId: Long? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<RecurringFormViewModel> {
            parametersOf(recurringId)
        }
        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            RecurringFormUiState.Loading -> LoadingContent()
            RecurringFormUiState.Error -> ErrorContent()
            is RecurringFormUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun ErrorContent() {
        val manager = LocalModalManager.current
        ModalErrorContent(
            message = stringResource(Res.string.recurring_form_unavailable),
            onClose = { manager.dismiss() },
        )
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun Content(
        state: RecurringFormUiState.Content,
        onAction: (RecurringFormAction) -> Unit,
    ) {
        val manager = LocalModalManager.current
        val categoryFormEntry = koinInject<CategoryFormModalEntry>()
        val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()

        val title = rememberTextFieldState(state.form.title)
        val amount = rememberTextFieldState(state.form.amount)
        val dayOfMonth = rememberTextFieldState(state.form.dayOfMonth)

        var target by remember {
            mutableStateOf(
                if (state.form.creditCard != null) Transaction.Target.CREDIT_CARD
                else Transaction.Target.ACCOUNT
            )
        }

        LaunchedEffect(Unit) {
            snapshotFlow { title.text.toString() }
                .drop(1)
                .collect { onAction(RecurringFormAction.TitleChanged(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .drop(1)
                .collect { onAction(RecurringFormAction.AmountChanged(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { dayOfMonth.text.toString() }
                .drop(1)
                .collect { onAction(RecurringFormAction.DayOfMonthChanged(it)) }
        }

        LaunchedEffect(target, state.creditCards) {
            if (target.isCreditCard && state.creditCards.size == 1 && state.form.creditCard == null) {
                onAction(RecurringFormAction.SelectCreditCard(state.creditCards.first()))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            TypeToggle(
                selectedType = state.form.type,
                onTypeSelected = { onAction(RecurringFormAction.TypeChanged(it)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = title,
                label = { Text(text = stringResource(Res.string.recurring_form_title_label)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(state.form.type.isExpense) {
                TargetSelector(
                    selectedTarget = target,
                    onTargetSelected = { target = it },
                    availableTargets = state.targets,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            AnimatedVisibility(target.isAccount || state.form.type.isIncome) {
                AccountSelector(
                    selectedAccount = state.form.account,
                    accounts = state.accounts,
                    onAccountSelected = {
                        onAction(RecurringFormAction.SelectAccount(it))
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            AnimatedVisibility(state.form.type.isExpense && target.isCreditCard) {
                CreditCardSelector(
                    creditCards = state.creditCards,
                    creditCard = state.form.creditCard,
                    onCreditCardSelected = {
                        onAction(RecurringFormAction.SelectCreditCard(it))
                    },
                    onEmpty = { manager.show(creditCardFormEntry.create()) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CategorySelector(
                selectedCategory = state.form.category,
                categories = when (state.form.type) {
                    Recurring.Type.INCOME -> state.incomeCategories
                    Recurring.Type.EXPENSE -> state.expenseCategories
                },
                onCategorySelected = { onAction(RecurringFormAction.SelectCategory(it)) },
                onEmpty = { manager.show(categoryFormEntry.create()) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = amount,
                label = { Text(text = stringResource(Res.string.recurring_form_amount_label)) },
                inputTransformation = rememberMoneyInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = dayOfMonth,
                label = { Text(text = stringResource(Res.string.recurring_form_day_label)) },
                inputTransformation = DayInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onAction(RecurringFormAction.Submit) },
                enabled = state.form.isValid(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.recurring_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    @Composable
    private fun TypeToggle(
        selectedType: Recurring.Type,
        onTypeSelected: (Recurring.Type) -> Unit,
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onTypeSelected(Recurring.Type.EXPENSE) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Recurring.Type.EXPENSE -> ButtonDefaults.buttonColors(
                    containerColor = Expense,
                    contentColor = Color.White,
                )

                else -> ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.add_transaction_expense),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = { onTypeSelected(Recurring.Type.INCOME) },
            modifier = Modifier.weight(1f),
            colors = when (selectedType) {
                Recurring.Type.INCOME -> ButtonDefaults.buttonColors(
                    containerColor = Income,
                    contentColor = Color.White,
                )

                else -> ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.add_transaction_income),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
