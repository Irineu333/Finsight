package com.neoutils.finsight.ui.modal.viewGoal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.GoalProgress
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.goalForm.GoalFormModal
import com.neoutils.finsight.ui.modal.deleteGoal.DeleteGoalModal
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.goalProgressColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_goal_above_target_label
import com.neoutils.finsight.resources.view_goal_delete
import com.neoutils.finsight.resources.view_goal_edit
import com.neoutils.finsight.resources.view_goal_earned_label
import com.neoutils.finsight.resources.view_goal_missing_label
import com.neoutils.finsight.resources.view_goal_target_label
import org.jetbrains.compose.resources.stringResource

class ViewGoalModal(
    private val goalProgress: GoalProgress,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val formatter = LocalCurrencyFormatter.current
        val manager = LocalModalManager.current
        val goal = goalProgress.goal
        val accentColor = goalProgressColor(goalProgress.progress)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategoryIconBox(
                    icon = goal.icon,
                    tint = accentColor,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp),
                )

                Text(
                    text = goal.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }

            if (goal.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(goal.categories) { category ->
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(
                    label = stringResource(Res.string.view_goal_target_label),
                    value = formatter.format(goal.amount),
                )
                DetailRow(
                    label = stringResource(Res.string.view_goal_earned_label),
                    value = formatter.format(goalProgress.earned),
                )
                if (goalProgress.isReached) {
                    DetailRow(
                        label = stringResource(Res.string.view_goal_above_target_label),
                        value = formatter.format(goalProgress.exceeded),
                    )
                } else {
                    DetailRow(
                        label = stringResource(Res.string.view_goal_missing_label),
                        value = formatter.format(goalProgress.remaining),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { goalProgress.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accentColor,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { manager.show(DeleteGoalModal(goal)) },
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
                        text = stringResource(Res.string.view_goal_delete),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                OutlinedButton(
                    onClick = { manager.show(GoalFormModal(goal)) },
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
                        text = stringResource(Res.string.view_goal_edit),
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
        valueColor: androidx.compose.ui.graphics.Color = colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}
