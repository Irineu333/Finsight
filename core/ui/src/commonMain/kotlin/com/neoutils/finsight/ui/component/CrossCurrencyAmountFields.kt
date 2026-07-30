package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.usecase.CrossCurrencyAmountSuggestion
import com.neoutils.finsight.domain.usecase.impliedRate
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.cross_currency_implied_by_rate
import com.neoutils.finsight.resources.decimal_separator
import com.neoutils.finsight.resources.exchange_rates_quote
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.formatRate
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The grammar the three two-value flows share — design D24, and it lives in one place
 * so that a transfer, an invoice payment and an advance payment cannot drift apart.
 *
 * The order a form asks in is **who takes part → how much → when**, and it is the
 * caller's to lay out: the selectors come first so that revealing a second amount never
 * pushes a selector down under the user's finger.
 *
 * The **labels name the account** — *"Sai de Nubank"* / *"Entra em Chase"* — because
 * once two amounts are on screen, "Valor" no longer says which is which, and "valor de
 * origem/destino" only repeats what the selectors above already said. The currency
 * symbol inside each field comes free from [rememberMoneyInputTransformation]: a field
 * carries the currency of the account it names, never the device locale's.
 */
@Composable
fun AmountField(
    state: TextFieldState,
    label: String,
    currency: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    supportingText: String? = null,
    placeholder: String? = null,
) {
    OutlinedTextField(
        state = state,
        label = { Text(text = label) },
        inputTransformation = rememberMoneyInputTransformation(currency, state),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        supportingText = supportingText?.let { { Text(text = it) } },
        placeholder = placeholder?.let { { Text(text = it) } },
        shape = RoundedCornerShape(12.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The **second** amount: the other end of an operation that crosses currencies.
 *
 * It is revealed by [AnimatedVisibility] exactly when the two ends are denominated
 * differently — the pattern `ConfirmRecurringModal` already uses to reveal cascading
 * selectors — so the single-currency case stays identical to what it was, field for
 * field.
 *
 * **The derived rate is shown as this field's `supportingText`** and never as a control.
 * The slot is free because this field has no validation to report, and a value the app
 * *derived* reads as a consequence there; a disabled rate field was ruled out — it
 * weighs 56dp and suggests something was entered.
 *
 * **Pre-filling happens only from a rate of the operation's own day** (design D24, task
 * 11.5). This is not a convenience rule: what is typed here *becomes* a harvested rate,
 * so pre-filling from a fortnight-old quote would write the old rate back as a new
 * observation, in silence and in a loop. Anything else goes to the placeholder, with the
 * date it is from spelled out beside it.
 *
 * @param counterpartAmount what the user stated on the other side, so this field can
 * show the rate the two of them imply. Zero means "not stated yet".
 * @param suggestion what the archive implies, or `null` when it has nothing to say —
 * between two non-base currencies it always has nothing, deliberately.
 */
@Composable
fun CounterpartAmountField(
    visible: Boolean,
    state: TextFieldState,
    label: String,
    currency: String,
    counterpartAmount: Double,
    counterpartCurrency: String,
    suggestion: CrossCurrencyAmountSuggestion?,
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val separator = stringResource(Res.string.decimal_separator)

    val sameDay = suggestion != null && suggestion.asOf == date

    // Keyed on the currency as well as on visibility: a value suggested in dollars must
    // not survive as a value in euros when the account changes. The money transformation
    // deliberately keeps the digits and swaps only the symbol, which is right for a
    // number the user typed and wrong for one the app offered.
    LaunchedEffect(visible, currency, sameDay, suggestion?.amount) {
        if (!visible) {
            state.clearText()
            return@LaunchedEffect
        }

        if (sameDay && suggestion != null && state.text.isEmpty()) {
            state.setTextAndPlaceCursorAtEnd(formatter.format(suggestion.amount, currency))
        }
    }

    AnimatedVisibility(visible) {
        val typedRate = impliedRate(
            sourceAmount = counterpartAmount,
            targetAmount = state.text.toString().moneyToDouble(),
        )

        val supporting = when {
            // What the two ends say, the moment they both say something. It consults
            // nothing — the amounts *are* the observation (design D6).
            typedRate != null -> stringResource(
                Res.string.exchange_rates_quote,
                counterpartCurrency,
                formatRate(typedRate, separator),
                currency,
            )

            // A rate from another day is offered, never assumed — and it says which day,
            // because that is the whole reason it was not filled in.
            suggestion != null && !sameDay -> stringResource(
                Res.string.cross_currency_implied_by_rate,
                LocalDateFormats.current.monthDayYear.format(suggestion.asOf),
                formatter.format(suggestion.amount, currency),
            )

            else -> null
        }

        // The gap belongs to the revealed field and not to the form around it: a spacer
        // left outside would still occupy 8dp in the single-currency case, which is the
        // one case that must stay identical to what it was.
        Column {
            Spacer(modifier = Modifier.height(8.dp))

            AmountField(
                state = state,
                label = label,
                currency = currency,
                imeAction = ImeAction.Next,
                supportingText = supporting,
                // An older rate reaches the field as a placeholder and never as its
                // value: what is typed here becomes an observation, and a placeholder
                // is the one way to offer a number without asserting it.
                placeholder = suggestion
                    ?.takeIf { !sameDay }
                    ?.let { formatter.format(it.amount, currency) },
                modifier = modifier,
            )
        }
    }
}
