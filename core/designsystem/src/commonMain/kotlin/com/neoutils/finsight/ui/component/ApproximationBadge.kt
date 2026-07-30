package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.approximateFigure
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_approximate_badge
import com.neoutils.finsight.resources.money_approximate_converted
import com.neoutils.finsight.resources.money_approximate_none
import com.neoutils.finsight.resources.money_approximate_partial
import com.neoutils.finsight.resources.money_approximate_see_rates
import com.neoutils.finsight.resources.money_approximate_title
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The way out of the `≈` mark — design D21, as a button rather than as a line of text.
 *
 * The mark itself stays where it is: it is the signal, it travels inside the figure, and
 * it is never colour. What changed is the **explanation**. It used to be a permanent
 * 13sp footer on any card holding an approximate figure, which is a lot of card for a
 * sentence most users read once. It is now a 16dp button that opens the sentence, so the
 * card keeps its shape and the explanation keeps its three jobs: it says what the mark
 * means, it says whether a rate was applied *and when it is from*, and it leads to where
 * a rate can be corrected.
 *
 * `≈` at 12–20sp is still not a touch target — which is exactly why the button exists
 * beside it rather than on it.
 *
 * @param figures every figure the surface shows. The badge decides for itself whether it
 * appears, from what it is given, so no screen has to remember to.
 */
@Composable
fun ApproximationBadge(
    figures: List<ConsolidatedAmount>,
    onSeeRates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val approximate = figures.approximateFigure() ?: return
    val modalManager = LocalModalManager.current

    IconButton(
        onClick = {
            modalManager.show(
                ApproximationInfoModal(
                    asOf = approximate.asOf,
                    // Everything a rate reached landed in the base term; anything beside
                    // it is what no rate could touch.
                    convertedSomething = approximate.baseIndex != null,
                    hasUnconvertedPart = approximate.terms.size > 1,
                    onSeeRates = onSeeRates,
                )
            )
        },
        modifier = modifier.size(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.money_approximate_badge),
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * What "approximate" means for *this* figure — which is not one sentence but three, and
 * saying the wrong one is worse than saying nothing.
 *
 * A figure of several currencies with **no rate at all** converted nothing, and telling
 * that user it was "approximate by the rate of 5 July" names a rate that was never
 * applied and does not exist. So the text is chosen from what the reduction actually
 * did: converted everything, converted part of it, or converted nothing.
 */
private class ApproximationInfoModal(
    private val asOf: LocalDate?,
    private val convertedSomething: Boolean,
    private val hasUnconvertedPart: Boolean,
    private val onSeeRates: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current
        val date = asOf?.let { LocalDateFormats.current.monthDayYear.format(it) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.money_approximate_title),
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    !convertedSomething || date == null ->
                        stringResource(Res.string.money_approximate_none)

                    hasUnconvertedPart ->
                        stringResource(Res.string.money_approximate_partial, date)

                    else -> stringResource(Res.string.money_approximate_converted, date)
                },
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    modalManager.dismiss(this@ApproximationInfoModal)
                    onSeeRates()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.money_approximate_see_rates))
            }
        }
    }
}
