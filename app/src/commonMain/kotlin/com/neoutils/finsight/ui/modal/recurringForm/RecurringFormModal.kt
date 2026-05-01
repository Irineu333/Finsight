@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.recurringForm

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_expense
import com.neoutils.finsight.resources.add_transaction_income
import com.neoutils.finsight.resources.recurring_form_amount_label
import com.neoutils.finsight.resources.recurring_form_day_label
import com.neoutils.finsight.resources.recurring_form_save
import com.neoutils.finsight.resources.recurring_form_title_label
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.CategorySelector
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.TargetSelector
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.DayInputTransformation
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class RecurringFormModal(
    private val recurring: Recurring? = null
) : ModalBottomSheet() {

    private val initialCreditCard
        get() = if (recurring?.creditCard != null) {
            Transaction.Target.CREDIT_CARD
        } else {
            Transaction.Target.ACCOUNT
        }

    private val initialType
        get() = recurring?.type ?: Recurring.Type.EXPENSE

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current

        val viewModel = koinViewModel<RecurringFormViewModel> {
            parametersOf(recurring)
        }
        val uiState by viewModel.uiState.collectAsState()

        val currencyFormatter = LocalCurrencyFormatter.current

        var type by remember { mutableStateOf<Recurring.Type>(initialType) }

        val title = rememberTextFieldState(recurring?.title.orEmpty())

        val amount = rememberTextFieldState(
            recurring?.let {
                currencyFormatter.format(recurring.amount)
            }.orEmpty()
        )

        val dayOfMonth = rememberTextFieldState(recurring?.dayOfMonth?.toString().orEmpty())

        var target by remember { mutableStateOf(initialCreditCard) }

        var selectedCategory by remember { mutableStateOf(recurring?.category) }

        LaunchedEffect(type) {
            selectedCategory = selectedCategory?.takeIf { it.type.isAccept(type) }
        }

        LaunchedEffect(target, uiState.creditCards) {
            if (target.isCreditCard && uiState.creditCards.size == 1 && uiState.selectedCreditCard == null) {
                viewModel.onAction(
                    RecurringFormAction.SelectCreditCard(uiState.creditCards.first())
                )
            }
        }

        val form by remember {
            derivedStateOf {
                val effectiveTarget = target.takeIf { type.isExpense } ?: Transaction.Target.ACCOUNT
                RecurringForm(
                    type = type,
                    amount = amount.text.toString(),
                    title = title.text.toString(),
                    dayOfMonth = dayOfMonth.text.toString(),
                    category = selectedCategory?.takeIf { it.type.isAccept(type) },
                    account = uiState.selectedAccount?.takeIf { effectiveTarget.isAccount },
                    creditCard = uiState.selectedCreditCard?.takeIf { effectiveTarget.isCreditCard },
                )
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
                selectedType = type,
                onTypeSelected = { type = it },
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

            AnimatedVisibility(type.isExpense) {
                TargetSelector(
                    selectedTarget = target,
                    onTargetSelected = { target = it },
                    availableTargets = uiState.targets,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            AnimatedVisibility(target.isAccount || type.isIncome) {
                AccountSelector(
                    selectedAccount = uiState.selectedAccount,
                    accounts = uiState.accounts,
                    onAccountSelected = {
                        viewModel.onAction(RecurringFormAction.SelectAccount(it))
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            AnimatedVisibility(type.isExpense && target.isCreditCard) {
                CreditCardSelector(
                    creditCards = uiState.creditCards,
                    creditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = {
                        viewModel.onAction(RecurringFormAction.SelectCreditCard(it))
                    },
                    onEmpty = { manager.show(CreditCardFormModal()) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CategorySelector(
                selectedCategory = selectedCategory,
                categories = when (type) {
                    Recurring.Type.INCOME -> uiState.incomeCategories
                    Recurring.Type.EXPENSE -> uiState.expenseCategories
                },
                onCategorySelected = { selectedCategory = it },
                onEmpty = { manager.show(CategoryFormModal()) },
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
                onClick = { viewModel.onAction(RecurringFormAction.Submit(form)) },
                enabled = form.isValid(),
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
