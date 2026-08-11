package com.neoutils.finsight.ui.modal.editAccountBalance

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_date_label
import com.neoutils.finsight.resources.edit_account_balance_label
import com.neoutils.finsight.resources.edit_account_balance_save
import com.neoutils.finsight.resources.edit_account_balance_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import androidx.compose.material.icons.twotone.CalendarToday
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One adjustment, opened on [initialDate]. The entry point chooses which date to open on;
 * it never chooses a kind of adjustment, because there is none.
 */
@OptIn(ExperimentalMaterial3Api::class)
class EditAccountBalanceModal(
    private val initialDate: LocalDate,
    private val account: Account,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditAccountBalanceViewModel> {
            parametersOf(initialDate, account)
        }
        val manager = LocalModalManager.current
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val currencyFormatter = LocalCurrencyFormatter.current
        when (val state = uiState) {
            EditAccountBalanceUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.edit_account_balance_title),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
            }

            is EditAccountBalanceUiState.Content -> {
                // The balance being edited belongs to the selected account, so the field
                // reads and writes in that account's currency (design D29).
                val currency = state.selectedAccount.currency

                // Compose edits text through a `TextFieldState`, and that is an editing
                // buffer, not the form: it reports to the ViewModel, which owns the date.
                val dateState = rememberTextFieldState(state.date)

                LaunchedEffect(Unit) {
                    snapshotFlow { dateState.text.toString() }
                        .drop(1)
                        .collect { viewModel.onAction(EditAccountBalanceAction.ChangeDate(it)) }
                }

                val balanceState = rememberTextFieldState(
                    currencyFormatter.format(
                        DisplayAmount.natural(state.currentBalance, currency, isApproximate = false)
                    )
                )

                val newBalance by remember {
                    derivedStateOf {
                        balanceState.text.toString().moneyToDouble()
                    }
                }

                val adjustment by remember {
                    derivedStateOf {
                        newBalance - state.currentBalance
                    }
                }

                LaunchedEffect(state.currentBalance, currency) {
                    balanceState.edit {
                        replace(
                            0,
                            length,
                            currencyFormatter.format(
                                DisplayAmount.natural(
                                    state.currentBalance,
                                    currency,
                                    isApproximate = false,
                                )
                            ),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.edit_account_balance_title),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AccountSelector(
                        selectedAccount = state.selectedAccount,
                        accounts = state.accounts,
                        onAccountSelected = { selected ->
                            selected?.let {
                                viewModel.onAction(EditAccountBalanceAction.SelectAccount(it))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        state = dateState,
                        label = { Text(stringResource(Res.string.add_transaction_date_label)) },
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
                                            initialDate = runCatching {
                                                dayMonthYear.parse(dateState.text.toString())
                                            }.getOrNull(),
                                            // An adjustment is never dated in the future,
                                            // and the calendar is where that is settled —
                                            // not an error raised after the submit.
                                            maxDate = state.today,
                                            onDateSelected = { selectedDate ->
                                                dateState.edit {
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        label = { Text(stringResource(Res.string.edit_account_balance_label)) },
                        state = balanceState,
                        inputTransformation = rememberMoneyInputTransformation(currency, balanceState),
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = adjustment != 0.0,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                if (adjustment != 0.0) {
                                    AnimatedContent(
                                        targetState = adjustment,
                                        transitionSpec = {
                                            fadeIn() togetherWith fadeOut()
                                        }
                                    ) { currentAdjustment ->
                                        AdjustmentLabel(
                                            adjustment = currentAdjustment,
                                            currency = currency,
                                            modifier = Modifier.padding(end = 16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_balance_amount")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.onAction(EditAccountBalanceAction.Submit(newBalance)) },
                        enabled = balanceState.text.isNotBlank() && newBalance != state.currentBalance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_balance_save"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Adjustment),
                    ) {
                        Text(
                            text = stringResource(Res.string.edit_account_balance_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AdjustmentLabel(
        adjustment: Double,
        currency: String,
        modifier: Modifier = Modifier
    ) {
        val formatter = LocalCurrencyFormatter.current
        val isIncome = adjustment > 0
        val color = if (isIncome) Income else Expense
        val icon = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = formatter.format(
                    DisplayAmount.explicitSign(adjustment, currency, isApproximate = false)
                ),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

}
