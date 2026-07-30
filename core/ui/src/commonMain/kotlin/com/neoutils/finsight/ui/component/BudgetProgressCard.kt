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
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatSingleLine
import com.neoutils.finsight.ui.model.BudgetProgressUi
import com.neoutils.finsight.ui.theme.budgetProgressColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_progress_card_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun BudgetProgressCard(
    budgetProgress: List<BudgetProgressUi>,
    modifier: Modifier = Modifier,
    onBudgetClick: (BudgetProgressUi) -> Unit = {},
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
    progress: BudgetProgressUi,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val accentColor = budgetProgressColor(progress.progress)

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconBox(
            icon = progress.icon,
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
                    text = progress.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    // Declared degradation (design D20): "spent / limit" is a grammar of one
                    // term a side, so the spending is shown as a single line — marked, and
                    // saying a term was left out — rather than truncated by the layout.
                    text = "${formatter.formatSingleLine(progress.spent)} / ${formatter.format(progress.limit)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
            }

            LinearProgressIndicator(
                progress = { progress.progress },
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
