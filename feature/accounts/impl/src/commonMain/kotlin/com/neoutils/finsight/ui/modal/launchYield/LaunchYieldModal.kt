@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.launchYield

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.launch_yield_amount_label
import com.neoutils.finsight.resources.launch_yield_date_label
import com.neoutils.finsight.resources.launch_yield_save
import com.neoutils.finsight.resources.launch_yield_title
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Date and amount, and nothing else.
 *
 * There is deliberately no target balance and no notion of a previous launch: a
 * yield is money that came in, so it is recorded the way any income is (design D1).
 * Adjusting the balance remains its own path, answering its own question.
 */
class LaunchYieldModal(
    private val account: Account,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {

        val viewModel = koinViewModel<LaunchYieldViewModel> { parametersOf(account) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val manager = LocalModalManager.current

        val amount = rememberTextFieldState()
        val yieldAmount by remember {
            derivedStateOf { parseMoneyToDouble(amount.text.toString()) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.launch_yield_title),
                style = MaterialTheme.typography.titleLarge,
            )

            when (val state = uiState) {
                LaunchYieldUiState.Loading -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                is LaunchYieldUiState.Content -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    AccountSelector(
                        selectedAccount = state.account,
                        accounts = state.accounts,
                        onAccountSelected = { selected ->
                            selected?.let { viewModel.onAction(LaunchYieldAction.SelectAccount(it)) }
                        },
                        valueTestTag = "launch_yield_account",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        label = { Text(stringResource(Res.string.launch_yield_amount_label)) },
                        state = amount,
                        // The yield lands on the selected account, so the field wears
                        // that account's currency — never the device locale's (D10).
                        inputTransformation = rememberMoneyInputTransformation(
                            state.account.currency,
                            amount,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_yield_amount"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        label = { Text(stringResource(Res.string.launch_yield_date_label)) },
                        value = dayMonthYear.format(state.date),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    manager.show(
                                        DatePickerModal(
                                            initialDate = state.date,
                                            maxDate = currentDate,
                                            onDateSelected = { selected ->
                                                viewModel.onAction(LaunchYieldAction.DateChanged(selected))
                                            },
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.CalendarToday,
                                    contentDescription = stringResource(Res.string.launch_yield_date_label),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.onAction(LaunchYieldAction.Submit(yieldAmount)) },
                        enabled = yieldAmount > 0.0 && !state.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_yield_save"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Income),
                    ) {
                        Text(
                            text = stringResource(Res.string.launch_yield_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun parseMoneyToDouble(formatted: String): Double {
    val digits = formatted.filter { it.isDigit() }
    val cents = digits.toLongOrNull() ?: return 0.0
    return cents.toDouble() / 100
}
