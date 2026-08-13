@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.addTransaction

import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import org.koin.compose.koinInject
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
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
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.moneyToDouble
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
import kotlin.time.ExperimentalTime

class AddTransactionModal : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val manager = LocalModalManager.current
        val categoriesEntry = koinInject<CategoriesEntry>()
        val creditCardsEntry = koinInject<CreditCardsEntry>()

        val viewModel = koinViewModel<AddTransactionViewModel>()

        val uiState by viewModel.uiState.collectAsState()

        // The only state left here: Compose edits text through a `TextFieldState`, and that
        // is an editing buffer, not the form. Each one reports to the ViewModel, which owns
        // what the field means.
        val title = rememberTextFieldState(uiState.form.title.orEmpty())
        val amount = rememberTextFieldState(uiState.form.amount)
        val date = rememberTextFieldState(uiState.form.date)

        LaunchedEffect(Unit) {
            snapshotFlow { title.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddTransactionAction.ChangeTitle(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddTransactionAction.ChangeAmount(it)) }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .drop(1)
                .collect { viewModel.onAction(AddTransactionAction.ChangeDate(it)) }
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {

            TypeToggle(
                selectedType = uiState.form.type,
                onTypeSelected = {
                    viewModel.onAction(AddTransactionAction.ChangeType(it))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                state = title,
                label = {
                    Text(text = stringResource(Res.string.add_transaction_title_label))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_transaction_title"),
            )

            AnimatedVisibility(uiState.form.type.isExpense) {
                TargetSelector(
                    selectedTarget = uiState.selectedTarget,
                    onTargetSelected = {
                        viewModel.onAction(AddTransactionAction.ChangeTarget(it))
                    },
                    availableTargets = uiState.targets,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    valueTestTag = "add_transaction_target",
                )
            }

            AnimatedVisibility(
                uiState.form.type.isExpense && uiState.selectedTarget.isCreditCard
            ) {
                CreditCardSelector(
                    creditCards = uiState.creditCards,
                    creditCard = uiState.selectedCreditCard,
                    onCreditCardSelected = {
                        viewModel.onAction(AddTransactionAction.SelectCreditCard(it))
                    },
                    onEmpty = { manager.show(creditCardsEntry.creditCardFormModal()) },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    valueTestTag = "add_transaction_credit_card",
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
                                viewModel.onAction(AddTransactionAction.SelectInvoiceMonth(it))
                            },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.selectedTarget.isAccount || uiState.form.type.isIncome
            ) {
                    AccountSelector(
                        selectedAccount = uiState.selectedAccount,
                        accounts = uiState.accounts,
                        onAccountSelected = {
                            viewModel.onAction(AddTransactionAction.SelectAccount(it))
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        valueTestTag = "add_transaction_account",
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CategorySelector(
                selectedCategory = uiState.form.category,
                categories = uiState.categories,
                onCategorySelected = {
                    viewModel.onAction(AddTransactionAction.SelectCategory(it))
                },
                onEmpty = { manager.show(categoriesEntry.categoryFormModal()) },
                modifier = Modifier
                    .fillMaxWidth(),
                valueTestTag = "add_transaction_category",
            )

            Spacer(modifier = Modifier.height(8.dp))

            // The arrangement sits on the card this expense targets, so it reads in the
            // card's currency (design D17) — and until the card answers with one, there
            // is no figure to break the total into.
            val cardCurrency = uiState.currencyOf(TransactionTarget.CREDIT_CARD)

            OutlinedTextField(
                state = amount,
                label = {
                    Text(text = stringResource(Res.string.add_transaction_amount_label))
                },
                // Keyed by the currency of what the form writes to, so a field already
                // filled changes symbol when the target does (design D10). Without a
                // target there is nothing to denominate it with, and it does not format.
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
                trailingIcon = if (
                    uiState.form.type.isExpense &&
                    uiState.selectedTarget.isCreditCard &&
                    uiState.invoiceSelection != null &&
                    cardCurrency != null
                ) {
                    {
                        InstallmentCounter(
                            state = InstallmentState(
                                count = uiState.form.installments,
                                total = uiState.form.amount.moneyToDouble(),
                                currency = cardCurrency,
                            ),
                            onInstallmentsChange = {
                                viewModel.onAction(AddTransactionAction.ChangeInstallments(it))
                            },
                        )
                    }
                } else null,
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_transaction_amount"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = {
                    Text(text = stringResource(Res.string.add_transaction_date_label))
                },
                inputTransformation = DateInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    // Two icons in a slot sized for one: at their default 48dp each they
                    // read as two separate controls with a gap between them, so they are
                    // brought to 40dp and overlapped slightly — one pair of affordances
                    // for one field, which is what they are.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Beside the date because the date is what decides the day of the
                        // repetition — which is why marking it asks for no field of its own.
                        AnimatedVisibility(uiState.canRepeat) {
                            val tint by animateColorAsState(
                                targetValue = if (uiState.isRecurring) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                                label = "repeat_tint",
                            )

                            IconButton(
                                onClick = {
                                    viewModel.onAction(
                                        AddTransactionAction.ChangeRecurring(!uiState.isRecurring)
                                    )
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("add_transaction_repeat"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Autorenew,
                                    contentDescription = stringResource(
                                        Res.string.add_transaction_repeat_monthly
                                    ),
                                    tint = tint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        IconButton(
                            modifier = Modifier.size(40.dp),
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
                    }
                },
                // Said, not corrected: a date the user moved out of the invoice's period is
                // still theirs, and the invoice is what the expense lands on either way.
                // Which of the two notes is shown was decided by the state, not here.
                supportingText = uiState.dateSupport?.let { support ->
                    {
                        // The slot appears and disappears with the note, which changes the
                        // field's height in one frame and shoves everything below it. The
                        // text fades in place while `animateContentSize` below carries the
                        // height, so the fields glide instead of jumping.
                        AnimatedContent(
                            targetState = support,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "date_support",
                        ) { current ->
                            Text(
                                text = when (current) {
                                    DateSupport.OutsideInvoice -> {
                                        stringResource(Res.string.transaction_date_outside_invoice)
                                    }

                                    is DateSupport.RepeatsOnDay -> {
                                        stringResource(
                                            Res.string.add_transaction_repeats_on_day,
                                            current.day,
                                        )
                                    }
                                }
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.onAction(AddTransactionAction.Submit)
                },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_transaction_save"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.add_transaction_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
            modifier = Modifier
                .weight(1f)
                .testTag("add_transaction_type_expense"),
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
                text = stringResource(Res.string.add_transaction_expense),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Button(
            onClick = { onTypeSelected(TransactionType.INCOME) },
            modifier = Modifier
                .weight(1f)
                .testTag("add_transaction_type_income"),
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
                text = stringResource(Res.string.add_transaction_income),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
