@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.neoutils.finsight.ui.screen.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.twotone.CalendarToday
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.reports_account_balance_description
import com.neoutils.finsight.resources.reports_account_balance_title
import com.neoutils.finsight.resources.reports_available_formats
import com.neoutils.finsight.resources.reports_csv
import com.neoutils.finsight.resources.reports_end_date
import com.neoutils.finsight.resources.reports_filters_title
import com.neoutils.finsight.resources.reports_generate
import com.neoutils.finsight.resources.reports_generate_hint
import com.neoutils.finsight.resources.reports_invoice_description
import com.neoutils.finsight.resources.reports_invoice_title
import com.neoutils.finsight.resources.reports_no_accounts
import com.neoutils.finsight.resources.reports_no_credit_cards
import com.neoutils.finsight.resources.reports_no_invoices
import com.neoutils.finsight.resources.reports_pdf
import com.neoutils.finsight.resources.reports_start_date
import com.neoutils.finsight.resources.reports_subtitle
import com.neoutils.finsight.resources.reports_summary_account
import com.neoutils.finsight.resources.reports_summary_card
import com.neoutils.finsight.resources.reports_summary_format
import com.neoutils.finsight.resources.reports_summary_invoice
import com.neoutils.finsight.resources.reports_summary_period
import com.neoutils.finsight.resources.reports_summary_title
import com.neoutils.finsight.resources.reports_title
import com.neoutils.finsight.resources.reports_transactions_description
import com.neoutils.finsight.resources.reports_transactions_title
import com.neoutils.finsight.ui.component.AccountSelector
import com.neoutils.finsight.ui.component.CreditCardSelector
import com.neoutils.finsight.ui.component.InvoiceSelector
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.reportPreview.ReportPreviewModal
import com.neoutils.finsight.ui.extension.toLabel
import com.neoutils.finsight.ui.modal.DatePickerModal
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit = {},
    onGenerateReport: (ReportRequest) -> Unit = {},
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val modalManager = LocalModalManager.current
    val reportTypes = ReportType.entries
    val selectedPage = reportTypes.indexOf(uiState.reportType).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { reportTypes.size }
    )

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                reportTypes.getOrNull(page)?.let(viewModel::selectReportType)
            }
    }

    LaunchedEffect(selectedPage) {
        if (pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.reports_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "reports_intro") {
                IntroCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem()
                )
            }

            item(key = "reports_type_pager") {
                ReportTypePager(
                    reportTypes = reportTypes,
                    selectedReportType = uiState.reportType,
                    pagerState = pagerState,
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "reports_filters") {
                FiltersCard(
                    uiState = uiState,
                    onAccountSelected = viewModel::selectAccount,
                    onCreditCardSelected = viewModel::selectCreditCard,
                    onInvoiceSelected = viewModel::selectInvoice,
                    onPickStartDate = {
                        modalManager.show(
                            DatePickerModal(
                                initialDate = uiState.startDate,
                                maxDate = uiState.endDate,
                                onDateSelected = viewModel::updateStartDate,
                            )
                        )
                    },
                    onPickEndDate = {
                        modalManager.show(
                            DatePickerModal(
                                initialDate = uiState.endDate,
                                minDate = uiState.startDate,
                                onDateSelected = viewModel::updateEndDate,
                            )
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }

            item(key = "reports_summary") {
                SummaryCard(
                    uiState = uiState,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }

            item(key = "reports_action") {
                Button(
                    onClick = {
                        viewModel.generateReport { preview ->
                            uiState.reportRequest?.let(onGenerateReport)
                            modalManager.show(ReportPreviewModal(preview))
                        }
                    },
                    enabled = uiState.canGenerate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .animateItem()
                ) {
                    Text(text = stringResource(Res.string.reports_generate))
                }
            }

            item(key = "reports_hint") {
                Text(
                    text = stringResource(Res.string.reports_generate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun IntroCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.reports_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReportTypeCard(
    reportType: ReportType,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSelected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = colors,
        modifier = modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = reportType.accent().copy(alpha = 0.16f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = reportType.icon(),
                        contentDescription = null,
                        tint = reportType.accent(),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(reportType.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(reportType.descriptionRes()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(text = stringResource(reportType.formatRes()))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReportTypePager(
    reportTypes: List<ReportType>,
    selectedReportType: ReportType,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
        ) { page ->
            val reportType = reportTypes[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            ReportTypeCard(
                reportType = reportType,
                isSelected = reportType == selectedReportType,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = 1f - (pageOffset.coerceIn(0f, 1f) * 0.06f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (pageOffset.coerceIn(0f, 1f) * 0.18f)
                    },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = 6.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reportTypes.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .background(
                            color = if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            },
                            shape = CircleShape,
                        )
                )
            }
        }
    }
}

@Composable
private fun FiltersCard(
    uiState: ReportsUiState,
    onAccountSelected: (com.neoutils.finsight.domain.model.Account?) -> Unit,
    onCreditCardSelected: (com.neoutils.finsight.domain.model.CreditCard) -> Unit,
    onInvoiceSelected: (com.neoutils.finsight.domain.model.Invoice) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.reports_filters_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            AnimatedContent(
                targetState = uiState.reportType,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "report-filters",
            ) { reportType ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (reportType) {
                        ReportType.ACCOUNT_BALANCE -> {
                            if (uiState.accounts.isEmpty()) {
                                EmptyFilterState(
                                    text = stringResource(Res.string.reports_no_accounts),
                                )
                            } else {
                                AccountSelector(
                                    selectedAccount = uiState.selectedAccount,
                                    accounts = uiState.accounts,
                                    onAccountSelected = onAccountSelected,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            PeriodFields(
                                startDate = uiState.startDate,
                                endDate = uiState.endDate,
                                onPickStartDate = onPickStartDate,
                                onPickEndDate = onPickEndDate,
                            )
                        }

                        ReportType.INVOICE -> {
                            if (uiState.creditCards.isEmpty()) {
                                EmptyFilterState(
                                    text = stringResource(Res.string.reports_no_credit_cards),
                                )
                            } else {
                                CreditCardSelector(
                                    creditCards = uiState.creditCards,
                                    creditCard = uiState.selectedCreditCard,
                                    onCreditCardSelected = onCreditCardSelected,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (uiState.invoices.isEmpty()) {
                                EmptyFilterState(
                                    text = stringResource(Res.string.reports_no_invoices),
                                )
                            } else {
                                InvoiceSelector(
                                    invoices = uiState.invoices,
                                    invoice = uiState.selectedInvoice,
                                    onInvoiceSelected = onInvoiceSelected,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        ReportType.TRANSACTIONS -> {
                            PeriodFields(
                                startDate = uiState.startDate,
                                endDate = uiState.endDate,
                                onPickStartDate = onPickStartDate,
                                onPickEndDate = onPickEndDate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodFields(
    startDate: LocalDate,
    endDate: LocalDate,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
) {
    DateField(
        value = startDate,
        label = stringResource(Res.string.reports_start_date),
        onClick = onPickStartDate,
    )

    DateField(
        value = endDate,
        label = stringResource(Res.string.reports_end_date),
        onClick = onPickEndDate,
    )
}

@Composable
private fun DateField(
    value: LocalDate,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedTextField(
        value = dayMonthYear.format(value),
        onValueChange = {},
        readOnly = true,
        label = {
            Text(text = label)
        },
        trailingIcon = {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.TwoTone.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryCard(
    uiState: ReportsUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.reports_summary_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            AnimatedContent(
                targetState = uiState.reportRequest,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "report-summary",
            ) { request ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (request) {
                        is ReportRequest.AccountBalance -> {
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_account),
                                value = request.account.name,
                            )
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_period),
                                value = "${dayMonthYear.format(request.startDate)} - ${dayMonthYear.format(request.endDate)}",
                            )
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_format),
                                value = stringResource(request.format.labelRes()),
                            )
                        }

                        is ReportRequest.InvoiceStatement -> {
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_card),
                                value = request.creditCard.name,
                            )
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_invoice),
                                value = request.invoice.toLabel(),
                            )
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_format),
                                value = stringResource(request.format.labelRes()),
                            )
                        }

                        is ReportRequest.TransactionsByPeriod -> {
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_period),
                                value = "${dayMonthYear.format(request.startDate)} - ${dayMonthYear.format(request.endDate)}",
                            )
                            SummaryLine(
                                label = stringResource(Res.string.reports_summary_format),
                                value = stringResource(request.format.labelRes()),
                            )
                        }

                        null -> {
                            Text(
                                text = stringResource(Res.string.reports_generate_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.reports_available_formats),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(text = stringResource(uiState.reportType.formatRes()))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyFilterState(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ReportType.titleRes(): StringResource = when (this) {
    ReportType.ACCOUNT_BALANCE -> Res.string.reports_account_balance_title
    ReportType.INVOICE -> Res.string.reports_invoice_title
    ReportType.TRANSACTIONS -> Res.string.reports_transactions_title
}

private fun ReportType.descriptionRes(): StringResource = when (this) {
    ReportType.ACCOUNT_BALANCE -> Res.string.reports_account_balance_description
    ReportType.INVOICE -> Res.string.reports_invoice_description
    ReportType.TRANSACTIONS -> Res.string.reports_transactions_description
}

private fun ReportType.formatRes(): StringResource = when (this) {
    ReportType.ACCOUNT_BALANCE -> Res.string.reports_pdf
    ReportType.INVOICE -> Res.string.reports_pdf
    ReportType.TRANSACTIONS -> Res.string.reports_csv
}

private fun ReportType.icon(): ImageVector = when (this) {
    ReportType.ACCOUNT_BALANCE -> Icons.Default.AccountBalanceWallet
    ReportType.INVOICE -> Icons.AutoMirrored.Filled.ReceiptLong
    ReportType.TRANSACTIONS -> Icons.Default.Description
}

private fun ReportType.accent(): Color = when (this) {
    ReportType.ACCOUNT_BALANCE -> Color(0xFF2E7D32)
    ReportType.INVOICE -> Color(0xFFC62828)
    ReportType.TRANSACTIONS -> Color(0xFF1565C0)
}

private fun ReportFormat.labelRes(): StringResource = when (this) {
    ReportFormat.PDF -> Res.string.reports_pdf
    ReportFormat.CSV -> Res.string.reports_csv
}
