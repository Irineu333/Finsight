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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.ConsolidationNotice
import com.neoutils.finsight.extension.approximateFigure
import com.neoutils.finsight.extension.consolidationNotice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_approximate_badge
import com.neoutils.finsight.resources.money_approximate_converted
import com.neoutils.finsight.resources.money_approximate_none
import com.neoutils.finsight.resources.money_approximate_partial
import com.neoutils.finsight.resources.money_approximate_see_rates
import com.neoutils.finsight.resources.money_approximate_title
import com.neoutils.finsight.resources.money_stacked_badge
import com.neoutils.finsight.resources.money_stacked_title
import com.neoutils.finsight.resources.money_unresolved_badge
import com.neoutils.finsight.resources.money_unresolved_body
import com.neoutils.finsight.resources.money_unresolved_title
import com.neoutils.finsight.ui.theme.Error
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The way out of the `≈` mark — design D21, as a button rather than as a line of text, and
 * at **three levels of severity** rather than one.
 *
 * The mark itself stays where it is: it is the signal, it travels inside the figure, and on
 * the number it is never colour. What this carries is the **explanation**, and it says how
 * much consolidation is costing this surface, because the three cases are not the same news
 * and a single grey dot said all of them identically:
 *
 * | [ConsolidationNotice] | reads | the surface |
 * |---|---|---|
 * | [ConsolidationNotice.CONVERTED] | grey, `Info` | shows one number, approximate |
 * | [ConsolidationNotice.STACKED] | amber, `WarningAmber` | shows parts where a total was expected |
 * | [ConsolidationNotice.UNRESOLVED] | red, `ErrorOutline` | cannot do part of its job |
 *
 * **Colour here does not contradict D21, and the distinction is exact.** D21 rules colour
 * out for the mark *on the amount*, on two grounds: the palette is spoken for by
 * transaction nature, and colour alone fails whoever does not read it. Neither applies to
 * a badge that is not on an amount and never travels alone — each level carries its own
 * glyph, its own accessibility label, and its own sentence behind one tap, which is the
 * *icon **and** label* the project already requires of `CategoryCard`. The amber is the
 * same value as `Adjustment` and that is why it is worth being explicit: on a 16dp glyph in
 * the card's corner, beside no amount, there is nothing for it to be mistaken for.
 *
 * It absorbed the badge that used to explain a missing proportion bar. That was
 * [ConsolidationNotice.UNRESOLVED] under another name, and two components meant two
 * affordances for one question — while both KDocs claimed the user learns only one.
 *
 * @param figures every figure the surface shows. The badge decides for itself whether it
 * appears and at which level, so no screen has to remember to.
 * @param unresolved whether this surface had to leave something out for want of a rate: a
 * bar not drawn, a proportion not taken, a total shown as `***`. It is declared because it
 * cannot be read off the figures — what it describes is *what is not there*.
 */
@Composable
fun ConsolidationBadge(
    figures: List<ConsolidatedAmount>,
    onSeeRates: () -> Unit,
    modifier: Modifier = Modifier,
    unresolved: Boolean = false,
) {
    val notice = figures.consolidationNotice(unresolved) ?: return
    val modalManager = LocalModalManager.current

    IconButton(
        onClick = {
            modalManager.show(
                ConsolidationInfoModal(
                    notice = notice,
                    asOf = figures.approximateFigure()?.asOf,
                    // Whether a rate **passed through** the base term, not whether one
                    // exists. A base term is there whenever money was already in the base
                    // and stayed put — `{BRL 100, USD 50}` with no rate at all has one —
                    // so `baseIndex != null` answers a different question, and answering
                    // this one with it names a rate that was never applied.
                    convertedSomething = figures.approximateFigure()?.base?.isApproximate == true,
                    onSeeRates = onSeeRates,
                )
            )
        },
        modifier = modifier.size(24.dp),
    ) {
        Icon(
            imageVector = notice.icon,
            contentDescription = stringResource(notice.label),
            tint = notice.tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

private val ConsolidationNotice.icon: ImageVector
    get() = when (this) {
        ConsolidationNotice.CONVERTED -> Icons.Outlined.Info
        ConsolidationNotice.STACKED -> Icons.Outlined.WarningAmber
        ConsolidationNotice.UNRESOLVED -> Icons.Outlined.ErrorOutline
    }

private val ConsolidationNotice.tint: Color
    @Composable get() = when (this) {
        // Deliberately the quiet one: the number is a number, and this is provenance.
        ConsolidationNotice.CONVERTED -> colorScheme.onSurfaceVariant
        ConsolidationNotice.STACKED -> Warning
        ConsolidationNotice.UNRESOLVED -> Error
    }

private val ConsolidationNotice.label: StringResource
    get() = when (this) {
        ConsolidationNotice.CONVERTED -> Res.string.money_approximate_badge
        ConsolidationNotice.STACKED -> Res.string.money_stacked_badge
        ConsolidationNotice.UNRESOLVED -> Res.string.money_unresolved_badge
    }

/**
 * What the badge means for *this* surface — which is not one sentence but four, and saying
 * the wrong one is worse than saying nothing.
 *
 * The level chooses the title, and within the stacked level the reduction chooses the body:
 * a figure of several currencies with **no rate at all** converted nothing, and telling that
 * user it was "approximate by the rate of 5 July" names a rate that was never applied and
 * does not exist.
 */
private class ConsolidationInfoModal(
    private val notice: ConsolidationNotice,
    private val asOf: LocalDate?,
    private val convertedSomething: Boolean,
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
                text = stringResource(
                    when (notice) {
                        ConsolidationNotice.CONVERTED -> Res.string.money_approximate_title
                        ConsolidationNotice.STACKED -> Res.string.money_stacked_title
                        ConsolidationNotice.UNRESOLVED -> Res.string.money_unresolved_title
                    }
                ),
                style = typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    notice == ConsolidationNotice.UNRESOLVED ->
                        stringResource(Res.string.money_unresolved_body)

                    !convertedSomething || date == null ->
                        stringResource(Res.string.money_approximate_none)

                    notice == ConsolidationNotice.STACKED ->
                        stringResource(Res.string.money_approximate_partial, date)

                    else -> stringResource(Res.string.money_approximate_converted, date)
                },
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    modalManager.dismiss(this@ConsolidationInfoModal)
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
