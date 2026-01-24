@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.neoutils.finance.ui.screen.invoiceTransactions

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
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finance.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceModal
import com.neoutils.finance.ui.theme.Adjustment
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Income as IncomeColor
import com.neoutils.finance.ui.theme.Expense as ExpenseColor
import com.neoutils.finance.ui.theme.InvoicePayment
import com.neoutils.finance.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finance.ui.theme.InvoicePayment as BillPaymentColor
import com.neoutils.finance.ui.util.stringUiText
import com.neoutils.finance.util.DateFormats
import kotlin.math.absoluteValue
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
                                    text = { Text("Editar Cartão") },
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
                                    text = { Text("Excluir Cartão") },
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
                    onEditInvoice = { invoiceId, amount ->
                        modalManager.show(
                            EditBalanceModal(
                                type = EditBalanceModal.Type.CREDIT_CARD,
                                currentBalance = amount,
                                invoiceId = invoiceId
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

            uiState.transactions.forEach { (date, transactions) ->
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
                    items = transactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        category = transaction.category,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (transaction.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(transaction))
                                }

                                else -> {
                                    modalManager.show(ViewTransactionModal(transaction))
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
    onEditInvoice: (Long, Double) -> Unit,
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
                onEditClick = { invoiceId, amount ->
                    onEditInvoice(invoiceId, amount)
                }
            )
        }
    }
}

@Composable
private fun InvoiceSummaryItem(
    summary: InvoiceTransactionsUiState.InvoiceSummary,
    modifier: Modifier = Modifier,
    onEditClick: ((Long, Double) -> Unit)? = null
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
                label = "Gastos",
                amount = summary.expense,
                color = Expense,
                isNegative = true
            )

            SummaryRow(
                label = "Adiantamentos",
                amount = summary.advancePayment,
                color = InvoicePayment,
                isPositive = true
            )

            if (summary.mustShowAdjustment) {
                SummaryRow(
                    label = "Ajustes",
                    amount = summary.adjustment,
                    color = Adjustment,
                    showSign = true
                )
            }

            HorizontalDivider()

            SummaryRow(
                label = "Total",
                amount = summary.total,
                color = colorScheme.onSurface,
                isTotal = true,
                onEditClick = if (summary.canEdit) {
                    onEditClick?.let { { it(summary.invoiceId, summary.total) } }
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
                    text = "Antecipar Pagamento",
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
                    text = "Fechar Fatura",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (summary.status.isFuture) {
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
                    text = "Excluir Fatura",
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
                    text = "Reabrir Fatura",
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
                    text = "Pagar Fatura",
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
                    contentDescription = "Editar fatura",
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
        label = { Text(selectedCategory?.name ?: "Categoria") },
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
            text = { Text("Todas") },
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
            Transaction.Type.ADVANCE_PAYMENT -> BillPaymentColor
            else -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.EXPENSE -> "Despesa"
                    Transaction.Type.ADJUSTMENT -> "Ajuste"
                    Transaction.Type.ADVANCE_PAYMENT -> "Antecipação"
                    else -> "Tipo"
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
            text = { Text("Todos") },
            onClick = {
                onAction(InvoiceTransactionsAction.SelectType(null))
                expanded = false
            }
        )

        listOf(
            Transaction.Type.EXPENSE to "Despesa",
            Transaction.Type.ADJUSTMENT to "Ajuste",
            Transaction.Type.ADVANCE_PAYMENT to "Antecipação",
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
