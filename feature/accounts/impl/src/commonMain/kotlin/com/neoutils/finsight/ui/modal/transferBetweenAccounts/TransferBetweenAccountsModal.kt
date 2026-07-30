@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import androidx.compose.animation.AnimatedVisibility
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
import com.neoutils.finsight.ui.component.CrossCurrencyAmountField
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
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

        val amount = rememberTextFieldState()
        val destinationAmount = rememberTextFieldState()
        val date = rememberTextFieldState(dayMonthYear.format(currentDate))

        val sourceCurrency = uiState.selectedSourceAccount?.currency
        val destinationCurrency = uiState.selectedDestinationAccount?.currency

        // The first amount and the date reach the ViewModel as they are typed: which quote
        // governs this operation depends on both, and that decision is not the form's.
        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }
                .collect {
                    viewModel.onAction(
                        TransferBetweenAccountsAction.SourceAmountChanged(it.moneyToDouble())
                    )
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .collect { typed ->
                    runCatching { dayMonthYear.parse(typed) }.getOrNull()?.let {
                        viewModel.onAction(TransferBetweenAccountsAction.DateChanged(it))
                    }
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
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                AccountSelector(
                    selectedAccount = uiState.selectedSourceAccount,
                    accounts = uiState.accounts,
                    onAccountSelected = {
                        viewModel.onAction(TransferBetweenAccountsAction.SelectSourceAccount(it))
                    },
                    label = stringResource(Res.string.transfer_source_account_label),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                AccountSelector(
                    selectedAccount = uiState.selectedDestinationAccount,
                    accounts = uiState.destinationAccounts,
                    onAccountSelected = {
                        viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(it))
                    },
                    label = stringResource(Res.string.transfer_destination_account_label),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    state = amount,
                    label = {
                        Text(
                            text = if (uiState.isCrossCurrency) {
                                stringResource(
                                    Res.string.transfer_amount_from_label,
                                    uiState.selectedSourceAccount?.name.orEmpty(),
                                )
                            } else {
                                stringResource(Res.string.transfer_amount_label)
                            }
                        )
                    },
                    inputTransformation = rememberMoneyInputTransformation(
                        currency = sourceCurrency ?: sourceAccount.currency,
                        state = amount,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Revealed only where the two ends disagree about the currency: a transfer
                // inside one currency reads exactly as it did before this change.
                AnimatedVisibility(visible = uiState.isCrossCurrency) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CrossCurrencyAmountField(
                            state = destinationAmount,
                            currency = destinationCurrency.orEmpty(),
                            label = stringResource(
                                Res.string.transfer_amount_to_label,
                                uiState.selectedDestinationAccount?.name.orEmpty(),
                            ),
                            counterpartAmount = amount.text.toString().moneyToDouble(),
                            counterpartCurrency = sourceCurrency.orEmpty(),
                            suggestedAmount = uiState.suggestion?.amount,
                            suggestedRateDate = uiState.suggestion?.rate?.date,
                            isSuggestionFromOperationDate =
                                uiState.suggestion?.isFromOperationDate == true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

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
                                        initialDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull(),
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
                        val source = amount.text.toString().moneyToDouble()
                        viewModel.onAction(
                            TransferBetweenAccountsAction.Submit(
                                sourceAmount = source,
                                // Inside one currency the two ends are the same number, and
                                // the field that would say so is not even on screen.
                                destinationAmount = if (uiState.isCrossCurrency) {
                                    destinationAmount.text.toString().moneyToDouble()
                                } else {
                                    source
                                },
                                date = dayMonthYear.parse(date.text.toString()),
                            )
                        )
                    },
                    enabled = isValidTransfer(
                        amount = amount.text.toString(),
                        date = date.text.toString(),
                        sourceAccount = uiState.selectedSourceAccount,
                        destinationAccount = uiState.selectedDestinationAccount,
                        // Without this the residue guard of a cross-currency write would be
                        // reachable by an empty second field.
                        destinationAmount = destinationAmount.text.toString()
                            .takeIf { uiState.isCrossCurrency },
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

        }
    }

    private fun isValidTransfer(
        amount: String,
        date: String,
        sourceAccount: Account?,
        destinationAccount: Account?,
        /** `null` when the operation is single-currency and there is no second field. */
        destinationAmount: String?,
    ): Boolean {
        if (amount.isEmpty()) return false
        if (amount.moneyToDouble() <= 0.0) return false
        if (destinationAmount != null && destinationAmount.moneyToDouble() <= 0.0) return false
        if (date.isEmpty()) return false
        if (sourceAccount == null || destinationAccount == null) return false
        if (sourceAccount.id == destinationAccount.id) return false

        val parsedDate = runCatching {
            dayMonthYear.parse(date)
        }.getOrElse { return false }

        return parsedDate <= currentDate
    }
}
