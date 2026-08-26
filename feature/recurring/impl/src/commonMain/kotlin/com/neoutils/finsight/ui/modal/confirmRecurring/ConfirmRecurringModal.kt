@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.confirmRecurring

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.isAccept
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import com.neoutils.finsight.extension.today
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime



class ConfirmRecurringModal(
    private val recurring: Recurring,
    private val targetDate: LocalDate,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val categoriesEntry = koinInject<CategoriesEntry>()
        val creditCardsEntry = koinInject<CreditCardsEntry>()
        // The same clock the ViewModel confirms against — the picker must not offer a date the
        // confirmation would then clamp.
        val currentDate = koinInject<Clock>().today()
        val viewModel = koinViewModel<ConfirmRecurringViewModel> {
            parametersOf(recurring, targetDate)
        }
        val uiState by viewModel.uiState.collectAsState()

        val currencyFormatter = LocalCurrencyFormatter.current
        // Seeded in the currency of where the confirmation will post, and re-rendered
        // when the user points it somewhere else (design D10, D17). With nothing
        // selected yet the digits are seeded undressed, and `ReformatOnCurrencyChange`
        // dresses them as soon as a destination states a currency.
        val amount = rememberTextFieldState(
            uiState.currency
                ?.let { currencyFormatter.format(recurring.amount, it) }
                ?: (recurring.amount * 100).roundToLong().toString()
        )

        ReformatOnCurrencyChange(state = amount, currency = uiState.currency)
        val dateText = rememberTextFieldState(dayMonthYear.format(targetDate))
        // The title of *this* cycle, seeded from the template and free to differ.
        // Editing it never writes back to the recurring.
        val title = rememberTextFieldState(recurring.title.orEmpty())

        val typeLabel = if (recurring.type.isIncome) {
            stringResource(Res.string.recurring_income)
        } else {
            stringResource(Res.string.recurring_expense)
        }

        LaunchedEffect(uiState.confirmDate) {
            val formatted = dayMonthYear.format(uiState.confirmDate)
            if (dateText.text.toString() != formatted) {
                dateText.edit { replace(0, length, formatted) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            OutlinedTextField(
                value = typeLabel,
                onValueChange = {},
                label = { Text(text = stringResource(Res.string.view_recurring_type_label)) },
                readOnly = true,
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = title,
                label = { Text(text = stringResource(Res.string.recurring_confirm_title_label)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_recurring_title"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            CategorySelector(
                selectedCategory = uiState.selectedCategory,
                categories = uiState.categories,
                onCategorySelected = { category ->
                    viewModel.onAction(ConfirmRecurringAction.CategorySelected(category))
                },
                // Nothing to offer is not a dead end anywhere else in the app, and it is
                // not one here: the selector opens the category form, already on the type
                // this recurring accepts.
                onEmpty = {
                    modalManager.show(
                        categoriesEntry.categoryFormModal(
                            initialType = Category.Type.entries.firstOrNull {
                                it.isAccept(recurring.type)
                            },
                        )
                    )
                },
                valueTestTag = "confirm_recurring_category",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            AnimatedVisibility(recurring.type.isExpense) {
                TargetSelector(
                    selectedTarget = uiState.selectedTarget,
                    onTargetSelected = { target ->
                        viewModel.onAction(ConfirmRecurringAction.TargetSelected(target))
                    },
                    availableTargets = uiState.targets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            AnimatedVisibility(
                uiState.selectedTarget.isAccount || recurring.type.isIncome
            ) {
                Column {
                    AccountSelector(
                        selectedAccount = uiState.selectedAccount,
                        accounts = uiState.accounts,
                        onAccountSelected = { account ->
                            viewModel.onAction(ConfirmRecurringAction.AccountSelected(account))
                        },
                        label = stringResource(Res.string.view_recurring_account_label),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    CurrencyFilterNote(
                        visible = uiState.hiddenByCurrency,
                        currency = uiState.recurringCurrency,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(uiState.selectedTarget.isCreditCard && recurring.type.isExpense) {
                Column {
                    CreditCardSelector(
                        creditCards = uiState.creditCards,
                        creditCard = uiState.selectedCreditCard,
                        onCreditCardSelected = { card ->
                            viewModel.onAction(ConfirmRecurringAction.CreditCardSelected(card))
                        },
                        // Same reason as the category selector: an empty list is a
                        // starting point, not a wall. The note below still says when the
                        // list is empty because of the currency rather than because the
                        // user holds no card.
                        onEmpty = { modalManager.show(creditCardsEntry.creditCardFormModal()) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    CurrencyFilterNote(
                        visible = uiState.hiddenByCurrency,
                        currency = uiState.recurringCurrency,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(
                uiState.selectedTarget.isCreditCard && recurring.type.isExpense
            ) {
                InvoiceSelector(
                    invoices = uiState.invoices,
                    invoice = uiState.selectedInvoice,
                    onInvoiceSelected = {
                        viewModel.onAction(ConfirmRecurringAction.InvoiceSelected(it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            OutlinedTextField(
                state = amount,
                label = { Text(text = stringResource(Res.string.recurring_confirm_amount_label)) },
                // Nothing selected denominates nothing: the field does not format, and
                // Confirm is already refused in that state.
                inputTransformation = uiState.currency?.let {
                    rememberMoneyInputTransformation(it, amount)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_recurring_amount"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = dateText,
                label = { Text(text = stringResource(Res.string.recurring_confirm_date_label)) },
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = uiState.confirmDate,
                                    // The month of the cycle, not merely "not the
                                    // future": a date from another month files the
                                    // confirmation where the pending list never looks.
                                    minDate = uiState.confirmableDates.start,
                                    maxDate = uiState.confirmableDates.endInclusive,
                                    onDateSelected = { date ->
                                        viewModel.onAction(ConfirmRecurringAction.DateChanged(date))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_recurring_date"),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { viewModel.onAction(ConfirmRecurringAction.Skip) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_recurring_skip"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_skip),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = {
                        viewModel.onAction(
                            ConfirmRecurringAction.Confirm(
                                amount = amount.text.toString(),
                                title = title.text.toString(),
                            )
                        )
                    },
                    // A transaction has to be nameable: by its own title, or by the
                    // category that names it in its place. It is `RecurringForm`'s rule,
                    // consumed rather than re-stated — erasing both is what it refuses,
                    // not erasing the title.
                    enabled = amount.text.toString().moneyToDouble() > 0.0 &&
                            (title.text.isNotBlank() || uiState.selectedCategory != null) &&
                            if (uiState.selectedTarget.isCreditCard) {
                                uiState.selectedCreditCard != null
                            } else {
                                uiState.selectedAccount != null
                            },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_recurring_confirm"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_confirm_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Why a selector offers less than what the user holds.
 *
 * Both selectors of this modal shrink for the same reason and say so with the same
 * sentence, so the sentence has one place. Without it the control would simply be missing
 * accounts or cards, which reads as a bug rather than as a rule (design D26).
 */
@Composable
private fun CurrencyFilterNote(
    visible: Boolean,
    currency: String?,
) {
    if (!visible || currency == null) return

    Text(
        text = stringResource(Res.string.confirm_recurring_currency_filter, currency),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}
