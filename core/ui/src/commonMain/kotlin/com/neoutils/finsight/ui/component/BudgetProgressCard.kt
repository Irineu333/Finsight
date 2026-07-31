package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatOrUnresolved
import com.neoutils.finsight.ui.theme.budgetProgressColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_progress_card_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun BudgetProgressCard(
    budgetProgress: List<BudgetProgress>,
    modifier: Modifier = Modifier,
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
            Text(
                text = stringResource(Res.string.budget_progress_card_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

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
                    text = "${formatter.formatOrUnresolved(progress.spentAmount)} / " +
                        formatter.format(progress.limitAmount),
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
