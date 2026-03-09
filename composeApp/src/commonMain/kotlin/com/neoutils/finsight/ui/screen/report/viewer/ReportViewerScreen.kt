@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.toPercentageString
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.screen.home.AppRoute
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReportViewerScreen(
    route: AppRoute.ReportViewer,
    onNavigateBack: () -> Unit = {},
    viewModel: ReportViewerViewModel = koinViewModel { parametersOf(route) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReportViewerContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun ReportViewerContent(
    uiState: ReportViewerUiState,
    onNavigateBack: () -> Unit,
) {
    val modalManager = LocalModalManager.current
    val formatter = LocalCurrencyFormatter.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.report_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        when (uiState) {
            is ReportViewerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ReportViewerUiState.Content -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = uiState.perspectiveLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = uiState.dateRangeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.surfaceContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SummaryRow(
                                    label = stringResource(Res.string.report_viewer_summary_income),
                                    value = formatter.format(uiState.income),
                                    valueColor = Income,
                                )
                                SummaryRow(
                                    label = stringResource(Res.string.report_viewer_summary_expense),
                                    value = formatter.format(uiState.expense),
                                    valueColor = Expense,
                                )
                                HorizontalDivider()
                                SummaryRow(
                                    label = stringResource(Res.string.report_viewer_summary_balance),
                                    value = formatter.format(uiState.balance),
                                    valueColor = if (uiState.balance >= 0) Income else Expense,
                                    bold = true,
                                )
                            }
                        }
                    }

                    if (!uiState.categorySpending.isNullOrEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.report_viewer_spending_by_category),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                            )
                        }

                        items(uiState.categorySpending, key = { it.category.id }) { spending ->
                            ListItem(
                                headlineContent = { Text(spending.category.name) },
                                supportingContent = {
                                    LinearProgressIndicator(
                                        progress = { (spending.percentage / 100).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatter.format(spending.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Expense,
                                        )
                                        Text(
                                            text = spending.percentage.toPercentageString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    if (!uiState.transactions.isNullOrEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.report_viewer_transactions),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                            )
                        }

                        uiState.transactions.forEach { (date, operations) ->
                            item(key = "date_$date") {
                                Text(
                                    text = date.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
                                )
                            }

                            items(operations, key = { "op_${it.id}" }) { operation ->
                                OperationCard(
                                    operation = operation,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = {
                                        when {
                                            operation.type.isAdjustment -> modalManager.show(ViewAdjustmentModal(operation))
                                            else -> modalManager.show(ViewOperationModal(operation))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
        )
    }
}
