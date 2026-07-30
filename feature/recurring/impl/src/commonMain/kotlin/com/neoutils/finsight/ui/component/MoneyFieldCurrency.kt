package com.neoutils.finsight.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.neoutils.finsight.extension.LocalCurrencyFormatter

/**
 * Re-renders a money field that is **already filled** when the account behind it
 * changes.
 *
 * `MoneyInputTransformation` is keyed by currency, so everything typed after the change
 * comes out right; what it cannot do is revisit the text already in the field, and a
 * field showing `R$ 100,00` over a dollar account is exactly the failure design D10
 * exists to close — for as long as the user does not type again.
 *
 * The digits are the state; the symbol is presentation. So the digits are re-read and
 * re-formatted, and nothing else about the field moves.
 *
 * A `null` [currency] is the account not having answered yet: the digits are left
 * undressed rather than dressed in a symbol nobody chose, and they are re-read the
 * moment a currency arrives.
 */
@Composable
internal fun ReformatOnCurrencyChange(
    state: TextFieldState,
    currency: String?,
) {
    val formatter = LocalCurrencyFormatter.current

    LaunchedEffect(currency) {
        currency ?: return@LaunchedEffect
        val cents = state.text.toString()
            .filter { it.isDigit() }
            .toLongOrNull()
            ?: return@LaunchedEffect
        val formatted = formatter.format(cents / 100.0, currency)
        if (state.text.toString() == formatted) return@LaunchedEffect
        state.edit {
            delete(0, length)
            insert(0, formatted)
        }
    }
}
