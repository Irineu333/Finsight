@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewCategory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.component.MonthSelector
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.modal.archiveCategory.ArchiveCategoryModal
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryModal
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.view_category_edit
import com.neoutils.finsight.resources.view_category_total_received
import com.neoutils.finsight.resources.view_category_total_spent
import com.neoutils.finsight.resources.view_category_transactions_month
import com.neoutils.finsight.resources.view_category_type_expense
import com.neoutils.finsight.resources.view_category_type_income
import com.neoutils.finsight.resources.view_category_unarchive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

class ViewCategoryModal(
    private val categoryId: Long,
) : AdaptiveModal() {

    // Both slots (RenderBody/RenderActions) render under this modal as their
    // ViewModelStoreOwner, so this resolves the same ViewModel and collects the same
    // state — the resolution lives here once, not copied into each slot, and the
    // collector is lifecycle-aware because the state reacts to observeLedgerChanges()
    // (design D8).
    @Composable
    private fun rememberViewState(): Pair<ViewCategoryViewModel, ViewCategoryUiState> {
        val viewModel = koinViewModel<ViewCategoryViewModel> { parametersOf(categoryId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        return viewModel to uiState
    }

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val (viewModel, uiState) = rememberViewState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewCategoryEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewCategoryUiState.Loading -> DetailLoadingState()
            ViewCategoryUiState.Error -> DetailErrorState()
            is ViewCategoryUiState.Content -> ContentBody(
                uiState = state,
                onAction = viewModel::onAction,
            )
        }
    }

    @Composable
    private fun ContentBody(
        uiState: ViewCategoryUiState.Content,
        onAction: (ViewCategoryAction) -> Unit,
    ) {
        val formatter = LocalCurrencyFormatter.current

        val isIncome = uiState.category.type.isIncome
        val typeLabel = stringResource(
            if (isIncome) Res.string.view_category_type_income else Res.string.view_category_type_expense
        )
        val totalLabel = stringResource(
            if (isIncome) Res.string.view_category_total_received else Res.string.view_category_total_spent
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {

            MonthSelector(
                selectedYearMonth = uiState.selectedYearMonth,
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
                    category = uiState.category,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = uiState.category.displayColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = uiState.category.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = totalLabel,
                value = formatter.format(uiState.totalAmount),
                valueColor = uiState.category.displayColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_category_transactions_month),
                value = uiState.transactionCount.toString()
            )
        }
    }

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val (viewModel, uiState) = rememberViewState()
        val content = uiState as? ViewCategoryUiState.Content ?: return

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Retire and unarchive are mutually exclusive by archived state: a category
            // is only archived once it has entries, so the two offers never overlap. A
            // screen decides whether it offers an action, never which one it is.
            if (content.category.isArchived) {
                OutlinedActionButton(
                    label = stringResource(Res.string.view_category_unarchive),
                    icon = Icons.Default.Unarchive,
                    contentColor = colorScheme.primary,
                    onClick = { viewModel.onAction(ViewCategoryAction.Unarchive) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                OutlinedActionButton(
                    label = stringResource(content.retireAction.label),
                    icon = content.retireAction.icon,
                    contentColor = colorScheme.error,
                    onClick = {
                        manager.show(
                            when (content.retireAction) {
                                RetireAction.DELETE -> DeleteCategoryModal(content.category)
                                RetireAction.ARCHIVE -> ArchiveCategoryModal(content.category)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedActionButton(
                label = stringResource(Res.string.view_category_edit),
                icon = Icons.Default.Edit,
                contentColor = Info,
                onClick = { manager.show(CategoryFormModal(content.category)) },
                modifier = Modifier.weight(1f),
            )
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
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor
            )
        }
    }
}
