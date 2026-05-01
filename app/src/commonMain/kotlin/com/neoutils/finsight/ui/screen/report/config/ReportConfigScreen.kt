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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.PerspectiveTab
import com.neoutils.finsight.extension.toUiText
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountCard
import com.neoutils.finsight.ui.component.AccountCardVariant
import com.neoutils.finsight.ui.component.CreditCardCard
import com.neoutils.finsight.ui.component.CreditCardCardVariant
import com.neoutils.finsight.ui.screen.report.ReportViewerParams
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToViewer: (ReportViewerParams) -> Unit = {},
    viewModel: ReportConfigViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analytics.logScreenView("reports_config")
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportConfigEvent.NavigateToViewer -> onNavigateToViewer(event.params)
            }
        }
    }

    ReportConfigContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onGenerate = {
            viewModel.onAction(ReportConfigAction.GenerateReport)
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

    val segmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primary.copy(alpha = 0.2f),
        activeContentColor = colorScheme.primary,
        activeBorderColor = colorScheme.primary,
        inactiveContainerColor = colorScheme.surfaceContainer,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.outline,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.report_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = onGenerate,
                enabled = uiState is ReportConfigUiState.Content && uiState.config.isValid,
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
            if (uiState !is ReportConfigUiState.Content) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                return@LazyColumn
            }

            if (uiState.creditCards.isNotEmpty()) {
                item(
                    key = "perspective_tabs",
                    contentType = "segmented_buttons",
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    ) {
                        SegmentedButton(
                            selected = uiState.config.selectedTab == PerspectiveTab.ACCOUNT,
                            onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.ACCOUNT)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = segmentedButtonColors,
                            icon = {},
                        ) {
                            Text(stringResource(Res.string.report_config_perspective_account))
                        }
                        SegmentedButton(
                            selected = uiState.config.selectedTab == PerspectiveTab.CREDIT_CARD,
                            onClick = { onAction(ReportConfigAction.SelectPerspective(PerspectiveTab.CREDIT_CARD)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = segmentedButtonColors,
                            icon = {},
                        ) {
                            Text(stringResource(Res.string.report_config_perspective_credit_card))
                        }
                    }
                }
            }

            if (uiState.config.selectedTab == PerspectiveTab.ACCOUNT) {
                item(
                    key = "accounts_title",
                    contentType = "section_title",
                ) {
                    Text(
                        text = stringResource(Res.string.report_config_accounts),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }

                if (uiState.accounts.isNotEmpty()) {
                    item(
                        key = "accounts_list",
                        contentType = "accounts_row",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        ) {
                            items(
                                items = uiState.accounts,
                                key = { it.id },
                                contentType = { "account_card" },
                            ) { account ->
                                AccountCard(
                                    account = account,
                                    variant = AccountCardVariant.Selection(
                                        selected = account.id in uiState.config.selectedAccountIds,
                                        onClick = { onAction(ReportConfigAction.ToggleAccount(account.id)) },
                                    ),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.config.selectedTab == PerspectiveTab.CREDIT_CARD) {
                item(
                    key = "credit_cards_title",
                    contentType = "section_title",
                ) {
                    Text(
                        text = stringResource(Res.string.report_config_credit_cards),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }
                item(
                    key = "credit_cards_pager",
                    contentType = "credit_cards_pager",
                ) {
                    val initialPage = uiState.creditCards
                        .indexOfFirst { it.id == uiState.config.selectedCreditCardId }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    ) { page ->
                        CreditCardCard(
                            creditCard = uiState.creditCards[page],
                            variant = CreditCardCardVariant.Selection,
                        )
                    }
                }
            }

            if (uiState.config.selectedTab == PerspectiveTab.ACCOUNT) {
                item(
                    key = "date_range_title",
                    contentType = "section_title",
                ) {
                    Text(
                        text = stringResource(Res.string.report_config_date_range),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }

                item(
                    key = "date_range_card",
                    contentType = "date_range_card",
                ) {
                    DateRangeCard(
                        startDate = uiState.config.startDate,
                        endDate = uiState.config.endDate,
                        onRangeSelected = { start, end ->
                            onAction(ReportConfigAction.SelectStartDate(start))
                            onAction(ReportConfigAction.SelectEndDate(end))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (uiState.config.selectedTab == PerspectiveTab.CREDIT_CARD) {
                item(
                    key = "invoice_title",
                    contentType = "section_title",
                ) {
                    Text(
                        text = stringResource(Res.string.report_config_invoice),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }
                item(
                    key = "invoice_list",
                    contentType = "invoice_row",
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    ) {
                        items(
                            items = uiState.invoices,
                            key = { it.id },
                            contentType = { "invoice_card" },
                        ) { invoice ->
                            InvoiceSelectionCard(
                                invoice = invoice,
                                selected = invoice.id in uiState.config.selectedInvoiceIds,
                                onClick = { onAction(ReportConfigAction.ToggleInvoice(invoice.id)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            item(
                key = "sections_title",
                contentType = "section_title",
            ) {
                Text(
                    text = stringResource(Res.string.report_config_sections),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }

            item(
                key = "sections_card",
                contentType = "sections_card",
            ) {
                SectionsCard(
                    includeSpendingByCategory = uiState.config.includeSpendingByCategory,
                    includeIncomeByCategory = uiState.config.includeIncomeByCategory,
                    includeTransactionList = uiState.config.includeTransactionList,
                    onToggleSpendingByCategory = { onAction(ReportConfigAction.ToggleSpendingByCategory(it)) },
                    onToggleIncomeByCategory = { onAction(ReportConfigAction.ToggleIncomeByCategory(it)) },
                    onToggleTransactionList = { onAction(ReportConfigAction.ToggleTransactionList(it)) },
                    showIncomeByCategory = uiState.config.selectedTab != PerspectiveTab.CREDIT_CARD,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun InvoiceSelectionCard(
    invoice: Invoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dateFormats = LocalDateFormats.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "invoice_border",
    )

    Card(
        modifier = modifier
            .width(156.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = Color(invoice.status.colorValue),
                    modifier = Modifier.size(20.dp),
                )
                Surface(
                    color = Color(invoice.status.colorValue).copy(alpha = 0.15f),
                    contentColor = Color(invoice.status.colorValue),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = stringResource(invoice.status.toUiText()),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = dateFormats.yearMonth.format(invoice.dueMonth),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
