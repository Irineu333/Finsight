@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToViewer: (AppRoute.ReportViewer) -> Unit = {},
    viewModel: ReportConfigViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReportConfigContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onGenerate = {
            viewModel.buildViewerRoute(uiState)?.let(onNavigateToViewer)
        },
    )
}

@OptIn(FormatStringsInDatetimeFormats::class)
@Composable
private fun ReportConfigContent(
    uiState: ReportConfigUiState,
    onAction: (ReportConfigAction) -> Unit,
    onNavigateBack: () -> Unit,
    onGenerate: () -> Unit,
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {},
            dismissButton = {},
        ) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = uiState.startDate?.toEpochDays()?.times(86400000L),
            )
            DatePicker(state = state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(Res.string.report_config_cancel))
                }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val days = (millis / 86400000L).toInt()
                            onAction(ReportConfigAction.SelectStartDate(LocalDate.fromEpochDays(days)))
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.report_config_confirm))
                }
            }
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {},
            dismissButton = {},
        ) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = uiState.endDate?.toEpochDays()?.times(86400000L),
            )
            DatePicker(state = state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(Res.string.report_config_cancel))
                }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val days = (millis / 86400000L).toInt()
                            onAction(ReportConfigAction.SelectEndDate(LocalDate.fromEpochDays(days)))
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.report_config_confirm))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.report_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onGenerate,
                enabled = uiState.isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) {
                Text(stringResource(Res.string.report_config_generate))
            }
        },
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                PrimaryTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = uiState.selectedTab == PerspectiveTab.ACCOUNT,
                        onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.ACCOUNT)) },
                        text = { Text(stringResource(Res.string.report_config_perspective_account)) },
                    )
                    Tab(
                        selected = uiState.selectedTab == PerspectiveTab.CREDIT_CARD,
                        onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.CREDIT_CARD)) },
                        text = { Text(stringResource(Res.string.report_config_perspective_credit_card)) },
                    )
                }
            }

            if (uiState.selectedTab == PerspectiveTab.ACCOUNT) {
                items(uiState.accounts, key = { it.id }) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        leadingContent = {
                            Checkbox(
                                checked = account.id in uiState.selectedAccountIds,
                                onCheckedChange = { onAction(ReportConfigAction.ToggleAccount(account.id)) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(uiState.creditCards, key = { it.id }) { card ->
                    ListItem(
                        headlineContent = { Text(card.name) },
                        leadingContent = {
                            RadioButton(
                                selected = card.id == uiState.selectedCreditCardId,
                                onClick = { onAction(ReportConfigAction.SelectCreditCard(card.id)) },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Text(
                    text = stringResource(Res.string.report_config_date_range),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(uiState.startDate?.toString() ?: stringResource(Res.string.report_config_start_date))
                    }
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(uiState.endDate?.toString() ?: stringResource(Res.string.report_config_end_date))
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.report_config_include_spending_by_category)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.includeSpendingByCategory,
                            onCheckedChange = { onAction(ReportConfigAction.ToggleSpendingByCategory(it)) },
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.report_config_include_transactions)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.includeTransactionList,
                            onCheckedChange = { onAction(ReportConfigAction.ToggleTransactionList(it)) },
                        )
                    },
                )
            }
        }
    }
}
