package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.approximationDate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_approximate_footer
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.stringResource

/**
 * The footer of a card that holds at least one approximate figure — design D21.
 *
 * `≈` at 12–20sp is not a touch target, so the explanation and the way out live here
 * instead: one element doing three jobs. It says what the mark means, it reveals that a
 * rate exists **and when it is from**, and it is the target that leads to the rates
 * screen. It renders only when something on the card is approximate — the same way
 * `AccountCard` already gates its adjustment and invoice lines on `!= 0.0`.
 *
 * An icon and a `(aprox.)` suffix were both ruled out: `SummaryCard` holds up to six
 * lines of money in a column already in `SpaceBetween`, and neither six 16dp icons nor
 * eight extra characters fit there. Colour is ruled out too — the palette is taken by
 * transaction nature, and the amber "approximate" would want is literally `Adjustment`.
 *
 * @param figures every figure the card shows. The footer decides for itself whether it
 * appears, from what it is given, so no screen has to remember to.
 */
@Composable
fun ApproximationFooter(
    figures: List<ConsolidatedAmount>,
    onSeeRates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val asOf = figures.approximationDate() ?: return

    Text(
        text = stringResource(
            Res.string.money_approximate_footer,
            LocalDateFormats.current.monthDayYear.format(asOf),
        ),
        fontSize = 13.sp,
        color = colorScheme.onSurfaceVariant,
        modifier = modifier.clickable(onClick = onSeeRates),
    )
}
