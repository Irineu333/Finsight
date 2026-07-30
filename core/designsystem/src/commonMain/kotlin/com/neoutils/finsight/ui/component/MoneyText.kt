package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.degradedTerm
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatTerms
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_unconverted_term
import org.jetbrains.compose.resources.stringResource

/**
 * The **one** way a money figure is rendered, however many terms it has.
 *
 * Terms take one line each, right-aligned. The first keeps the surface's own typographic
 * style; the ones below it drop a step and go to `onSurfaceVariant` — the same shape
 * `CreditCardCard` already gives *available* at 20sp beside the limit at 14sp. The step
 * does not mean "worth less": it means "same figure, second line". Juxtaposing in a
 * single line survives neither `TotalBalanceCard` nor `BalanceCard.Default` at 36sp
 * (design D22).
 *
 * No surface decides any of this for itself, which is what design D20 requires — and a
 * surface that genuinely cannot hold more than one term says so through [singleTerm]
 * rather than letting the layout truncate.
 */
@Composable
fun MoneyText(
    figure: ConsolidatedAmount,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleTerm: Boolean = false,
) {
    val formatter = LocalCurrencyFormatter.current

    if (singleTerm && !figure.isSingleTerm) {
        // Declared degradation: the base term, its mark, and the fact that something is
        // not in it. Never a silent truncation.
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            Text(text = formatter.format(figure.degradedTerm()), style = style)
            Text(
                text = stringResource(Res.string.money_unconverted_term),
                style = style.copy(
                    fontSize = style.fontSize * SECONDARY_TERM_SCALE,
                    color = colorScheme.onSurfaceVariant,
                ),
                textAlign = TextAlign.End,
            )
        }
        return
    }

    val terms = formatter.formatTerms(figure)

    if (terms.size == 1) {
        Text(text = terms.single(), style = style, modifier = modifier)
        return
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        terms.forEachIndexed { index, term ->
            Text(
                text = term,
                style = if (index == 0) style else style.copy(
                    fontSize = style.fontSize * SECONDARY_TERM_SCALE,
                    color = colorScheme.onSurfaceVariant,
                ),
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * The same rule for a figure that has a single term by construction — an account balance,
 * an invoice, a statement line. It is not a special case: it is the common one, and it
 * renders through the same call so that a surface never grows a second way to show money.
 */
@Composable
fun MoneyText(
    amount: DisplayAmount,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    Text(text = formatter.format(amount), style = style, modifier = modifier)
}

/** 20sp beside 14sp — the step `CreditCardCard` already draws. */
private const val SECONDARY_TERM_SCALE = 0.7f
