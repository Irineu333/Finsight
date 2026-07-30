package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.AppliedRate
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.extension.appliedRatesOf
import com.neoutils.finsight.extension.explanationIsOwed
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.approximation_footer_action
import com.neoutils.finsight.resources.approximation_footer_converted
import com.neoutils.finsight.resources.approximation_footer_rate
import com.neoutils.finsight.resources.approximation_footer_unreached
import com.neoutils.finsight.util.dayMonthYear
import org.jetbrains.compose.resources.stringResource

/**
 * What a card says under its figures when one of them is not exact (design D21/D25).
 *
 * It renders **only** when something is owed an explanation, in the conditional idiom the
 * account card already uses for a line with nothing to say: no slot, no placeholder, no
 * empty row shifting the layout. A card whose every figure is exact — which is every card
 * of a single-currency user — is byte for byte the card it was.
 *
 * It says three things, and each is there for a reason:
 *
 * - **why the mark is there**, in the language of a helper text rather than a warning: two
 *   currencies met, and a figure that spans them is read through a rate or not at all;
 * - **which rate**, with the date of the quote — the figure carries the quotes that
 *   produced it, so what is revealed here is the one that was actually applied and not a
 *   fresh reading that may have moved since;
 * - **a way to the rates**, because a reader who disagrees with the number has exactly one
 *   thing to do about it. Where that screen lives is not this component's business — no
 *   `:core:` module names a feature — so it arrives through [LocalOpenExchangeRates],
 *   which the shell provides. A parameter would have to be threaded through every card and
 *   every one of their call sites to reach a footer that is invisible in almost all of them.
 *
 * With several unconverted currencies and no rate at all there is no rate to reveal, and
 * the footer still appears: the elision is what is being explained.
 */
@Composable
fun ApproximationFooter(
    figures: List<MoneyFigure>,
    modifier: Modifier = Modifier,
) {
    if (!explanationIsOwed(figures)) return

    val rates = appliedRatesOf(figures)
    val openRates = LocalOpenExchangeRates.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

        Text(
            text = stringResource(
                if (rates.isEmpty()) {
                    Res.string.approximation_footer_unreached
                } else {
                    Res.string.approximation_footer_converted
                }
            ),
            color = colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        rates.forEach { rate -> RateLine(rate) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = openRates)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(Res.string.approximation_footer_action),
                color = colorScheme.primary,
                fontSize = 13.sp,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * One quote, as the reader can check it: one unit of the currency, what it was worth in the
 * base, and **the date of that quote** — which is the last one on or before the figure's own
 * date, so it is often older than the figure and saying so is the point.
 */
@Composable
private fun RateLine(rate: AppliedRate) {
    val formatter = LocalCurrencyFormatter.current

    Text(
        text = stringResource(
            Res.string.approximation_footer_rate,
            rate.currency,
            formatter.format(rate.rate, rate.baseCurrency),
            dayMonthYear.format(rate.date),
        ),
        color = colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )
}

/**
 * The way to the exchange-rate screen, provided by the shell.
 *
 * It is a `CompositionLocal` and not a parameter because the footer is invisible on almost
 * every card of almost every user: threading a navigation callback through every card, and
 * through each of their call sites, to reach something that usually does not render would
 * put the cost of the rare case on all the ordinary ones.
 *
 * There is no default. A no-op default would make a footer that explains a number and then
 * leads nowhere — which is worse than the missing provider it hides.
 */
val LocalOpenExchangeRates = compositionLocalOf<() -> Unit> {
    error("LocalOpenExchangeRates not provided: the approximation footer has nowhere to lead")
}
