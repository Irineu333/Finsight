package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.extension.APPROXIMATION_MARK
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.degradedTerm
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.figureTerms
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_unconverted_term
import org.jetbrains.compose.resources.stringResource

/**
 * The **one** way a money figure is rendered, however many terms it has.
 *
 * Terms take one line each. The first keeps the surface's own typographic
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
    align: TextAlign = TextAlign.End,
) {
    val formatter = LocalCurrencyFormatter.current

    if (singleTerm && !figure.isSingleTerm) {
        // Declared degradation: the base term, its mark, and the fact that something is
        // not in it. Never a silent truncation.
        Column(
            modifier = modifier,
            horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.End,
        ) {
            Text(text = formatter.format(figure.degradedTerm()), style = style)
            Text(
                text = stringResource(Res.string.money_unconverted_term),
                style = style.copy(
                    fontSize = style.fontSize * SECONDARY_TERM_SCALE,
                    color = colorScheme.onSurfaceVariant,
                ),
                textAlign = align,
            )
        }
        return
    }

    val terms = formatter.figureTerms(figure)

    if (terms.size == 1) {
        Text(text = terms.single().amount, style = style, modifier = modifier)
        return
    }

    val secondaryStyle = style.copy(
        fontSize = style.fontSize * SECONDARY_TERM_SCALE,
        color = colorScheme.onSurfaceVariant,
    )

    // Where the amounts of a left-aligned stack begin: under the first term's amount,
    // past whatever precedes it. Measured rather than guessed, because the mark is a
    // glyph of the surface's own type and no fixed inset follows it across styles.
    val measurer = rememberTextMeasurer()
    val indent = if (align == TextAlign.Start && figure.isApproximate) {
        with(LocalDensity.current) {
            measurer.measure(AnnotatedString("$APPROXIMATION_MARK "), style).size.width.toDp()
        }
    } else {
        0.dp
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.End,
    ) {
        terms.forEachIndexed { index, term ->
            val termStyle = if (index == 0) style else secondaryStyle

            if (index == 0 || term.joiner == null) {
                Text(text = term.amount, style = termStyle, textAlign = align)
                return@forEachIndexed
            }

            when (align) {
                // Left-aligned: the joiner is a bullet under the mark, and the amounts
                // line up with the first one — the shape a reader follows down a column.
                TextAlign.Start -> Row(modifier = Modifier.padding(start = indent)) {
                    Text(text = "${term.joiner} ", style = termStyle)
                    Text(text = term.amount, style = termStyle)
                }

                // Right-aligned: glued, because there the column's edge is what says
                // "same figure, second line" and a gap would break it (design D22).
                else -> Text(
                    text = term.joiner + term.amount,
                    style = termStyle,
                    textAlign = TextAlign.End,
                )
            }
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
