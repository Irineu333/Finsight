package com.neoutils.finsight.ui.modal.editAccountBalance

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.edit_account_balance_current_title
import com.neoutils.finsight.resources.edit_account_balance_final_title
import com.neoutils.finsight.resources.edit_account_balance_initial_title
import com.neoutils.finsight.resources.edit_account_balance_label
import com.neoutils.finsight.resources.edit_account_balance_save
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
class EditAccountBalanceModal(
    private val type: Type,
    private val targetMonth: YearMonth? = null,
    private val account: Account,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<EditAccountBalanceViewModel> {
            parametersOf(type, targetMonth, account)
        }
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
                        text = stringResource(type.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    if (targetMonth != null) {
                        Text(
                            text = LocalDateFormats.current.yearMonth.format(targetMonth),
                            fontSize = 14.sp,
                            color = TextLight1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
            }

            is EditAccountBalanceUiState.Content -> {
                val balanceState = rememberTextFieldState(
                    formatMoney((state.currentBalance * 100).toLong(), currencyFormatter)
                )

                val newBalance by remember {
                    derivedStateOf {
                        parseMoneyToDouble(balanceState.text.toString())
                    }
                }

                val adjustment by remember {
                    derivedStateOf {
                        newBalance - state.currentBalance
                    }
                }

                LaunchedEffect(state.currentBalance) {
                    balanceState.edit {
                        replace(0, length, formatMoney((state.currentBalance * 100).toLong(), currencyFormatter))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(type.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    if (targetMonth != null) {
                        Text(
                            text = LocalDateFormats.current.yearMonth.format(targetMonth),
                            fontSize = 14.sp,
                            color = TextLight1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

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
                        label = { Text(stringResource(Res.string.edit_account_balance_label)) },
                        state = balanceState,
                        inputTransformation = rememberMoneyInputTransformation(),
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
                                            modifier = Modifier.padding(end = 16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.onAction(EditAccountBalanceAction.Submit(newBalance)) },
                        enabled = balanceState.text.isNotBlank() && newBalance != state.currentBalance,
                        modifier = Modifier.fillMaxWidth(),
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
                text = formatter.formatWithSign(adjustment),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    private fun formatMoney(cents: Long, formatter: com.neoutils.finsight.extension.CurrencyFormatter): String {
        val isNegative = cents < 0
        val formatted = formatter.format(kotlin.math.abs(cents).toDouble() / 100)
        return if (isNegative) "-$formatted" else formatted
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val isNegative = formatted.startsWith("-")
        val digits = formatted.filter { it.isDigit() }
        val cents = digits.toLongOrNull() ?: return 0.0
        return (if (isNegative) -cents else cents).toDouble() / 100
    }

    enum class Type(val titleRes: StringResource) {
        CURRENT(Res.string.edit_account_balance_current_title),
        FINAL(Res.string.edit_account_balance_final_title),
        INITIAL(Res.string.edit_account_balance_initial_title)
    }
}
