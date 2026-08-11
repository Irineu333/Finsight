@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.exchangeRateForm

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rate_form_from
import com.neoutils.finsight.resources.exchange_rate_form_to
import com.neoutils.finsight.resources.exchange_rate_form_currency_locked
import com.neoutils.finsight.resources.exchange_rate_form_date
import com.neoutils.finsight.resources.exchange_rate_form_rate
import com.neoutils.finsight.resources.exchange_rate_form_rate_helper_pair
import com.neoutils.finsight.resources.exchange_rate_form_remove
import com.neoutils.finsight.resources.exchange_rate_form_save
import com.neoutils.finsight.resources.exchange_rate_form_title_edit
import com.neoutils.finsight.resources.exchange_rate_form_title_new
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.date.DatePickerModal
import com.neoutils.finsight.ui.util.exposeTestTags
import com.neoutils.finsight.ui.util.optionalTestTag
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.util.DateInputTransformation
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.formatRateForEditing
import com.neoutils.finsight.util.rateToDoubleOrNull
import com.neoutils.finsight.util.rememberRateInputTransformation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Registers a rate, corrects one, or removes it.
 *
 * **It is a form, and it is built like every other form of this app** — centred title,
 * fields that are fields, a full-width primary button. Each end is an
 * `ExposedDropdownMenuBox` because that is what choosing among a list inside a form
 * looks like here (`AccountSelector`), not the 52dp `CurrencyRow`, which states a
 * permanent attribute of an account. And the date is typed and validated like every
 * other date in the app, with the calendar as a button beside it — not a read-only box
 * that can only be filled by a picker.
 *
 * **Both ends are chosen.** A rate is an observation about a pair, so the form states
 * both of them, with the base in force pre-selected as the counterpart and the whole
 * catalog offered on either side — pricing the base against another currency is a
 * legitimate observation, and its inverse feeds the reading. The one restriction left is
 * that the two ends differ.
 *
 * Correcting a rate **locks** the pair rather than hiding it (the rule task 15.8
 * wrote for the budget form: a locked field still answers what the number is about),
 * and removing one wears the outlined `error` button with the bin that every
 * destructive action of this app wears.
 *
 * **This is the only place an external suggestion may ever appear** (design D11), and in
 * v1 none does. No read of this app waits on the network, shows a loading state, or fails
 * because a source is unreachable — so there is nothing here to block on either.
 */
