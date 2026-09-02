@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import com.neoutils.finsight.feature.shell.api.ChromeEffect
import com.neoutils.finsight.ui.extension.color
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.neoutils.finsight.ui.util.exposeTestTags
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.ui.component.EmptyStateMessage
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.extension.toUiText
import com.neoutils.finsight.ui.component.TransactionCard
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.modal.archiveCreditCard.ArchiveCreditCardModal
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finsight.ui.modal.createInvoice.CreateInvoiceModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.invoicePayment.InvoicePaymentModal
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income as IncomeColor
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.InvoicePayment as BillPaymentColor
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_spending_uncategorized
import com.neoutils.finsight.resources.credit_cards_unarchive
import com.neoutils.finsight.resources.invoice_transactions_advance_payments
import com.neoutils.finsight.resources.invoice_transactions_adjustments
import com.neoutils.finsight.resources.invoice_transactions_close_invoice
import com.neoutils.finsight.resources.invoice_transactions_create_invoice
import com.neoutils.finsight.resources.invoice_transactions_delete_invoice
import com.neoutils.finsight.resources.invoice_transactions_edit_card
import com.neoutils.finsight.resources.invoice_transactions_empty_body
import com.neoutils.finsight.resources.invoice_transactions_empty_filter_body
import com.neoutils.finsight.resources.invoice_transactions_empty_filter_title
import com.neoutils.finsight.resources.invoice_transactions_empty_title
import com.neoutils.finsight.resources.invoice_transactions_expenses
import com.neoutils.finsight.resources.invoice_transactions_filter_category
import com.neoutils.finsight.resources.invoice_transactions_filter_category_all
import com.neoutils.finsight.resources.invoice_transactions_filter_type
import com.neoutils.finsight.resources.invoice_transactions_filter_type_adjustment
import com.neoutils.finsight.resources.invoice_transactions_filter_type_all
import com.neoutils.finsight.resources.invoice_transactions_filter_type_expense
import com.neoutils.finsight.resources.invoice_transactions_filter_type_payment
import com.neoutils.finsight.resources.invoice_transactions_reopen_invoice
import com.neoutils.finsight.resources.invoice_transactions_total
import com.neoutils.finsight.resources.transactions_empty_filter_action
import com.neoutils.finsight.resources.transactions_filter_installment
import com.neoutils.finsight.resources.transactions_filter_recurring
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InvoiceTransactionsScreen(
    creditCardId: Long,
    invoiceId: Long? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: InvoiceTransactionsViewModel = koinViewModel {
        parametersOf(creditCardId, invoiceId)
    },
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analytics.logScreenView("invoice_transactions")
    }

    // Charging the card in view is what the shell's universal action does, so this screen takes the
    // default chrome — and says so, because silence is what the shell holds a previous screen's
    // chrome through.
    ChromeEffect()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                InvoiceTransactionsEvent.CreditCardDeleted -> onNavigateBack()
            }
        }
    }

    InvoiceTransactionsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun InvoiceTransactionsContent(
    uiState: InvoiceTransactionsUiState,
    onAction: (InvoiceTransactionsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val modalManager = LocalModalManager.current
    val detailController = LocalDetailPaneController.current
    val transactionsEntry = koinInject<TransactionsEntry>()
    val dateFormats = LocalDateFormats.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = uiState.creditCardName)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                    actionIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    val creditCard = uiState.invoices.firstOrNull()?.invoice?.creditCard
                    if (creditCard != null) {
                        var menuExpanded by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("invoice_transactions_more_options"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                // A popup is its own composition root: without this, no
                                // tag on the options below reaches the E2E driver.
                                modifier = Modifier.exposeTestTags(),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.invoice_transactions_edit_card)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        modalManager.show(CreditCardFormModal(creditCard))
                                    }
                                )
                                // A write like the others, so an archived card is not
                                // offered it. The month it opens on is where the user
                                // already is in the calendar.
                                if (!uiState.isArchived) {
                                    val initialDueMonth = uiState.invoices
                                        .getOrNull(uiState.selectedInvoiceIndex)
                                        ?.dueMonth

                                    if (initialDueMonth != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.invoice_transactions_create_invoice)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                )
                                            },
                                            modifier = Modifier.testTag("invoice_transactions_create_invoice"),
                                            onClick = {
                                                menuExpanded = false
                                                modalManager.show(
                                                    CreateInvoiceModal(
                                                        creditCard = creditCard,
                                                        initialDueMonth = initialDueMonth,
                                                        // Nothing else follows: the screen
                                                        // goes to the new invoice, and
                                                        // declaring its value stays the
                                                        // user's next gesture (design D6).
                                                        onCreated = { invoice ->
                                                            onAction(
                                                                InvoiceTransactionsAction
                                                                    .SelectInvoiceForDueMonth(invoice.dueMonth)
                                                            )
                                                        }
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                                // An archived card is already retired: offering to archive
                                // it again is a dead end, so the entry becomes unarchive —
                                // reversible and innocuous, no confirmation (design D8).
                                if (uiState.isArchived) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.credit_cards_unarchive)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Unarchive,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAction(InvoiceTransactionsAction.Unarchive)
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(uiState.retireAction.label)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = uiState.retireAction.icon,
                                                contentDescription = null,
                                                tint = Expense
                                            )
                                        },
                                        // This entry used to open the delete modal whatever
                                        // the card was, which the strict guard now refuses.
                                        onClick = {
                                            menuExpanded = false
                                            modalManager.show(
                                                when (uiState.retireAction) {
                                                    RetireAction.DELETE -> DeleteCreditCardModal(creditCard)
                                                    RetireAction.ARCHIVE -> ArchiveCreditCardModal(creditCard)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        modifier = Modifier.testTag("screen_invoice_transactions"),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "invoice_pager"
            ) {
                InvoicePager(
                    invoices = uiState.invoices,
                    selectedIndex = uiState.selectedInvoiceIndex,
                    onSelectInvoice = { index ->
                        onAction(InvoiceTransactionsAction.SelectInvoice(index))
                    },
                    // No balance adjustment on an archived card: it would write to the
                    // ledger and the writer refuses it anyway.
                    onEditInvoice = if (uiState.isArchived) null else { invoice ->
                        modalManager.show(
                            EditInvoiceBalanceModal(
                                initialInvoice = invoice
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // An archived card takes no write action; the screen stays read-only history.
            if (!uiState.isArchived) {
                uiState.invoices.getOrNull(uiState.selectedInvoiceIndex)?.let { selectedInvoice ->
                    item(
                        key = "invoice_actions"
                    ) {
                        InvoiceActions(
                            summary = selectedInvoice,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .animateContentSize()
                        )
                    }
                }
            }

            item(
                key = "filters_row"
            ) {
                FiltersRow(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }

            when (val listState = uiState.listState) {
                // Nothing has been read yet: the screen says nothing rather than
                // claiming an emptiness it cannot yet know about.
                InvoiceTransactionsUiState.ListState.Loading -> Unit

                InvoiceTransactionsUiState.ListState.EmptyInvoice,
                is InvoiceTransactionsUiState.ListState.EmptyScope -> item(
                    key = "empty_state"
                ) {
                    // Centred inside a column narrower than the screen: on a desktop or a
                    // tablet, text running the full width would read as a paragraph
                    // rather than as a short notice.
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .animateItem(),
                        contentAlignment = Alignment.Center,
                    ) {
                        InvoiceTransactionsEmptyState(
                            listState = listState,
                            onAction = onAction,
                            modifier = Modifier
                                .widthIn(max = 400.dp)
                                .padding(
                                    horizontal = 24.dp,
                                    vertical = 48.dp
                                )
                        )
                    }
                }

                is InvoiceTransactionsUiState.ListState.Content -> {
                    listState.transactions.forEach { (date, transactions) ->
                        item(
                            key = "date_title_$date"
                        ) {
                            Text(
                                text = dateFormats.formatRelativeDate(date),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }

                        items(
                            items = transactions,
                            key = { it.id }
                        ) { transactionUi ->
                            TransactionCard(
                                transaction = transactionUi,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .animateItem(),
                                onClick = {
                                    detailController.show(transactionsEntry.viewTransactionModal(transactionUi.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What stands where the list would be. An invoice with nothing on it cannot be revealed by
 * any filter, and this screen offers no command to record a transaction, so that text only
 * states the fact. A cut with nothing in it can be loosened, and only then is clearing
 * worth offering.
 */
@Composable
private fun InvoiceTransactionsEmptyState(
    listState: InvoiceTransactionsUiState.ListState,
    onAction: (InvoiceTransactionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInvoiceEmpty = listState is InvoiceTransactionsUiState.ListState.EmptyInvoice

    EmptyStateMessage(
        icon = if (isInvoiceEmpty) {
            Icons.Outlined.CreditCard
        } else {
            Icons.Outlined.FilterAltOff
        },
        title = stringResource(
            if (isInvoiceEmpty) {
                Res.string.invoice_transactions_empty_title
            } else {
                Res.string.invoice_transactions_empty_filter_title
            }
        ),
        description = stringResource(
            if (isInvoiceEmpty) {
                Res.string.invoice_transactions_empty_body
            } else {
                Res.string.invoice_transactions_empty_filter_body
            }
        ),
        modifier = modifier,
        action = if (listState is InvoiceTransactionsUiState.ListState.EmptyScope && listState.canClearFilters) {
            {
                Button(
                    onClick = { onAction(InvoiceTransactionsAction.ClearFilters) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(Res.string.transactions_empty_filter_action))
                }
            }
        } else null,
    )
}

@Composable
private fun InvoicePager(
    invoices: List<InvoiceTransactionsUiState.InvoiceSummary>,
    selectedIndex: Int,
    onSelectInvoice: (Int) -> Unit,
    onEditInvoice: ((invoice: Invoice) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { invoices.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedIndex) {
            onSelectInvoice(pagerState.currentPage)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = true,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
        ) { page ->
            InvoiceSummaryItem(
                summary = invoices[page],
                modifier = Modifier.fillMaxWidth(),
                onEditClick = onEditInvoice
            )
        }
    }
}

@Composable
private fun InvoiceSummaryItem(
    summary: InvoiceTransactionsUiState.InvoiceSummary,
    modifier: Modifier = Modifier,
    onEditClick: ((invoice: Invoice) -> Unit)? = null
) {
    val formats = LocalDateFormats.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formats.yearMonth.format(summary.dueMonth),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    summary.nextDateLabel?.let { label ->
                        Text(
                            text = stringUiText(label),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = summary.status.color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(summary.status.toUiText()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = summary.status.color,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            SummaryRow(
                label = stringResource(Res.string.invoice_transactions_expenses),
                amount = summary.expense,
                color = Expense,
                amountTestTag = "invoice_expenses_amount",
            )

            SummaryRow(
                label = stringResource(Res.string.invoice_transactions_advance_payments),
                amount = summary.advancePayment,
                color = InvoicePayment,
                amountTestTag = "invoice_advance_payments_amount",
            )

            if (summary.mustShowAdjustment) {
                SummaryRow(
                    label = stringResource(Res.string.invoice_transactions_adjustments),
                    amount = summary.adjustment,
                    color = Adjustment
                )
            }

            HorizontalDivider()

            SummaryRow(
                label = stringResource(Res.string.invoice_transactions_total),
                amount = summary.total,
                color = colorScheme.onSurface,
                isTotal = true,
                amountTestTag = "invoice_total_amount",
                onEditClick = if (summary.canEdit && onEditClick != null) {
                    {
                        onEditClick(summary.invoice)
                    }
                } else null
            )
        }
    }
}

@Composable
private fun InvoiceActions(
    summary: InvoiceTransactionsUiState.InvoiceSummary,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val invoice = summary.invoice

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (summary.isClosable) {
            OutlinedButton(
                onClick = { modalManager.show(CloseInvoiceModal(summary.invoiceId, summary.closingDate)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFA726)
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFA726).copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.invoice_transactions_close_invoice),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (summary.status.isDeletable) {
            OutlinedButton(
                onClick = {
                    modalManager.show(
                        DeleteFutureInvoiceModal(
                            invoice = invoice,
                            // Counted where the invoice's own transactions are already
                            // known, and handed over: the sheet states what the deletion
                            // takes, and the count and the invoice are one answer.
                            transactionsToRemove = summary.transactionCount,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.error
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(colorScheme.error.copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.invoice_transactions_delete_invoice),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (summary.status.isClosed && summary.canReopen) {
            OutlinedButton(
                onClick = { modalManager.show(ReopenInvoiceModal(invoice.id)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFA726)
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFA726).copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.invoice_transactions_reopen_invoice),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // One command, and the invoice on screen is only what it opens on: the verb
        // comes from the state, resolved by the view model.
        if (summary.canPay) {
            val openPayment = { modalManager.show(InvoicePaymentModal(summary.invoiceId)) }

            val payModifier = Modifier
                .fillMaxWidth()
                .testTag("invoice_pay_invoice")

            val payContent: @Composable RowScope.() -> Unit = {
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(summary.payLabel),
                    fontSize = 14.sp,
                    fontWeight = if (summary.paySettles) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Solid emphasis is the screen's recommendation, and only settling the
            // invoice earns it: paying part of one is something the user may do, not
            // something the screen is asking for.
            if (summary.paySettles) {
                Button(
                    onClick = openPayment,
                    modifier = payModifier,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp),
                    content = payContent,
                )
            } else {
                OutlinedButton(
                    onClick = openPayment,
                    modifier = payModifier,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.primary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            colorScheme.primary.copy(alpha = 0.5f)
                        )
                    ),
                    contentPadding = PaddingValues(12.dp),
                    content = payContent,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: DisplayAmount,
    color: Color,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    // On the amount, not on the row: an assertion binds a figure to the node that renders it.
    amountTestTag: String? = null,
) {
    val formatter = LocalCurrencyFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 18.sp else 16.sp,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTotal) colorScheme.onSurface else colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (onEditClick != null) {
                        Modifier.clickable { onEditClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (onEditClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ModeEdit,
                    contentDescription = stringResource(Res.string.invoice_transactions_total),
                    tint = color.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = formatter.format(amount),
                fontSize = if (isTotal) 20.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = amountTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

@Composable
private fun FiltersRow(
    uiState: InvoiceTransactionsUiState,
    onAction: (InvoiceTransactionsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(
            key = "category_filter"
        ) {
            Box {
                CategoryFilterChip(
                    selectedSubject = uiState.selectedSubject,
                    categories = uiState.categories,
                    offersUncategorized = uiState.mustShowUncategorizedFilter,
                    onAction = onAction
                )
            }
        }

        item(
            key = "type_filter"
        ) {
            Box {
                TypeFilterChip(
                    selectedType = uiState.selectedType,
                    onAction = onAction
                )
            }
        }

        item(
            key = "recurring_filter"
        ) {
            Box {
                RecurringFilterChip(
                    enabled = uiState.showRecurringOnly,
                    onAction = onAction
                )
            }
        }

        item(
            key = "installment_filter"
        ) {
            Box {
                InstallmentFilterChip(
                    enabled = uiState.showInstallmentOnly,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    selectedSubject: SpendingSubject?,
    categories: List<Category>,
    offersUncategorized: Boolean,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Only a category has a declared nature; the unclassified value borrows none.
    val chipColor = when (selectedSubject) {
        is SpendingSubject.Categorized -> when (selectedSubject.category.type) {
            Category.Type.INCOME -> IncomeColor
            Category.Type.EXPENSE -> ExpenseColor
        }

        SpendingSubject.Uncategorized, null -> null
    }

    FilterChip(
        selected = selectedSubject != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedSubject) {
                    is SpendingSubject.Categorized -> selectedSubject.category.name
                    // The same key the breakdown names this value with: one concept, one word.
                    SpendingSubject.Uncategorized ->
                        stringResource(Res.string.category_spending_uncategorized)

                    null -> stringResource(Res.string.invoice_transactions_filter_category)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.invoice_transactions_filter_category_all)) },
            onClick = {
                onAction(InvoiceTransactionsAction.SelectSubject(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(InvoiceTransactionsAction.SelectSubject(SpendingSubject.Categorized(category)))
                    expanded = false
                }
            )
        }

        // Last, and only when the cut has something to find: a value that could answer
        // nothing but an empty list is not an offer.
        if (offersUncategorized) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.category_spending_uncategorized)) },
                onClick = {
                    onAction(InvoiceTransactionsAction.SelectSubject(SpendingSubject.Uncategorized))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: TransactionType?,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        when (selectedType) {
            TransactionType.EXPENSE -> ExpenseColor
            TransactionType.ADJUSTMENT -> AdjustmentColor
            TransactionType.INCOME -> BillPaymentColor
            else -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    TransactionType.EXPENSE -> stringResource(Res.string.invoice_transactions_filter_type_expense)
                    TransactionType.ADJUSTMENT -> stringResource(Res.string.invoice_transactions_filter_type_adjustment)
                    TransactionType.INCOME -> stringResource(Res.string.invoice_transactions_filter_type_payment)
                    else -> stringResource(Res.string.invoice_transactions_filter_type)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.invoice_transactions_filter_type_all)) },
            onClick = {
                onAction(InvoiceTransactionsAction.SelectType(null))
                expanded = false
            }
        )

        listOf(
            TransactionType.EXPENSE to stringResource(Res.string.invoice_transactions_filter_type_expense),
            TransactionType.ADJUSTMENT to stringResource(Res.string.invoice_transactions_filter_type_adjustment),
            TransactionType.INCOME to stringResource(Res.string.invoice_transactions_filter_type_payment),
        ).forEach { (type, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onAction(InvoiceTransactionsAction.SelectType(type))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun RecurringFilterChip(
    enabled: Boolean,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(InvoiceTransactionsAction.ToggleRecurring(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_recurring))
        },
    )
}

@Composable
private fun InstallmentFilterChip(
    enabled: Boolean,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(InvoiceTransactionsAction.ToggleInstallment(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_installment))
        },
    )
}
