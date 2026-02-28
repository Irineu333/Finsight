@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.dashboard

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Wallet
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
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_credit_cards
import com.neoutils.finsight.resources.dashboard_current_balance
import com.neoutils.finsight.resources.dashboard_default
import com.neoutils.finsight.resources.dashboard_installments
import com.neoutils.finsight.resources.dashboard_recents
import com.neoutils.finsight.resources.dashboard_see_all
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

private val formats = DateFormats()

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    openCategories: () -> Unit = {},
    openCreditCards: () -> Unit = {},
    openAccounts: () -> Unit = {},
    openInstallments: () -> Unit = {},
    openBudgets: () -> Unit = {},
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
        onOpenInstallments = openInstallments,
        onOpenBudgets = openBudgets,
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
    onOpenInstallments: () -> Unit,
    onOpenBudgets: () -> Unit,
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
                onEditBalance = { account ->
                    modalManager.show(
                        EditAccountBalanceModal(
                            type = EditAccountBalanceModal.Type.CURRENT,
                            account = account,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .animateItem()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                        EditInvoiceBalanceModal(
                                            initialInvoice = it.invoice,
                                        )
                                    )
                                }
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

        val spendingPages = buildList {
            if (uiState.budgetProgress.isNotEmpty()) add(SpendingPage.Budgets)
            if (uiState.categorySpending.isNotEmpty()) add(SpendingPage.Categories)
        }

        if (spendingPages.isNotEmpty()) {
            item(key = "spending_pager") {
                val pagerState = rememberPagerState(pageCount = { spendingPages.size })

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .animateItem(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        when (spendingPages[page]) {
                            SpendingPage.Categories -> CategorySpendingCard(
                                categorySpending = uiState.categorySpending,
                                modifier = Modifier.fillMaxWidth(),
                                onCategoryClick = { modalManager.show(ViewCategoryModal(it)) },
                            )
                            SpendingPage.Budgets -> BudgetProgressCard(
                                budgetProgress = uiState.budgetProgress,
                                modifier = Modifier.fillMaxWidth(),
                                onBudgetClick = { modalManager.show(ViewBudgetModal(it)) },
                            )
                        }
                    }

                    if (spendingPages.size > 1) {
                        PageIndicator(
                            count = spendingPages.size,
                            current = pagerState.currentPage,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
                        text = stringResource(Res.string.dashboard_recents),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    TextButton(
                        onClick = {
                            openTransactions(null, null)
                        }
                    ) {
                        Text(text = stringResource(Res.string.dashboard_see_all))
                    }
                }
            }
        }

        itemsIndexed(
            items = uiState.recents,
            key = { _, operation ->
                "operation_" + operation.id
            },
        ) { index, operation ->
            val isLastWithFade = uiState.hasMoreRecents && index == uiState.recents.lastIndex

            OperationCard(
                operation = operation,
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

                        operation.type.isAdjustment -> {
                            modalManager.show(ViewAdjustmentModal(operation))
                        }

                        else -> {
                            modalManager.show(ViewOperationModal(operation))
                        }
                    }
                }
            )
        }

        item(
            key = "open_budgets_action"
        ) {
            Card(
                onClick = onOpenBudgets,
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
                        text = stringResource(Res.string.dashboard_budgets),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
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
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp)
                    .animateItem(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.dashboard_categories),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
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
                        text = stringResource(Res.string.dashboard_credit_cards),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
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
                        text = stringResource(Res.string.dashboard_accounts),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
        }

        item(
            key = "open_installments_action"
        ) {
            Card(
                onClick = onOpenInstallments,
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
                        text = stringResource(Res.string.dashboard_installments),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

private enum class SpendingPage { Categories, Budgets }

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
    onEditBalance: (Account) -> Unit,
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
            onEditBalance = { onEditBalance(accounts[page].account) },
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
    val formatter = LocalCurrencyFormatter.current
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
                        text = stringResource(Res.string.dashboard_default),
                        fontSize = 12.sp,
                        color = TextLight1,
                    )
                }
            }

            Column {
                Text(
                    text = stringResource(Res.string.dashboard_current_balance),
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
                        text = formatter.format(balance),
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