class ExchangeRateFormModal(
    private val existing: ExchangeRate? = null,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ExchangeRateFormViewModel> { parametersOf(existing) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val modalManager = LocalModalManager.current

        // **A rate is money** — so many of the counterpart currency per one unit of the
        // priced one — and it *reads* through the app's one formatter, with the
        // counterpart's symbol as this field's prefix. It is not *typed* like money, though: the money rule fills from
        // the right because the last two digits are cents, and under it `5,32` became
        // `0,0532` while a rate of `0,000691` was not expressible at all — two places
        // round it to a rate of zero, which is a different statement, not a rounder truth.
        // So the field keeps what is typed and the archive keeps the full quotient, as
        // `currency-consolidation` requires.
        val formatter = LocalCurrencyFormatter.current
        val rate = rememberTextFieldState(
            uiState.rate?.let { formatter.formatRateForEditing(it) }.orEmpty()
        )

        LaunchedEffect(Unit) {
            snapshotFlow { rate.text.toString() }
                .collect {
                    val typed = it.rateToDoubleOrNull(formatter.decimalSeparator)
                        ?.takeIf { value -> value > 0.0 }
                    viewModel.onAction(ExchangeRateFormAction.ChangeRate(typed))
                }
        }

        val date = rememberTextFieldState(dayMonthYear.format(uiState.date))

        // The typed date is the source of truth of the field, and the state only hears
        // about it once it parses. A half-typed `15/0` is not an error to report — it is
        // a date the user has not finished writing — so it simply does not submit.
        val typedDate = runCatching { dayMonthYear.parse(date.text.toString()) }.getOrNull()

        LaunchedEffect(Unit) {
            snapshotFlow { date.text.toString() }
                .collect { text ->
                    runCatching { dayMonthYear.parse(text) }.getOrNull()?.let {
                        viewModel.onAction(ExchangeRateFormAction.SelectDate(it))
                    }
                }
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
                text = stringResource(
                    if (uiState.isEditing) {
                        Res.string.exchange_rate_form_title_edit
                    } else {
                        Res.string.exchange_rate_form_title_new
                    }
                ),
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val options = uiState.selectableCurrencies.map {
                it.code to "${it.symbol} · ${it.name ?: it.code} (${it.code})"
            }

            fun labelOf(code: String) = options.firstOrNull { it.first == code }?.second ?: code

            // The currency of an existing rate is not editable: changing it would not
            // correct that observation, it would silently reassign it to another
            // currency. Removing and registering again is the honest path.
            //
            // **Locking is not hiding.** The field used to disappear on the way in, and
            // a modal that answers "which currency is this number about?" with silence
            // is the exact failure task 15.8 corrected in the budget form: the number is
            // the whole subject, and the only thing naming it was the row the user
            // tapped a moment ago. It stays, disabled and with the reason beside it —
            // the same signifier the account form's locked currency row wears.
            if (uiState.isEditing) {
                OutlinedTextField(
                    value = labelOf(uiState.from),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(Res.string.exchange_rate_form_from)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = labelOf(uiState.to),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(Res.string.exchange_rate_form_to)) },
                    supportingText = {
                        Text(stringResource(Res.string.exchange_rate_form_currency_locked))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // **The selector of this app, whole** — `menuAnchor` and nothing else.
                // The field that opened the shared sheet had two defects with one cause:
                // a text field owns its own gestures, so bolting an opener onto it needs
                // a second affordance for the arrow, and the two paths then read as two
                // controls doing one thing. On iOS the field's half did not fire at all,
                // and the sheet — a modal over a modal — surfaced behind a keyboard that
                // nothing had dismissed.
                //
                // `ExposedDropdownMenuBox` answers all of it because it is the only
                // component here that owns the anchor: the whole field is the target, the
                // trailing icon is decoration rather than a button, and the menu is a
                // popup over the sheet instead of another sheet under the keyboard.
                CurrencyDropdown(
                    label = stringResource(Res.string.exchange_rate_form_from),
                    selected = labelOf(uiState.from),
                    options = options,
                    onSelected = { viewModel.onAction(ExchangeRateFormAction.SelectFrom(it)) },
                    valueTestTag = "exchange_rate_form_from",
                )

                Spacer(modifier = Modifier.height(8.dp))

                CurrencyDropdown(
                    label = stringResource(Res.string.exchange_rate_form_to),
                    selected = labelOf(uiState.to),
                    options = options,
                    onSelected = { viewModel.onAction(ExchangeRateFormAction.SelectTo(it)) },
                    valueTestTag = "exchange_rate_form_to",
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                state = rate,
                label = { Text(stringResource(Res.string.exchange_rate_form_rate)) },
                supportingText = {
                    Text(
                        stringResource(
                            Res.string.exchange_rate_form_rate_helper_pair,
                            uiState.to,
                            uiState.from,
                        )
                    )
                },
                prefix = { Text(LocalCurrencySymbols.current(uiState.to)) },
                inputTransformation = rememberRateInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exchange_rate_form_rate"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                state = date,
                label = { Text(stringResource(Res.string.exchange_rate_form_date)) },
                inputTransformation = DateInputTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modalManager.show(
                                DatePickerModal(
                                    initialDate = typedDate,
                                    onDateSelected = { selected ->
                                        date.edit {
                                            replace(0, length, dayMonthYear.format(selected))
                                        }
                                    },
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
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onAction(ExchangeRateFormAction.Submit) },
                // A rate the field cannot read, or a date it cannot parse, is not an
                // error to report — it is a form that is not finished.
                enabled = uiState.canSubmit && typedDate != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exchange_rate_form_save"),
            ) {
                Text(
                    text = stringResource(Res.string.exchange_rate_form_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Removal is the obligatory corollary of a rate outliving its transaction
            // (design D27), not a convenience: a rate observed by mistake from an
            // operation since deleted has no other path that reaches it.
            //
            // **It is a destructive action, and this app has one shape for those** —
            // outlined, in `error`, with the bin and a 16sp label, the button
            // `ViewBudgetModal`, `ViewTransactionModal`, `ViewCategoryModal` and
            // `ViewRecurringModal` all wear. It used to be a bare `TextButton` with
            // coloured text, the one place in the app where deleting looked like a link.
            if (uiState.isEditing) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.onAction(ExchangeRateFormAction.Remove) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.error,
                    ),
                    border = BorderStroke(width = 1.dp, color = colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    Text(
                        text = stringResource(Res.string.exchange_rate_form_remove),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * The app's selector, whole — `menuAnchor` and nothing else.
 *
 * The field that opened the shared sheet had two defects with one cause: a text field
 * owns its own gestures, so bolting an opener onto it needs a second affordance for the
 * arrow, and the two paths then read as two controls doing one thing. On iOS the field's
 * half did not fire at all, and the sheet — a modal over a modal — surfaced behind a
 * keyboard that nothing had dismissed.
 *
 * `ExposedDropdownMenuBox` answers all of it because it is the only component here that
 * owns the anchor: the whole field is the target, the trailing icon is decoration rather
 * than a button, and the menu is a popup over the sheet instead of another sheet under
 * the keyboard. It is a composable of its own now because the form states **two** ends.
 */
@Composable
private fun CurrencyDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    // Named on the field that shows the choice, as `AccountSelector` and `TargetSelector`
    // already do: the box around it is layout, and only the field renders the code.
    valueTestTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (options.isNotEmpty()) {
                expanded = it
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = options.isNotEmpty(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .optionalTestTag(valueTestTag),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The menu is its own popup window, and the app window's opt-in does not reach
            // it — without this its options are invisible to the accessibility tree with
            // no error to say why. The same call `TargetSelector` already makes.
            modifier = Modifier.exposeTestTags(),
        ) {
            options.forEach { (code, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel, fontSize = 14.sp) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    },
                    // By code, never by the label beside it: the label is the currency's
                    // name, which the user may have written themselves.
                    modifier = Modifier.testTag("exchange_rate_option_$code"),
                )
            }
        }
    }
}
