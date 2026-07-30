package com.neoutils.finsight.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.moneyInput
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rate_form_last_known
import com.neoutils.finsight.resources.exchange_rate_value
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.util.rememberMoneyInputTransformation
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * One end of a two-value flow — the amount in **one** currency, saying what it is worth in the
 * other.
 *
 * The three flows that cross currencies (a transfer, an invoice paid, an invoice paid early)
 * ask for two amounts and differ only in what the ends are called, so the grammar they share
 * is a component and not a convention: the rate a caller derives, the quote a caller offers
 * and the rule about when a quote may be typed into the field are decided once, here.
 *
 * **The supporting slot says one of two things**, and never both:
 *
 * - once there is a number in each end, the **derived** rate, per unit of this field's own
 *   currency — a consequence of what the user typed, which is why it is not a control;
 * - before that, when the app knows an older quote, the date that quote is from. The value
 *   itself goes to the *placeholder*, where accepting it is a deliberate act.
 *
 * **Only a quote from the operation's own date is typed into the field** (design D24). The
 * value left in this field *becomes* a collected rate, so seeding it from a two-week-old quote
 * would write the old rate back as a new one, silently and in a loop. And it seeds only an
 * empty field: what the user typed is never overwritten by a suggestion arriving late.
 */
@Composable
fun CrossCurrencyAmountField(
    state: TextFieldState,
    currency: String,
    label: String,
    counterpartAmount: Double,
    counterpartCurrency: String,
    suggestedAmount: Double?,
    suggestedRateDate: LocalDate?,
    isSuggestionFromOperationDate: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    val formatter = LocalCurrencyFormatter.current
    val typed = state.text.toString().moneyToDouble()

    LaunchedEffect(suggestedAmount, isSuggestionFromOperationDate, currency) {
        if (suggestedAmount != null && isSuggestionFromOperationDate && state.text.isEmpty()) {
            state.setTextAndPlaceCursorAtEnd(
                formatter.moneyInput((suggestedAmount * 100).roundToLong(), currency)
            )
        }
    }

    val supportingText = when {
        typed > 0.0 && counterpartAmount > 0.0 -> stringResource(
            Res.string.exchange_rate_value,
            currency,
            formatter.format(counterpartAmount / typed, counterpartCurrency),
        )

        suggestedAmount != null && suggestedRateDate != null -> stringResource(
            Res.string.exchange_rate_form_last_known,
            dayMonthYear.format(suggestedRateDate),
        )

        else -> null
    }

    OutlinedTextField(
        state = state,
        label = { Text(text = label) },
        placeholder = if (typed <= 0.0 && suggestedAmount != null && !isSuggestionFromOperationDate) {
            { Text(text = formatter.format(suggestedAmount, currency)) }
        } else {
            null
        },
        supportingText = supportingText?.let { text -> { Text(text = text) } },
        inputTransformation = rememberMoneyInputTransformation(currency, state),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        shape = RoundedCornerShape(12.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = modifier,
    )
}
