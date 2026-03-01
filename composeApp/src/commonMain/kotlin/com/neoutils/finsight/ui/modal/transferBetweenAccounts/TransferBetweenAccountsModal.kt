@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.DatePickerModal
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.LocalDateFormats
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

class TransferBetweenAccountsModal(
    private val sourceAccount: Account,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<TransferBetweenAccountsViewModel> {
            parametersOf(sourceAccount)
        }

        val uiState by viewModel.uiState.collectAsState()
        val modalManager = LocalModalManager.current
        val snackbarHostState = remember { SnackbarHostState() }

        val amount = rememberTextFieldState()
        val date = rememberTextFieldState(dayMonthYear.format(currentDate))

        LaunchedEffect(Unit) {
            viewModel.errorMessage.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.transfer_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AccountSelector(
                    selectedAccount = uiState.selectedSourceAccount,
                    accounts = uiState.accounts,
                    onAccountSelected = { viewModel.selectSourceAccount(it) },
                    label = stringResource(Res.string.transfer_source_account_label),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                AccountSelector(
                    selectedAccount = uiState.selectedDestinationAccount,
                    accounts = uiState.destinationAccounts,
                    onAccountSelected = { viewModel.selectDestinationAccount(it) },
                    label = stringResource(Res.string.transfer_destination_account_label),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(text = stringResource(Res.string.transfer_amount_label))
                    },
                    inputTransformation = rememberMoneyInputTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = date,
                    label = {
                        Text(text = stringResource(Res.string.transfer_date_label))
                    },
                    inputTransformation = DateInputTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                modalManager.show(
                                    DatePickerModal(
                                        initialDate = dayMonthYear.parse(date.text.toString()),
                                        maxDate = currentDate,
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
                        viewModel.transfer(
                            amount = amount.text.toString().moneyToDouble(),
                            date = dayMonthYear.parse(date.text.toString()),
                        )
                    },
                    enabled = isValidTransfer(
                        amount = amount.text.toString(),
                        date = date.text.toString(),
                        sourceAccount = uiState.selectedSourceAccount,
                        destinationAccount = uiState.selectedDestinationAccount,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.transfer_confirm),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    private fun isValidTransfer(
        amount: String,
        date: String,
        sourceAccount: Account?,
        destinationAccount: Account?,
    ): Boolean {
        if (amount.isEmpty()) return false
        if (amount.moneyToDouble() <= 0.0) return false
        if (date.isEmpty()) return false
        if (sourceAccount == null || destinationAccount == null) return false
        if (sourceAccount.id == destinationAccount.id) return false

        val parsedDate = runCatching {
            dayMonthYear.parse(date)
        }.getOrElse { return false }

        return parsedDate <= currentDate
    }
}
