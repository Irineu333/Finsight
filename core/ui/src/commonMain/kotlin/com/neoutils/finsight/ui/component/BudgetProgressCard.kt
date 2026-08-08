package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.UNRESOLVED_AMOUNT
import com.neoutils.finsight.ui.theme.budgetProgressColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_progress_card_title
import org.jetbrains.compose.resources.stringResource

/**
 * @param onSeeRates where the badge that explains an approximate or unresolved figure
 * leads. `null` leaves the widget without that way out.
 */
@Composable
fun BudgetProgressCard(
    budgetProgress: List<BudgetProgress>,
    modifier: Modifier = Modifier,
    onSeeRates: (() -> Unit)? = null,
    onBudgetClick: (BudgetProgress) -> Unit = {},
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.budget_progress_card_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                // This widget holds several budgets, so the badge speaks for the card
                // rather than for a row — the same shape `SummaryCard` already has with
                // its six lines of money. Which budget is which is answered by opening
                // one, where the badge sits on that budget alone.
                if (onSeeRates != null) {
                    ConsolidationBadge(
                        figures = budgetProgress.take(3).mapNotNull { it.spentFigure },
                        onSeeRates = onSeeRates,
                        // A row of this widget that could not be resolved shows `***`
                        // instead of a bar, and a thing that is not drawn cannot explain
                        // itself — so the card says it for whichever row lost one.
                        unresolved = budgetProgress.take(3).any { !it.isResolved },
                    )
                }
            }

            budgetProgress.take(3).forEach { progress ->
                BudgetProgressRow(
                    progress = progress,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { onBudgetClick(progress) },
                )
            }
        }
    }
}

@Composable
private fun BudgetProgressRow(
    progress: BudgetProgress,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    // With no fraction there is no "how full" to colour by, so the accent falls back to
    // the neutral one rather than to the colour of an empty bar (which reads "on track").
    val accentColor = progress.progress?.let { budgetProgressColor(it) }
        ?: colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconBox(
            icon = progress.budget.icon,
            tint = accentColor,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.size(40.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = progress.budget.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                // Both figures are denominated by the limit, never by the base (design
                // D13). And when the spending cannot be priced there is no total to put
                // here: `***` says so in the width of an amount, where a confident
                // `R$ 0,00` used to say the opposite (design D20).
                Text(
                    text = buildAnnotatedString {
                        val spent = progress.spentAmount
                        if (spent != null) {
                            append(formatter.format(spent))
                        } else {
                            // The parts do not fit this label — it is one line with a
                            // grammar of its own — so what is shown is their absence, and
                            // quietly: the variant colour says "no number here" without
                            // competing with the limit beside it.
                            withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                append(UNRESOLVED_AMOUNT)
                            }
                        }
                        append(" / ")
                        append(formatter.format(progress.limitAmount))
                    },
                    modifier = Modifier.testTag("dashboard_budget_amount"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
            }

            // No fraction, no bar — an empty track is the claim "nothing spent yet", which
            // is exactly what is not known. The row keeps its height from the icon, so
            // dropping the bar does not change the shape of the card.
            progress.progress?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accentColor,
                    trackColor = colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                    gapSize = (-4).dp,
                )
            }
        }
    }
}
