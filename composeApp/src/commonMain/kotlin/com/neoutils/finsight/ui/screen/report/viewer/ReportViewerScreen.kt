@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.CategorySpendingCard
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.screen.home.AppRoute
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.stringUiText
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

    val labels = ReportHtmlLabels(
        balance = stringResource(Res.string.report_viewer_summary_balance),
        initialBalance = stringResource(Res.string.report_viewer_summary_initial_balance),
        income = stringResource(Res.string.report_viewer_summary_income),
        expense = stringResource(Res.string.report_viewer_summary_expense),
        spendingByCategory = stringResource(Res.string.report_viewer_spending_by_category),
        transactions = stringResource(Res.string.report_viewer_transactions),
    )

    val content = uiState as? ReportViewerUiState.Content

    val perspectiveBadgeText = content?.perspectiveBadge?.let { stringUiText(it) } ?: ""

    val reportHtml = remember(uiState) {
        content?.let {
            buildReportHtml(
                state = it,
                perspectiveBadgeText = perspectiveBadgeText,
                formatter = formatter,
                dateFormats = dateFormats,
                labels = labels,
            )
        } ?: ""
    }

    val printAction = rememberPrintReportAction(
        html = reportHtml,
        title = content?.perspectiveLabel ?: "",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.report_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = printAction,
                        enabled = reportHtml.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = stringResource(Res.string.report_viewer_print),
                        )
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
                        ReportContextCard(
                            perspectiveLabel = uiState.perspectiveLabel,
                            perspectiveBadge = uiState.perspectiveBadge,
                            perspectiveIconKey = uiState.perspectiveIconKey,
                            startDate = uiState.startDate,
                            endDate = uiState.endDate,
                            balance = uiState.balance,
                            initialBalance = uiState.initialBalance,
                            income = uiState.income,
                            expense = uiState.expense,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }

                    if (!uiState.categorySpending.isNullOrEmpty()) {
                        item {
                            CategorySpendingCard(
                                categorySpending = uiState.categorySpending,
                                onCategoryClick = { modalManager.show(ViewCategoryModal(it)) },
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