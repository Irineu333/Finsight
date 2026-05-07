@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.feature.categories.modal.viewCategory

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
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.feature.categories.component.CategoryIconBox
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.core.ui.component.MonthSelector
import com.neoutils.finsight.feature.categories.modal.deleteCategory.DeleteCategoryModal
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.core.ui.theme.Expense
import com.neoutils.finsight.core.ui.theme.Income
import com.neoutils.finsight.core.ui.theme.Info
import com.neoutils.finsight.feature.categories.resources.Res
import com.neoutils.finsight.feature.categories.resources.view_category_delete
import com.neoutils.finsight.feature.categories.resources.view_category_edit
import com.neoutils.finsight.feature.categories.resources.view_category_total_received
import com.neoutils.finsight.feature.categories.resources.view_category_total_spent
import com.neoutils.finsight.feature.categories.resources.view_category_transactions_month
import com.neoutils.finsight.feature.categories.resources.view_category_type_expense
import com.neoutils.finsight.feature.categories.resources.view_category_type_income
import com.neoutils.finsight.feature.categories.resources.view_category_unavailable
import com.neoutils.finsight.feature.categories.resources.view_category_close
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class ViewCategoryModal(
    private val categoryId: Long
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val viewModel = koinViewModel<ViewCategoryViewModel> { parametersOf(categoryId) }
        val uiState by viewModel.uiState.collectAsState()

        when (val state = uiState) {
            ViewCategoryUiState.Loading -> LoadingContent()
            ViewCategoryUiState.Empty -> EmptyContent()
            is ViewCategoryUiState.Content -> Content(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(96.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    @Composable
    private fun EmptyContent() {
        val manager = LocalModalManager.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.view_category_unavailable),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { manager.dismiss() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(text = stringResource(Res.string.view_category_close))
            }
        }
    }

    @Composable
    private fun Content(
        state: ViewCategoryUiState.Content,
        onAction: (ViewCategoryAction) -> Unit,
    ) {
        val formatter = LocalCurrencyFormatter.current
        val manager = LocalModalManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {

            MonthSelector(
                selectedYearMonth = state.selectedYearMonth,
                onPreviousMonth = {
                    onAction(ViewCategoryAction.PreviousMonth)
                },
                onNextMonth = {
                    onAction(ViewCategoryAction.NextMonth)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBox(
                    category = state.category,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (state.category.type.isIncome) stringResource(Res.string.view_category_type_income) else stringResource(Res.string.view_category_type_expense),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (state.category.type.isIncome) Income else Expense
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.category.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = if (state.category.type.isIncome) stringResource(Res.string.view_category_total_received) else stringResource(Res.string.view_category_total_spent),
                value = formatter.format(state.totalAmount),
                valueColor = if (state.category.type.isIncome) Income else Expense
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_category_transactions_month),
                value = state.transactionCount.toString()
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        manager.show(DeleteCategoryModal(state.category))
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
                        manager.show(CategoryFormModal(categoryId = state.category.id))
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
