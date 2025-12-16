@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.ui.component.BalanceCard
import com.neoutils.finance.ui.component.BalanceCardConfig
import com.neoutils.finance.ui.component.CategorySpendingCard
import com.neoutils.finance.ui.component.CreditCardBillCard
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.component.TransactionCard
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.editCreditCardLimit.EditCreditCardLimitModal
import com.neoutils.finance.ui.modal.payBill.PayBillModal
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finance.ui.modal.viewCreditCard.ViewCreditCardModal
import com.neoutils.finance.util.DateFormats
import com.neoutils.finance.extension.toYearMonth
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val formats = DateFormats()

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    openCategories: () -> Unit = {},
    openCreditCards: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    DashboardContent(
        uiState = uiState,
        openTransactions = openTransactions,
        onOpenCategories = openCategories,
        onOpenCreditCards = openCreditCards,
        modalManager = modalManager,
        openEditBalance = {
            modalManager.show(
                EditBalanceModal(
                    currentBalance = uiState.balance.balance,
                )
            )
        }
    )
}

@Composable
private fun DashboardContent(
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    openEditBalance: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCreditCards: () -> Unit,
    uiState: DashboardUiState,
    modalManager: ModalManager
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = formats.yearMonth.format(uiState.yearMonth))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background,
            ),
        )
    },
    contentWindowInsets = WindowInsets()
) { paddingValues ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 16.dp,
        ),
    ) {

        item {
            BalanceCard(
                balance = uiState.balance.balance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onEditClick = openEditBalance,
                onClick = null,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BalanceCard(
                    balance = uiState.balance.income,
                    modifier = Modifier.weight(1f),
                    config = BalanceCardConfig.Income,
                    onClick = { openTransactions(Transaction.Type.INCOME, null) }
                )

                BalanceCard(
                    balance = uiState.balance.expense,
                    modifier = Modifier.weight(1f),
                    config = BalanceCardConfig.Expense,
                    onClick = { openTransactions(Transaction.Type.EXPENSE, null) }
                )
            }
        }

        if (uiState.creditCards.isNotEmpty()) {

            item {
                val pagerState = rememberPagerState(
                    pageCount = { uiState.creditCards.size }
                )

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cartões de Crédito",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        TextButton(
                            onClick = onOpenCreditCards
                        ) {
                            Text(text = "Ver Todos")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp
                        ),
                        pageSpacing = 8.dp
                    ) { page ->
                        val cardWithBill = uiState.creditCards[page]
                        val invoice = cardWithBill.currentInvoice
                        val invoiceStatus = invoice?.status

                        @OptIn(ExperimentalTime::class)
                        val currentMonth = Clock.System.now().toYearMonth()
                        val isInClosingMonth = invoice?.let { currentMonth >= it.closingMonth } ?: false

                        CreditCardBillCard(
                            uiModel = cardWithBill.billUi,
                            cardName = cardWithBill.creditCard.name,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                modalManager.show(
                                    ViewCreditCardModal(
                                        creditCard = cardWithBill.creditCard,
                                        billAmount = cardWithBill.billAmount
                                    )
                                )
                            },
                            onEditBill = if (invoiceStatus == Invoice.Status.OPEN) {
                                {
                                    invoice?.let {
                                        modalManager.show(
                                            EditBalanceModal(
                                                type = EditBalanceModal.Type.CREDIT_CARD,
                                                currentBalance = cardWithBill.billAmount,
                                                invoiceId = it.id
                                            )
                                        )
                                    }
                                }
                            } else null,
                            onEditLimit = {
                                modalManager.show(
                                    EditCreditCardLimitModal(
                                        creditCardId = cardWithBill.creditCard.id
                                    )
                                )
                            },
                            // Fechar Fatura - apenas no mês de fechamento e fatura OPEN
                            onCloseClick = if (invoiceStatus == Invoice.Status.OPEN && isInClosingMonth) {
                                {
                                    invoice?.let {
                                        modalManager.show(CloseInvoiceModal(it))
                                    }
                                }
                            } else null,
                            // Pagar/Antecipar - baseado no status
                            onPayClick = when (invoiceStatus) {
                                Invoice.Status.OPEN -> {
                                    // Antecipar Pagamento
                                    {
                                        invoice?.let {
                                            modalManager.show(
                                                AdvancePaymentModal(
                                                    invoice = it,
                                                    currentBillAmount = cardWithBill.billAmount
                                                )
                                            )
                                        }
                                    }
                                }
                                Invoice.Status.CLOSED -> {
                                    // Pagar Fatura
                                    {
                                        invoice?.let {
                                            modalManager.show(
                                                PayBillModal(
                                                    invoice = it,
                                                    currentBillAmount = cardWithBill.billAmount
                                                )
                                            )
                                        }
                                    }
                                }
                                else -> null
                            },
                            showPayButton = invoiceStatus == Invoice.Status.OPEN || invoiceStatus == Invoice.Status.CLOSED,
                            payButtonText = when (invoiceStatus) {
                                Invoice.Status.OPEN -> "Antecipar Pagamento"
                                Invoice.Status.CLOSED -> "Pagar Fatura"
                                else -> ""
                            }
                        )
                    }

                    if (uiState.creditCards.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(uiState.creditCards.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (isSelected) 8.dp else 6.dp)
                                        .background(
                                            color = if (isSelected) colorScheme.primary else colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.categorySpending.isNotEmpty()) {
            item {
                CategorySpendingCard(
                    categorySpending = uiState.categorySpending,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .animateItem(),
                    onCategoryClick = { category ->
                        modalManager.show(
                            ViewCategoryModal(category)
                        )
                    }
                )
            }
        }

        if (uiState.recents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recentes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = { openTransactions(null, null) }
                    ) {
                        Text(text = "Ver Tudo")
                    }
                }
            }
        }

        items(items = uiState.recents) { transaction ->
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
                            modalManager.show(
                                ViewAdjustmentModal(transaction)
                            )
                        }

                        else -> {
                            modalManager.show(
                                ViewTransactionModal(transaction)
                            )
                        }
                    }
                }
            )
        }

        item {
            Card(
                onClick = onOpenCategories,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Categorias",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
        }

        item {
            Card(
                onClick = onOpenCreditCards,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cartões de Crédito",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}