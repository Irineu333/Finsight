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
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatOrUnresolved
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.ApproximationBadge
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.DetailPaneController
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import org.koin.compose.koinInject
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormModal
import com.neoutils.finsight.ui.modal.deleteBudget.DeleteBudgetModal
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.domain.model.LimitType
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewBudgetModal(
    private val budgetId: Long,
) : AdaptiveModal() {

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val recurringEntry = koinInject<RecurringEntry>()

        val viewModel = koinViewModel<ViewBudgetViewModel> { parametersOf(budgetId) }
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewBudgetEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewBudgetUiState.Loading -> DetailLoadingState()
            ViewBudgetUiState.Error -> DetailErrorState()
            is ViewBudgetUiState.Content -> ContentBody(
                budgetProgress = state.budgetProgress,
                detailController = detailController,
                recurringEntry = recurringEntry,
            )
        }
    }

    @Composable
    private fun ContentBody(
        budgetProgress: BudgetProgress,
        detailController: DetailPaneController,
        recurringEntry: RecurringEntry,
    ) {
        val formatter = LocalCurrencyFormatter.current
        val navController = LocalNavController.current
        val budget = budgetProgress.budget
        // Limit and spending alike are denominated by the budget, never by the base: the
        // currency is chosen once, at creation, and stays the meaning of both numbers
        // (design D13).
        val currency = budget.currency
        // No fraction, no "how full" to colour by — the neutral accent instead.
        val accentColor = budgetProgress.progress?.let { budgetProgressColor(it) }
            ?: colorScheme.onSurfaceVariant

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryIconBox(
                    icon = budget.icon,
                    tint = accentColor,
                    modifier = Modifier.size(64.dp),
                    contentPadding = PaddingValues(16.dp),
                    shape = RoundedCornerShape(16.dp),
                )

                Text(
                    text = budget.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // The explanation lives **here and not on the list**, because the failure
                // is a property of one budget: only this budget's categories hold a
                // currency no rate reaches, and a badge on the card that shows three of
                // them could not say which. Top-right corner, like every other badge.
                ApproximationBadge(
                    figures = listOfNotNull(budgetProgress.spentFigure),
                    onSeeRates = { navController.navigate(ExchangeRatesRoute) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (budget.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(budget.categories) { category ->
                        val categoryColor = category.displayColor
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
                val percentage = budget.percentage
                if (budget.limitType == LimitType.PERCENTAGE && percentage != null) {
                    val pctLabel = buildString {
                        append("${percentage.toInt()}%")
                        budgetProgress.recurringLabel?.let { append(" de $it") }
                    }
                    DetailRow(
                        label = stringResource(Res.string.view_budget_percentage_label),
                        value = pctLabel,
                        onClick = budgetProgress.recurring?.let { recurring ->
                            { detailController.show(recurringEntry.viewRecurringModal(recurring.id)) }
                        },
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
                DetailRow(
                    label = stringResource(Res.string.view_budget_limit_label),
                    value = formatter.format(budget.amount, currency),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // The one surface with room for the truth: the spending as the figure it
                // is, stacked, instead of the `***` a one-line label has to fall back to.
                // The money is known — only its expression in the limit's currency is not.
                DetailRow(
                    label = stringResource(Res.string.view_budget_spent_label),
                    value = {
                        val figure = budgetProgress.spentFigure
                        if (figure != null) {
                            MoneyText(
                                figure = figure,
                                style = LocalTextStyle.current.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        } else {
                            Text(
                                text = formatter.formatOrUnresolved(budgetProgress.spentAmount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (budgetProgress.isExceeded) {
                    DetailRow(
                        label = stringResource(Res.string.view_budget_exceeded_by_label),
                        value = formatter.formatOrUnresolved(budgetProgress.exceededAmount),
                    )
                } else {
                    DetailRow(
                        label = stringResource(Res.string.view_budget_remaining_label),
                        value = formatter.formatOrUnresolved(budgetProgress.remainingAmount),
                    )
                }
            }

            // No fraction, no bar: an empty track claims "nothing spent yet", which is
            // precisely what is not known when part of the spending cannot be priced.
            budgetProgress.progress?.let { fraction ->
                Spacer(modifier = Modifier.height(8.dp))

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

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val viewModel = koinViewModel<ViewBudgetViewModel> { parametersOf(budgetId) }
        val uiState by viewModel.uiState.collectAsState()

        val budgetProgress = (uiState as? ViewBudgetUiState.Content)?.budgetProgress ?: return

        Actions(budgetProgress = budgetProgress, manager = manager)
    }

    @Composable
    private fun Actions(
        budgetProgress: BudgetProgress,
        manager: ModalManager,
    ) {
        val budget = budgetProgress.budget

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
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

    /**
     * The slot form, for a value that is not one line of text — a figure of several terms,
     * stacked. The string form below delegates to it, so the two can never drift apart in
     * spacing or alignment.
     *
     * Top-aligned rather than centred: with a stacked value the label belongs beside the
     * *first* term, which is the one that carries the surface's own weight.
     */
    @Composable
    private fun DetailRow(
        label: String,
        value: @Composable () -> Unit,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
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
                value()
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface,
        onClick: (() -> Unit)? = null,
    ) = DetailRow(
        label = label,
        onClick = onClick,
        value = {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        },
    )

}
