@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.component.MonthSelector
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryModal
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_category_delete
import com.neoutils.finsight.resources.view_category_edit
import com.neoutils.finsight.resources.view_category_total_received
import com.neoutils.finsight.resources.view_category_total_spent
import com.neoutils.finsight.resources.view_category_transactions_month
import com.neoutils.finsight.resources.view_category_type_expense
import com.neoutils.finsight.resources.view_category_type_income
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ViewCategoryModal(
    private val category: Category
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current

        val viewModel = koinViewModel<ViewCategoryViewModel> { parametersOf(category) }

        val uiState by viewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {

            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
                onPreviousMonth = {
                    viewModel.onAction(ViewCategoryAction.PreviousMonth)
                },
                onNextMonth = {
                    viewModel.onAction(ViewCategoryAction.NextMonth)
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBox(
                    category = uiState.category,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (uiState.category.type.isIncome) stringResource(Res.string.view_category_type_income) else stringResource(Res.string.view_category_type_expense),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.category.type.isIncome) Income else Expense
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = uiState.category.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = if (uiState.category.type.isIncome) stringResource(Res.string.view_category_total_received) else stringResource(Res.string.view_category_total_spent),
                value = uiState.totalAmount.toMoneyFormat(),
                valueColor = if (uiState.category.type.isIncome) Income else Expense
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_category_transactions_month),
                value = uiState.transactionCount.toString()
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        manager.show(DeleteCategoryModal(uiState.category))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.error,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = colorScheme.error,
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.view_category_delete),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = {
                        manager.show(CategoryFormModal(uiState.category))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Info,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Info,
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.view_category_edit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}
