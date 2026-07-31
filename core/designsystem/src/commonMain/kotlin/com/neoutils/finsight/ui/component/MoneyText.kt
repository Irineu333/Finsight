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

    // Where the amounts of a left-aligned stack begin: past whatever precedes the first
    // one — its mark, if it has one. Measured with the first line's own style rather than
    // guessed, because the mark is a glyph of the surface's type and no fixed inset
    // follows it from `headlineMedium` to 36sp.
    val measurer = rememberTextMeasurer()
    val firstPrefix = terms.first().mark?.plus(" ").orEmpty()
    val indent = if (align == TextAlign.Start && firstPrefix.isNotEmpty()) {
        with(LocalDensity.current) {
            measurer.measure(AnnotatedString(firstPrefix), style).size.width.toDp()
        }
    } else {
        INDENT_WITHOUT_MARK
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (align == TextAlign.Start) Alignment.Start else Alignment.End,
    ) {
        terms.forEachIndexed { index, term ->
            val termStyle = if (index == 0) style else secondaryStyle

            Text(
                text = term.text,
                style = termStyle,
                textAlign = align,
                // Left-aligned, the terms below the first are indented to where the
                // first amount begins, so the column reads as one figure continuing
                // rather than as three figures listed. Right-aligned there is nothing to
                // indent: the column's edge already says it (design D22).
                modifier = if (align == TextAlign.Start && index > 0) {
                    Modifier.padding(start = indent)
                } else {
                    Modifier
                },
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

/**
 * The step a continuation takes when the first term carries no mark to sit under. Small
 * on purpose: it says "this line belongs to the one above" and nothing more.
 */
private val INDENT_WITHOUT_MARK = 8.dp
