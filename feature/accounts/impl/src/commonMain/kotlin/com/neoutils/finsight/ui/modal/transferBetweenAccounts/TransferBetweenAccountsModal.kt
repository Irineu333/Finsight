@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.destinationLeg
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.extension.sourceLeg
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.AmountField
import com.neoutils.finsight.ui.component.CounterpartAmountField
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The form of a transfer, in its two modes: registering one and correcting one.
 *
 * They are the same form and differ only in what the header announces, so there is one
 * of them rather than two grammars for one operation. The two modes are reached by two
 * constructors over a private one, and that is the point: neither of them can be handed
 * a state that does not mean anything — no source and no transaction, or both at once.
 *
 * In correction the source account is **not** a parameter. It is the operation's own
 * outgoing leg, so asking the caller for it would open the door to a caller that
 * disagrees with the transaction it passed.
 */
class TransferBetweenAccountsModal private constructor(
    private val sourceAccount: Account,
    private val transaction: Transaction?,
) : ModalBottomSheet() {

    /** Registering a transfer: it is born pointing at the account the money leaves. */
    constructor(sourceAccount: Account) : this(sourceAccount, transaction = null)

    /** Correcting one: both ends are read off the operation. */
    constructor(transaction: Transaction) : this(
        sourceAccount = requireNotNull(transaction.entries.sourceLeg()?.account) {
            "A transfer always has an outgoing asset leg."
        },
        transaction = transaction,
    )

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<TransferBetweenAccountsViewModel> {
            parametersOf(sourceAccount, transaction)
        }

        val uiState by viewModel.uiState.collectAsState()
        val modalManager = LocalModalManager.current
        val formatter = LocalCurrencyFormatter.current

        // The clock the app was given, like the other sheets that write a transaction: this date
        // both seeds the field and bounds the picker, and the picker reads the same clock.
        val currentDate = koinInject<Clock>().today()

        // What the operation records, each end read in the currency of its own account.
        // A correction opens on facts, not on an empty form and today's date.
        val recordedSource = transaction?.entries?.sourceLeg()
        val recordedDestination = transaction?.entries?.destinationLeg()
        val recordedDestinationAmount = recordedDestination
            ?.takeIf { it.currency != recordedSource?.currency }
            ?.let { abs(it.amount) / 100.0 }

        val amount = rememberTextFieldState(
            recordedSource?.let { formatter.format(abs(it.amount) / 100.0, it.currency) }.orEmpty(),
        )
        val destinationAmount = rememberTextFieldState(
            recordedDestinationAmount
                ?.let { formatter.format(it, recordedDestination.currency) }
                .orEmpty(),
        )
        val date = rememberTextFieldState(
            dayMonthYear.format(transaction?.date ?: currentDate),
        )

        val source = uiState.selectedSourceAccount ?: sourceAccount
        val destination = uiState.selectedDestinationAccount

        // What is stated goes to the view model, which is where the archive is asked
        // what the other end is worth. The screen never multiplies by a rate.
        LaunchedEffect(Unit) {
            snapshotFlow { amount.text.toString() }.collect {
                viewModel.onAction(TransferBetweenAccountsAction.ChangeAmount(it.moneyToDouble()))
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }.collect { text ->
                runCatching { dayMonthYear.parse(text) }.getOrNull()?.let {
                    viewModel.onAction(TransferBetweenAccountsAction.ChangeDate(it))
                }
            }
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
                    text = stringResource(
                        if (uiState.isEditMode) {
                            Res.string.transfer_edit_title
                        } else {
                            Res.string.transfer_title
                        }
                    ),
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
                    modifier = Modifier
                        .fillMaxWidth(),
                    valueTestTag = "transfer_source_account",
                )

                Spacer(modifier = Modifier.height(8.dp))

                AccountSelector(
                    selectedAccount = uiState.selectedDestinationAccount,
                    accounts = uiState.destinationAccounts,
                    onAccountSelected = {
                        viewModel.onAction(TransferBetweenAccountsAction.SelectDestinationAccount(it))
                    },
                    label = stringResource(Res.string.transfer_destination_account_label),
                    // The destinations are the accounts minus the source, and dropping
                    // one account must not decide whether the other selector names its
                    // currency. The two selectors of a transfer answer to the same set.
                    currencyScope = uiState.accounts,
                    modifier = Modifier.fillMaxWidth(),
                    valueTestTag = "transfer_destination_account",
                )

                Spacer(modifier = Modifier.height(8.dp))

                // The amount typed is the one that leaves the source account, so the
                // field wears the source's currency and names the account it leaves.
                AmountField(
                    state = amount,
                    label = stringResource(Res.string.cross_currency_leaves_label, source.name),
                    currency = source.currency,
                    modifier = Modifier.testTag("transfer_amount"),
                )

                CounterpartAmountField(
                    visible = uiState.isCrossCurrency,
                    state = destinationAmount,
                    label = stringResource(
                        Res.string.cross_currency_enters_label,
                        destination?.name.orEmpty(),
                    ),
                    currency = destination?.currency ?: source.currency,
                    counterpartAmount = amount.text.toString().moneyToDouble(),
                    counterpartCurrency = source.currency,
                    suggestion = uiState.suggestion,
                    date = runCatching { dayMonthYear.parse(date.text.toString()) }
                        .getOrDefault(currentDate),
                    modifier = Modifier.testTag("transfer_destination_amount"),
                    // What the operation records is fact, and the archive's offer must
                    // neither replace it nor erase it for want of an observation.
                    recordedAmount = recordedDestinationAmount,
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
                        viewModel.onAction(
                            TransferBetweenAccountsAction.Submit(
                                amount = amount.text.toString().moneyToDouble(),
                                destinationAmount = destinationAmount.text.toString().moneyToDouble(),
                                date = dayMonthYear.parse(date.text.toString()),
                            )
                        )
                    },
                    enabled = isValidTransfer(
                        amount = amount.text.toString(),
                        destinationAmount = destinationAmount.text.toString(),
                        isCrossCurrency = uiState.isCrossCurrency,
                        date = date.text.toString(),
                        sourceAccount = uiState.selectedSourceAccount,
                        destinationAccount = uiState.selectedDestinationAccount,
                        today = currentDate,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transfer_save"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        // The verb has to name what the tap does. "Transfer" is what
                        // registering one does; a correction saves an operation that
                        // already moved the money, and reading "Transfer" there
                        // suggests it is about to move again.
                        text = stringResource(
                            if (uiState.isEditMode) {
                                Res.string.transfer_edit_confirm
                            } else {
                                Res.string.transfer_confirm
                            }
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        }
    }

}

