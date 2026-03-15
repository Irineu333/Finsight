package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.report_config_include_income_by_category
import com.neoutils.finsight.resources.report_config_include_spending_by_category
import com.neoutils.finsight.resources.report_config_include_transactions
import org.jetbrains.compose.resources.stringResource

@Composable
fun SectionsCard(
    includeSpendingByCategory: Boolean,
    includeIncomeByCategory: Boolean,
    includeTransactionList: Boolean,
    onToggleSpendingByCategory: (Boolean) -> Unit,
    onToggleIncomeByCategory: (Boolean) -> Unit,
    onToggleTransactionList: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSpendingByCategory(!includeSpendingByCategory) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.report_config_include_spending_by_category),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = includeSpendingByCategory,
                onCheckedChange = onToggleSpendingByCategory,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primary.copy(alpha = 0.35f),
                    checkedBorderColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                    uncheckedBorderColor = colorScheme.outline,
                ),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleIncomeByCategory(!includeIncomeByCategory) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.report_config_include_income_by_category),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = includeIncomeByCategory,
                onCheckedChange = onToggleIncomeByCategory,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primary.copy(alpha = 0.35f),
                    checkedBorderColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                    uncheckedBorderColor = colorScheme.outline,
                ),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleTransactionList(!includeTransactionList) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.report_config_include_transactions),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = includeTransactionList,
                onCheckedChange = onToggleTransactionList,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.primary,
                    checkedTrackColor = colorScheme.primary.copy(alpha = 0.35f),
                    checkedBorderColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = colorScheme.surfaceVariant,
                    uncheckedBorderColor = colorScheme.outline,
                ),
            )
        }
    }
}
