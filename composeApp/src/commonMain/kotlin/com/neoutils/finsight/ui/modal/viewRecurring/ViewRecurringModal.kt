package com.neoutils.finsight.ui.modal.viewRecurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_screen_day
import com.neoutils.finsight.resources.recurring_status_active
import com.neoutils.finsight.resources.recurring_status_inactive
import com.neoutils.finsight.resources.view_recurring_account_label
import com.neoutils.finsight.resources.view_recurring_amount_label
import com.neoutils.finsight.resources.view_recurring_category_label
import com.neoutils.finsight.resources.view_recurring_credit_card_label
import com.neoutils.finsight.resources.view_recurring_day_label
import com.neoutils.finsight.resources.view_recurring_delete
import com.neoutils.finsight.resources.view_recurring_edit
import com.neoutils.finsight.resources.view_recurring_reactivate
import com.neoutils.finsight.resources.view_recurring_status_label
import com.neoutils.finsight.resources.view_recurring_stop
import com.neoutils.finsight.resources.view_recurring_type_label
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringModal
import com.neoutils.finsight.ui.modal.reactivateRecurring.ReactivateRecurringModal
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.stopRecurring.StopRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Warning
import org.jetbrains.compose.resources.stringResource

class ViewRecurringModal(
    private val recurring: Recurring,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val formatter = LocalCurrencyFormatter.current
        val typeColor = if (recurring.type.isIncome) Income else Expense

        val typeLabel = if (recurring.type.isIncome) {
            stringResource(Res.string.recurring_income)
        } else {
            stringResource(Res.string.recurring_expense)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (recurring.category != null) {
                    CategoryIconBox(
                        category = recurring.category,
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(16.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                } else {
                    Surface(
                        color = typeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (recurring.type.isIncome) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                Text(
                    text = recurring.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column {
                DetailRow(
                    label = stringResource(Res.string.view_recurring_type_label),
                    value = typeLabel,
                    valueColor = typeColor,
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_amount_label),
                    value = formatter.format(recurring.amount),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_day_label),
                    value = stringResource(Res.string.recurring_screen_day, recurring.dayOfMonth),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DetailRow(
                    label = stringResource(Res.string.view_recurring_status_label),
                    value = if (recurring.isActive) {
                        stringResource(Res.string.recurring_status_active)
                    } else {
                        stringResource(Res.string.recurring_status_inactive)
                    },
                    valueColor = if (recurring.isActive) Income else Warning,
                )
                recurring.account?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_account_label),
                        value = it.name,
                    )
                }
                recurring.creditCard?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_credit_card_label),
                        value = it.name,
                    )
                }
                recurring.category?.let {
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow(
                        label = stringResource(Res.string.view_recurring_category_label),
                        value = it.name,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (recurring.isActive) {
                    OutlinedButton(
                        onClick = { manager.show(StopRecurringModal(recurring)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Warning,
                        ),
                        border = BorderStroke(width = 1.dp, color = Warning),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(Res.string.view_recurring_stop),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { manager.show(ReactivateRecurringModal(recurring)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Income,
                        ),
                        border = BorderStroke(width = 1.dp, color = Income),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(Res.string.view_recurring_reactivate),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                OutlinedButton(
                    onClick = { manager.show(RecurringFormModal(recurring)) },
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
                        text = stringResource(Res.string.view_recurring_edit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (!recurring.isActive) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { manager.show(DeleteRecurringModal(recurring)) },
                    modifier = Modifier.fillMaxWidth(),
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
                        text = stringResource(Res.string.view_recurring_delete),
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
