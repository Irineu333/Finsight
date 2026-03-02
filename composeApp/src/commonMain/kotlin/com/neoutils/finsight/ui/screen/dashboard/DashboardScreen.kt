@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_credit_cards
import com.neoutils.finsight.resources.dashboard_default
import com.neoutils.finsight.resources.dashboard_installments
import com.neoutils.finsight.resources.dashboard_pending_recurring
import com.neoutils.finsight.resources.dashboard_add_account
import com.neoutils.finsight.resources.dashboard_recents
import com.neoutils.finsight.resources.dashboard_recurring
import com.neoutils.finsight.resources.dashboard_reports
import com.neoutils.finsight.resources.dashboard_see_all
import com.neoutils.finsight.resources.dashboard_total_balance
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.AppIcon
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    openCategories: () -> Unit = {},
    openCreditCards: () -> Unit = {},
    openAccounts: () -> Unit = {},
    openInstallments: () -> Unit = {},
    openBudgets: () -> Unit = {},
    openRecurring: () -> Unit = {},
    openReports: () -> Unit = {},
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
        onOpenRecurring = openRecurring,
        onOpenReports = openReports,
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
    onOpenRecurring: () -> Unit,
    onOpenReports: () -> Unit,
    uiState: DashboardUiState,
    modalManager: ModalManager,
    navigator: Navigator
) = Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = LocalDateFormats.current.yearMonth.format(uiState.yearMonth))
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
            key = "total_balance",
        ) {
            TotalBalanceCard(
                balance = uiState.balance.balance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
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

        if (uiState.accounts.size > 1) {
            item(key = "accounts_overview") {
                DashboardAccountsRow(
                    accounts = uiState.accounts,
                    onOpenAccounts = onOpenAccounts,
                    onAccountClick = { accountId ->
                        navigator.navigate(NavigationAction.Accounts(accountId = accountId))
                    },
                    onAddAccount = {
                        modalManager.show(AccountFormModal())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .animateItem(),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_credit_cards),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = onOpenCreditCards) {
                            Text(text = stringResource(Res.string.dashboard_see_all))
                        }
                    }

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
                        pageSpacing = 16.dp,
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

        if (uiState.pendingRecurring.isNotEmpty()) {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            item(key = "pending_recurring_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.dashboard_pending_recurring),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onOpenRecurring) {
                        Text(text = stringResource(Res.string.dashboard_see_all))
                    }
                }
            }

            itemsIndexed(
                items = uiState.pendingRecurring,
                key = { _, recurring -> "pending_recurring_${recurring.id}" },
            ) { _, recurring ->
                PendingRecurringCard(
                    recurring = recurring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                    onClick = {
                        val targetDate = today.yearMonth.safeOnDay(recurring.dayOfMonth)
                        modalManager.show(ConfirmRecurringModal(recurring, targetDate))
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
            key = "open_reports_action"
        ) {
            Card(
                onClick = onOpenReports,
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
                        text = stringResource(Res.string.dashboard_reports),
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
                    .padding(top = 8.dp)
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
            key = "open_recurring_action"
        ) {
            Card(
                onClick = onOpenRecurring,
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
                        text = stringResource(Res.string.dashboard_recurring),
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

@Composable
private fun PendingRecurringCard(
    recurring: Recurring,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val typeColor = if (recurring.type.isIncome) Income else Expense

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (recurring.category != null) {
                    CategoryIconBox(
                        category = recurring.category,
                        contentPadding = PaddingValues(12.dp),
                    )
                } else {
                    Surface(
                        color = typeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = if (recurring.type.isIncome) {
                                Icons.AutoMirrored.Filled.TrendingUp
                            } else {
                                Icons.AutoMirrored.Filled.TrendingDown
                            },
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = recurring.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }

            Text(
                text = formatter.format(recurring.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = typeColor,
            )
        }
    }
}

private enum class SpendingPage { Categories, Budgets }

@Composable
private fun TotalBalanceCard(
    balance: Double,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                text = stringResource(Res.string.dashboard_total_balance),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatter.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DashboardAccountsRow(
    accounts: List<DashboardAccountUi>,
    onOpenAccounts: () -> Unit,
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.dashboard_accounts),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onOpenAccounts) {
                Text(text = stringResource(Res.string.dashboard_see_all))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = accounts.sortedByDescending { it.account.isDefault },
                key = { accountUi -> accountUi.account.id },
            ) { accountUi ->
                DashboardAccountItemCard(
                    accountUi = accountUi,
                    onClick = { onAccountClick(accountUi.account.id) },
                )
            }

            item(key = "add_account") {
                DashboardAddAccountCard(
                    onClick = onAddAccount,
                )
            }
        }
    }
}

@Composable
private fun DashboardAccountItemCard(
    accountUi: DashboardAccountUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .width(156.dp)
            .height(112.dp),
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
                    imageVector = AppIcon.fromKey(accountUi.account.iconKey).icon,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                if (accountUi.account.isDefault) {
                    Surface(
                        color = colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = colorScheme.primary,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_default),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = accountUi.account.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = formatter.format(accountUi.balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DashboardAddAccountCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .width(156.dp)
            .height(112.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )

                Text(
                    text = stringResource(Res.string.dashboard_add_account),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
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
