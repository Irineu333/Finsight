package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.extension.formatTerms

/**
 * The one way a money figure is drawn, whatever surface draws it.
 *
 * Terms take a line each, aligned to the trailing edge. The first keeps the surface's own
 * typography; the ones after it step **down** — smaller, unweighted and `onSurfaceVariant` —
 * which is the idiom the app already uses for a figure's second half. The step does not mean
 * "worth less": it means "same figure, next line".
 *
 * No surface decides this for itself. A figure of several terms is the refusal to add what
 * does not add, and a surface free to lay it out its own way is a surface free to truncate a
 * term — which is how a number starts lying without anyone having chosen it. A surface whose
 * width or grammar genuinely admits one line does not call this: it declares the degradation
 * by going through `CurrencyFormatter.formatSingleLine`, which is the only legitimate way to
 * show less of a figure than it holds.
 *
 * The common case is one term, and it costs exactly one [Text].
 */
@Composable
fun MoneyFigureText(
    figure: MoneyFigure,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val lines = formatter.formatTerms(figure)

    if (figure.isSingleTerm) {
        Text(
            text = lines.first(),
            style = style,
            modifier = modifier,
            maxLines = 1,
        )
        return
    }

    val restStyle = style.copy(
        fontSize = if (style.fontSize.isSpecified) style.fontSize * CONTINUATION_SCALE else style.fontSize,
        fontWeight = FontWeight.Normal,
        color = colorScheme.onSurfaceVariant,
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                style = if (index == 0) style else restStyle,
                maxLines = 1,
            )
        }
    }
}

/**
 * How far a continuation line steps down from the first. It is the ratio the card that
 * already draws a two-part figure uses — 20sp over 14sp — rather than a new opinion.
 */
private const val CONTINUATION_SCALE = 0.7f
