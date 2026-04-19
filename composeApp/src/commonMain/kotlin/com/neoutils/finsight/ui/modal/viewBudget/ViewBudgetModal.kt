package com.neoutils.finsight.ui.modal.viewBudget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormModal
import com.neoutils.finsight.ui.modal.deleteBudget.DeleteBudgetModal
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.budgetProgressColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_budget_delete
import com.neoutils.finsight.resources.view_budget_edit
import com.neoutils.finsight.resources.view_budget_exceeded_by_label
import com.neoutils.finsight.resources.view_budget_limit_label
import com.neoutils.finsight.resources.view_budget_percentage_label
import com.neoutils.finsight.resources.view_budget_remaining_label
import com.neoutils.finsight.resources.view_budget_spent_label
import org.jetbrains.compose.resources.stringResource

class ViewBudgetModal(
    private val budgetProgress: BudgetProgress,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val formatter = LocalCurrencyFormatter.current
        val manager = LocalModalManager.current
        val budget = budgetProgress.budget
        val accentColor = budgetProgressColor(budgetProgress.progress)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategoryIconBox(
                    icon = CategoryLazyIcon(budget.iconKey),
                    tint = accentColor,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp),
                )

                Text(
                    text = budget.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (budget.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(budget.categories) { category ->
                        val categoryColor = when (category.type) {
                            Category.Type.INCOME -> Income
                            Category.Type.EXPENSE -> Expense
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(categoryColor.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            CategoryIconBox(
                                category = category,
                                contentPadding = PaddingValues(3.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = category.name,
                                fontSize = 13.sp,
                                color = categoryColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Column {
                if (budget.limitType == LimitType.PERCENTAGE && budget.percentage != null) {
                    val pctLabel = buildString {
                        append("${budget.percentage.toInt()}%")
                        budgetProgress.recurringLabel?.let { append(" de $it") }
                    }
                    DetailRow(
                        label = stringResource(Res.string.view_budget_percentage_label),
                        value = pctLabel,
                        onClick = budgetProgress.recurring?.let { recurring ->
                            { manager.show(ViewRecurringModal(recurring)) }
                        },
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
                DetailRow(
                    label = stringResource(Res.string.view_budget_limit_label),
                    value = formatter.format(budget.amount),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_budget_spent_label),
                    value = formatter.format(budgetProgress.spent),
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (budgetProgress.isExceeded) {
                    DetailRow(
                        label = stringResource(Res.string.view_budget_exceeded_by_label),
                        value = formatter.format(budgetProgress.spent - budget.amount),
                    )
                } else {
                    DetailRow(
                        label = stringResource(Res.string.view_budget_remaining_label),
                        value = formatter.format(budgetProgress.remaining),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { budgetProgress.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accentColor,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { manager.show(DeleteBudgetModal(budget)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.error,
                    ),
                    border = BorderStroke(width = 1.dp, color = colorScheme.error),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.view_budget_delete),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                OutlinedButton(
                    onClick = { manager.show(BudgetFormModal(budget)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Info,
                    ),
                    border = BorderStroke(width = 1.dp, color = Info),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.view_budget_edit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ) {
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                )
            }
        }
    }
}
