@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.*
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.editCreditCardLimit.EditCreditCardLimitModal
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.theme.TextLight1
import com.neoutils.finance.util.DateFormats
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

private val formats = DateFormats()

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    openCategories: () -> Unit = {},
    openCreditCards: () -> Unit = {},
    openAccounts: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val navigator = LocalNavigator.current

    DashboardContent(
        uiState = uiState,
        openTransactions = openTransactions,
        onOpenCategories = openCategories,
        onOpenCreditCards = openCreditCards,
        onOpenAccounts = openAccounts,
        modalManager = modalManager,
        navigator = navigator
    )
}

@Composable
private fun DashboardContent(
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCreditCards: () -> Unit,
    onOpenAccounts: () -> Unit,
    uiState: DashboardUiState,
    modalManager: ModalManager,
    navigator: Navigator
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = formats.yearMonth.format(uiState.yearMonth))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background,
            ),
            windowInsets = WindowInsets()
        )
    },
    contentWindowInsets = WindowInsets(),
) { paddingValues ->

    val creditCardPagerState = rememberPagerState(
        pageCount = { uiState.creditCards.size }
    )

    val accountPagerState = rememberPagerState(
        pageCount = { uiState.accounts.size }
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 16.dp,
        ),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        item(
            key = "accounts_pager",
        ) {
            DashboardAccountPager(
                accounts = uiState.accounts,
                pagerState = accountPagerState,
                onAccountClick = { accountId ->
                    navigator.navigate(NavigationAction.Accounts(accountId = accountId))
                },
                onEditBalance = { accountId, currentBalance ->
                    modalManager.show(
                        EditBalanceModal(
                            type = EditBalanceModal.Type.CURRENT,
                            currentBalance = currentBalance,
                            accountId = accountId,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
            )
        }

        item(
            key = "balance"
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem()
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
            item(
                key = "credit_cards_pager",
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .animateItem(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalPager(
                        state = creditCardPagerState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val creditCardUi = uiState.creditCards[page]

                        DashboardCreditCardUI(
                            ui = creditCardUi,
                            config = CreditCardUiConfig.from(creditCardUi = creditCardUi),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                navigator.navigate(
                                    NavigationAction.CreditCards(
                                        creditCardId = creditCardUi.creditCard.id
                                    )
                                )
                            },
                            onCloseInvoice = {
                                creditCardUi.invoiceUi?.let {
                                    modalManager.show(CloseInvoiceModal(it.id, it.closingDate))
                                }
                            },
                            onPayInvoice = {
                                creditCardUi.invoiceUi?.let {
                                    modalManager.show(
                                        PayInvoiceModal(
                                            invoice = it.invoice,
                                            currentBillAmount = it.amount
                                        )
                                    )
                                }
                            },
                            onAdvancePayment = {
                                creditCardUi.invoiceUi?.let {
                                    modalManager.show(
                                        AdvancePaymentModal(
                                            invoice = it.invoice,
                                            currentBillAmount = it.amount
                                        )
                                    )
                                }
                            },
                            onEditAmount = {
                                creditCardUi.invoiceUi?.let {
                                    modalManager.show(
                                        EditBalanceModal(
                                            type = EditBalanceModal.Type.CREDIT_CARD,
                                            currentBalance = it.amount,
                                            invoiceId = it.id
                                        )
                                    )
                                }
                            },
                            onEditLimit = {
                                modalManager.show(
                                    EditCreditCardLimitModal(
                                        creditCard = creditCardUi.creditCard
                                    )
                                )
                            }
                        )
                    }
                    if (uiState.creditCards.size > 1) {
                        PageIndicator(
                            count = uiState.creditCards.size,
                            current = creditCardPagerState.currentPage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (uiState.categorySpending.isNotEmpty()) {
            item(
                key = "category_spending"
            ) {
                CategorySpendingCard(
                    categorySpending = uiState.categorySpending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                    onCategoryClick = { category ->
                        modalManager.show(ViewCategoryModal(category))
                    }
                )
            }
        }

        if (uiState.recents.isNotEmpty()) {
            item(
                key = "recents_title"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recentes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = {
                            openTransactions(null, null)
                        }
                    ) {
                        Text(text = "Ver Tudo")
                    }
                }
            }
        }

        itemsIndexed(
            items = uiState.recents,
            key = { _, transaction ->
                "transaction_" + transaction.id
            },
        ) { index, transaction ->
            val isLastWithFade = uiState.hasMoreRecents && index == uiState.recents.lastIndex

            TransactionCard(
                transaction = transaction,
                category = transaction.category,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (isLastWithFade) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent)
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        } else {
                            Modifier
                        }
                    ).animateItem(),
                onClick = {
                    when {
                        isLastWithFade -> {
                            openTransactions(null, null)
                        }

                        transaction.type.isAdjustment -> {
                            modalManager.show(ViewAdjustmentModal(transaction))
                        }

                        else -> {
                            modalManager.show(ViewTransactionModal(transaction))
                        }
                    }
                }
            )
        }

        item(
            key = "open_category_action"
        ) {
            Card(
                onClick = onOpenCategories,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp)
                    .animateItem(),
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

        item(
            key = "open_credit_card_action"
        ) {
            Card(
                onClick = onOpenCreditCards,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp)
                    .animateItem(),
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

        item(
            key = "open_accounts_action"
        ) {
            Card(
                onClick = onOpenAccounts,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainer,
                    contentColor = colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Contas",
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

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(
        space = 4.dp,
        alignment = Alignment.CenterHorizontally
    ),
    verticalAlignment = Alignment.CenterVertically
) {
    repeat(count) { index ->

        Box(
            modifier = Modifier
                .size(
                    when (index) {
                        current -> 8.dp
                        else -> 6.dp
                    }
                )
                .background(
                    color = when (index) {
                        current -> colorScheme.primary
                        else -> colorScheme.outline
                    },
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun DashboardAccountPager(
    accounts: List<DashboardAccountUi>,
    pagerState: PagerState,
    onAccountClick: (Long) -> Unit,
    onEditBalance: (Long, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp,
    ) { page ->
        DashboardAccountCard(
            accountUi = accounts[page],
            onClick = { onAccountClick(accounts[page].account.id) },
            onEditBalance = { onEditBalance(accounts[page].account.id, accounts[page].balance) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DashboardAccountCard(
    accountUi: DashboardAccountUi,
    onClick: () -> Unit,
    onEditBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = accountUi.account
    val balance = accountUi.balance

    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = account.name,
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                if (account.isDefault) {
                    Text(
                        text = "Padrão",
                        fontSize = 12.sp,
                        color = TextLight1,
                    )
                }
            }

            Column {
                Text(
                    text = "Saldo Atual",
                    fontSize = 12.sp,
                    color = TextLight1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onEditBalance() }
                ) {
                    Text(
                        text = balance.toMoneyFormat(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    Icon(
                        imageVector = Icons.Rounded.ModeEdit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
