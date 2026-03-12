@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.collect
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
    val dateFormats = LocalDateFormats.current
    val formatter = LocalCurrencyFormatter.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            snackbarHostState.showSnackbar(event.asString())
        }
    }

    ReportViewerContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        onShareHtml = { content, strings, badgeText ->
            viewModel.shareAsHtml(
                content.toReportLayout(
                    strings = strings,
                    dateFormats = dateFormats,
                    formatter = formatter,
                    perspectiveBadgeText = badgeText,
                )
            )
        },
        onPrint = { content, strings, badgeText ->
            viewModel.print(
                content.toReportLayout(
                    strings = strings,
                    dateFormats = dateFormats,
                    formatter = formatter,
                    perspectiveBadgeText = badgeText,
                )
            )
        },
    )
}

@Composable
private fun ReportViewerContent(
    uiState: ReportViewerUiState,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onShareHtml: (ReportViewerUiState.Content, ReportExportStrings, String) -> Unit,
    onPrint: (ReportViewerUiState.Content, ReportExportStrings, String) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val dateFormats = LocalDateFormats.current
    val exportStrings = ReportExportStrings(
        title = stringResource(Res.string.report_viewer_title),
        generatedAtPrefix = stringResource(Res.string.report_output_generated_at),
        summaryBalance = stringResource(Res.string.report_viewer_summary_balance),
        summaryInitialBalance = stringResource(Res.string.report_viewer_summary_initial_balance),
        summaryIncome = stringResource(Res.string.report_viewer_summary_income),
        summaryExpense = stringResource(Res.string.report_viewer_summary_expense),
        sectionSpendingByCategory = stringResource(Res.string.report_viewer_spending_by_category),
        sectionTransactions = stringResource(Res.string.report_viewer_transactions),
        operationTransfer = stringResource(Res.string.operation_card_transfer),
        operationPayment = stringResource(Res.string.operation_card_payment),
        operationBalanceAdjustment = stringResource(Res.string.operation_card_balance_adjustment),
        operationInvoiceAdjustment = stringResource(Res.string.operation_card_invoice_adjustment),
        columnCategory = stringResource(Res.string.report_output_column_category),
        columnTransaction = stringResource(Res.string.report_output_column_transaction),
        columnAmount = stringResource(Res.string.report_output_column_amount),
        columnPercentage = stringResource(Res.string.report_output_column_percentage),
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
                    if (uiState is ReportViewerUiState.Content) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        val badgeText = stringUiText(uiState.perspectiveBadge)

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.report_viewer_action_share_html)) },
                                    onClick = {
                                        menuExpanded = false
                                        onShareHtml(uiState, exportStrings, badgeText)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.report_viewer_action_print)) },
                                    onClick = {
                                        menuExpanded = false
                                        onPrint(uiState, exportStrings, badgeText)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
