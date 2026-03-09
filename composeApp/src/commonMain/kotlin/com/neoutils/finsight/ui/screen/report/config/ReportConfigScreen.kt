@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountCard
import com.neoutils.finsight.ui.component.AccountCardVariant
import com.neoutils.finsight.ui.component.CreditCardCard
import com.neoutils.finsight.ui.component.CreditCardCardVariant
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.coroutines.flow.collectLatest
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
    val colorScheme = MaterialTheme.colorScheme

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
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = uiState.selectedTab == PerspectiveTab.ACCOUNT,
                        onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.ACCOUNT)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {},
                    ) {
                        Text(stringResource(Res.string.report_config_perspective_account))
                    }
                    SegmentedButton(
                        selected = uiState.selectedTab == PerspectiveTab.CREDIT_CARD,
                        onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.CREDIT_CARD)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {},
                    ) {
                        Text(stringResource(Res.string.report_config_perspective_credit_card))
                    }
                }
            }

            if (uiState.selectedTab == PerspectiveTab.ACCOUNT) {
                item {
                    Text(
                        text = stringResource(Res.string.report_config_accounts),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (uiState.accounts.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.report_config_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(uiState.accounts, key = { it.id }) { account ->
                                AccountCard(
                                    account = account,
                                    variant = AccountCardVariant.Selection(
                                        selected = account.id in uiState.selectedAccountIds,
                                        onClick = { onAction(ReportConfigAction.ToggleAccount(account.id)) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.selectedTab == PerspectiveTab.CREDIT_CARD) {
                item {
                    Text(
                        text = stringResource(Res.string.report_config_credit_cards),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (uiState.creditCards.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.report_config_no_credit_cards),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    item {
                        val initialPage = uiState.creditCards
                            .indexOfFirst { it.id == uiState.selectedCreditCardId }
                            .coerceAtLeast(0)

                        val pagerState = rememberPagerState(
                            initialPage = initialPage,
                            pageCount = { uiState.creditCards.size },
                        )

                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.currentPage }.collectLatest { page ->
                                onAction(ReportConfigAction.SelectCreditCard(uiState.creditCards[page].id))
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 8.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) { page ->
                            CreditCardCard(
                                creditCard = uiState.creditCards[page],
                                variant = CreditCardCardVariant.Selection,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(Res.string.report_config_date_range),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                DateRangeCard(
                    startDate = uiState.startDate,
                    endDate = uiState.endDate,
                    onSelectStartDate = { onAction(ReportConfigAction.SelectStartDate(it)) },
                    onSelectEndDate = { onAction(ReportConfigAction.SelectEndDate(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.report_config_sections),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                SectionsCard(
                    includeSpendingByCategory = uiState.includeSpendingByCategory,
                    includeTransactionList = uiState.includeTransactionList,
                    onToggleSpendingByCategory = { onAction(ReportConfigAction.ToggleSpendingByCategory(it)) },
                    onToggleTransactionList = { onAction(ReportConfigAction.ToggleTransactionList(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
