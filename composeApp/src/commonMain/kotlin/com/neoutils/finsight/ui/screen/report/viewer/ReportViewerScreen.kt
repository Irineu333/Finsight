@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.BalanceCard
import com.neoutils.finsight.ui.component.BalanceCardConfig
import com.neoutils.finsight.ui.component.CategorySpendingCard
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.screen.home.AppRoute
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.LocalDateFormats
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
    val dateFormats = LocalDateFormats.current

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
        contentWindowInsets = WindowInsets.safeDrawing,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.report_viewer_summary_balance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatter.format(uiState.balance),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.balance >= 0) Income else Expense,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.report_viewer_summary_initial_balance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = formatter.format(uiState.initialBalance),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.initialBalance >= 0) Income else Expense,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            BalanceCard(
                                balance = uiState.income,
                                modifier = Modifier.weight(1f),
                                config = BalanceCardConfig.Income,
                            )
                            BalanceCard(
                                balance = uiState.expense,
                                modifier = Modifier.weight(1f),
                                config = BalanceCardConfig.Expense,
                            )
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

                        item {
                            CategorySpendingCard(
                                categorySpending = uiState.categorySpending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
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
                                    text = dateFormats.formatRelativeDate(date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 4.dp),
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