/**
 * Whether the form may be submitted — and, when the transfer crosses currencies, that
 * **both** amounts were stated.
 *
 * Covering the second field is the validation change easiest to forget (design D26), and
 * it is what makes the write boundary's same-sign guard unreachable by any path a user
 * can walk: in a form where one field is "leaves" and the other "arrives", the residues
 * oppose each other by construction, so the only way to reach that refusal is a zero —
 * which this refuses first.
 *
 * Top-level and `internal` so the rule can be exercised without a screen — with [today]
 * handed in, because the layer that owns a clock is the one that reads it.
 */
internal fun isValidTransfer(
    amount: String,
    destinationAmount: String,
    isCrossCurrency: Boolean,
    date: String,
    sourceAccount: Account?,
    destinationAccount: Account?,
    today: LocalDate,
): Boolean {
    if (amount.isEmpty()) return false
    if (amount.moneyToDouble() <= 0.0) return false
    if (isCrossCurrency && destinationAmount.moneyToDouble() <= 0.0) return false
    if (date.isEmpty()) return false
    if (sourceAccount == null || destinationAccount == null) return false
    if (sourceAccount.id == destinationAccount.id) return false

    val parsedDate = runCatching {
        dayMonthYear.parse(date)
    }.getOrElse { return false }

    return parsedDate <= today
}
