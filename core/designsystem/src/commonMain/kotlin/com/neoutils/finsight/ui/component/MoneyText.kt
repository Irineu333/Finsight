package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
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
 * Along which axis the terms of a figure are laid out. A closed set, declared by the
 * surface and rendered here — the surface says what room it has, never how to draw.
 */
enum class MoneyLayout {
    /**
     * One term per line, the default and the rule of design D22: juxtaposing survives
     * neither `TotalBalanceCard` at `headlineMedium` nor `BalanceCard.Default` at 36sp.
     */
    STACKED,

    /**
     * The terms on a single line, for a surface with **no vertical room to give**.
     *
     * D22's argument is about large type in a column that owns its height; a row of
     * small type that already shares its line with a label has the opposite constraint,
     * and there stacking is what breaks — it grows a row whose height is set by
     * something else (an icon, a progress bar), pushing the rest out of alignment.
     *
     * The step between terms is kept, so the figure still reads as one continuing thing
     * rather than as two figures side by side, and the joiner stays glued to its own
     * term, because it juxtaposes rather than adds (design D22).
     */
    INLINE,
}

/**
 * The **one** way a money figure is rendered, however many terms it has.
 *
 * Stacked, terms take one line each. The first keeps the surface's own typographic
 * style; the ones after it drop a step and go to `onSurfaceVariant` — the same shape
 * `CreditCardCard` already gives *available* at 20sp beside the limit at 14sp. The step
 * does not mean "worth less": it means "same figure, continued".
 *
 * No surface decides any of this for itself, which is what design D20 requires. What a
 * surface does declare is what it has room for: [layout], when it has no vertical room
 * to give, and [singleTerm] when it genuinely cannot hold more than one term — said out
 * loud, rather than letting the layout truncate.
 */
/**
 * What keeps a figure of several terms **one node**, and not a container with text under
 * it. A single term renders as one [Text] and carries the caller's `modifier` — including
 * whatever `testTag` it put there — on the node that renders the figure. The moment a
 * figure has two terms that node becomes a [Row] or a [Column], and a bare container
 * publishes no text of its own: the tag would still be found and would read empty, which
 * is exactly the defect an assertion on `id` **and** `text` exists to catch.
 *
 * Merging is also the honest reading. Two terms are one amount continued, not two amounts
 * — a screen reader that announces them separately is wrong for the same reason.
 */
private fun Modifier.asOneFigure() = semantics(mergeDescendants = true) {}

@Composable
fun MoneyText(
    figure: ConsolidatedAmount,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleTerm: Boolean = false,
    align: TextAlign = TextAlign.End,
    layout: MoneyLayout = MoneyLayout.STACKED,
) {
    val formatter = LocalCurrencyFormatter.current

    if (singleTerm && !figure.isSingleTerm) {
        // Declared degradation: the base term, its mark, and the fact that something is
        // not in it. Never a silent truncation.
        Column(
            modifier = modifier.asOneFigure(),
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
        // `text`, never `amount`: a figure that reduced two currencies to one term is a
        // single term **and** approximate, and that is the commonest multi-currency
        // reading there is. Printing the amount alone drops its mark, which is the silent
        // failure the mark exists to prevent.
        Text(text = terms.single().text, style = style, modifier = modifier)
        return
    }

    val secondaryStyle = style.copy(
        fontSize = style.fontSize * SECONDARY_TERM_SCALE,
        color = colorScheme.onSurfaceVariant,
    )

    if (layout == MoneyLayout.INLINE) {
        Row(
            modifier = modifier.asOneFigure(),
            horizontalArrangement = Arrangement.spacedBy(INLINE_TERM_GAP),
        ) {
            terms.forEachIndexed { index, term ->
                Text(
                    text = term.text,
                    style = if (index == 0) style else secondaryStyle,
                    // Baselines, not centres: the terms are of two sizes and sit on one
                    // line, and only a shared baseline reads as one sentence.
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        return
    }

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
        modifier = modifier.asOneFigure(),
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

/**
 * What separates two terms on one line. Only the terms are separated — the joiner stays
 * glued to the term it introduces, because it juxtaposes rather than adds (design D22).
 */
private val INLINE_TERM_GAP = 4.dp
