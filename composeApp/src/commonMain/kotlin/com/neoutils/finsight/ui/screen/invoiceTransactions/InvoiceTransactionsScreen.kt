@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income as IncomeColor
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.InvoicePayment as BillPaymentColor
import com.neoutils.finsight.ui.util.stringUiText
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_transactions_advance_payment
import com.neoutils.finsight.resources.invoice_transactions_advance_payments
import com.neoutils.finsight.resources.invoice_transactions_adjustments
import com.neoutils.finsight.resources.invoice_transactions_close_invoice
import com.neoutils.finsight.resources.invoice_transactions_delete_card
import com.neoutils.finsight.resources.invoice_transactions_delete_invoice
import com.neoutils.finsight.resources.invoice_transactions_edit_card
import com.neoutils.finsight.resources.invoice_transactions_expenses
import com.neoutils.finsight.resources.invoice_transactions_filter_category
import com.neoutils.finsight.resources.invoice_transactions_filter_category_all
import com.neoutils.finsight.resources.invoice_transactions_filter_type
import com.neoutils.finsight.resources.invoice_transactions_filter_type_adjustment
import com.neoutils.finsight.resources.invoice_transactions_filter_type_all
import com.neoutils.finsight.resources.invoice_transactions_filter_type_expense
import com.neoutils.finsight.resources.invoice_transactions_filter_type_payment
import com.neoutils.finsight.resources.invoice_transactions_pay_invoice
import com.neoutils.finsight.resources.invoice_transactions_reopen_invoice
import com.neoutils.finsight.resources.invoice_transactions_total
import kotlin.math.absoluteValue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val formats = DateFormats()

@Composable
fun InvoiceTransactionsScreen(
    creditCardId: Long,
    onNavigateBack: () -> Unit = {},
    viewModel: InvoiceTransactionsViewModel = koinViewModel {
        parametersOf(creditCardId)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.invoice_transactions_delete_card)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Expense
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        modalManager.show(DeleteCreditCardModal(creditCard))
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
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
                    onEditInvoice = { invoice ->
                        modalManager.show(
                            EditInvoiceBalanceModal(
                                initialInvoice = invoice
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

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

            item(
                key = "filters_row"
            ) {
                FiltersRow(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .animateItem()
                )
            }

            uiState.operations.forEach { (date, operations) ->
                item(
                    key = "date_title_$date"
                ) {
                    Text(
                        text = formats.formatRelativeDate(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    )
                }

                items(
                    items = operations,
                    key = { it.id }
                ) { operation ->
                    OperationCard(
                        operation = operation,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (operation.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(operation))
                                }

                                else -> {
                                    modalManager.show(ViewOperationModal(operation))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoicePager(
    invoices: List<InvoiceTransactionsUiState.InvoiceSummary>,
    selectedIndex: Int,
    onSelectInvoice: (Int) -> Unit,
    onEditInvoice: (invoice: Invoice) -> Unit,
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
                        text = summary.dueMonthLabel,
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
                        text = summary.status.label,
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
                isNegative = true
            )

            SummaryRow(
                label = stringResource(Res.string.invoice_transactions_advance_payments),
                amount = summary.advancePayment,
                color = InvoicePayment,
                isPositive = true
            )

            if (summary.mustShowAdjustment) {
                SummaryRow(
                    label = stringResource(Res.string.invoice_transactions_adjustments),
                    amount = summary.adjustment,
                    color = Adjustment,
                    showSign = true
                )
            }

            HorizontalDivider()

            SummaryRow(
                label = stringResource(Res.string.invoice_transactions_total),
                amount = summary.total,
                color = colorScheme.onSurface,
                isTotal = true,
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
        if (summary.status.isOpen) {
            OutlinedButton(
                onClick = {
                    modalManager.show(
                        AdvancePaymentModal(
                            invoice = invoice,
                            currentBillAmount = summary.total,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(colorScheme.primary.copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.invoice_transactions_advance_payment),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

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
                onClick = { modalManager.show(DeleteFutureInvoiceModal(invoice)) },
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

        if (summary.status.isClosed) {
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

            Button(
                onClick = {
                    modalManager.show(
                        PayInvoiceModal(
                            invoice = invoice,
                            currentBillAmount = summary.total
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.invoice_transactions_pay_invoice),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    isNegative: Boolean = false,
    isPositive: Boolean = false,
    showSign: Boolean = false,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null,
) {
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

        val formattedAmount = when {
            isPositive -> "+${amount.absoluteValue.toMoneyFormat()}"
            isNegative -> "-${amount.absoluteValue.toMoneyFormat()}"
            showSign && amount > 0 -> "+${amount.toMoneyFormat()}"
            else -> amount.toMoneyFormat()
        }

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
                text = formattedAmount,
                fontSize = if (isTotal) 20.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
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
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(
            key = "category_filter"
        ) {
            Box {
                CategoryFilterChip(
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
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
    }
}

@Composable
private fun CategoryFilterChip(
    selectedCategory: Category?,
    categories: List<Category>,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        selectedCategory?.let { category ->
            when (category.type) {
                Category.Type.INCOME -> IncomeColor
                Category.Type.EXPENSE -> ExpenseColor
            }
        }

    FilterChip(
        selected = selectedCategory != null,
        onClick = { expanded = true },
        label = { Text(selectedCategory?.name ?: stringResource(Res.string.invoice_transactions_filter_category)) },
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
                onAction(InvoiceTransactionsAction.SelectCategory(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(InvoiceTransactionsAction.SelectCategory(category))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: Transaction.Type?,
    onAction: (InvoiceTransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        when (selectedType) {
            Transaction.Type.EXPENSE -> ExpenseColor
            Transaction.Type.ADJUSTMENT -> AdjustmentColor
            Transaction.Type.INCOME -> BillPaymentColor
            else -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.EXPENSE -> stringResource(Res.string.invoice_transactions_filter_type_expense)
                    Transaction.Type.ADJUSTMENT -> stringResource(Res.string.invoice_transactions_filter_type_adjustment)
                    Transaction.Type.INCOME -> stringResource(Res.string.invoice_transactions_filter_type_payment)
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
            Transaction.Type.EXPENSE to stringResource(Res.string.invoice_transactions_filter_type_expense),
            Transaction.Type.ADJUSTMENT to stringResource(Res.string.invoice_transactions_filter_type_adjustment),
            Transaction.Type.INCOME to stringResource(Res.string.invoice_transactions_filter_type_payment),
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
