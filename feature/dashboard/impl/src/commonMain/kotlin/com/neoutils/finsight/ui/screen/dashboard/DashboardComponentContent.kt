@file:OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.feature.accounts.api.AccountsEntry
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.budgets.api.BudgetsEntry
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.feature.transactions.api.TransactionsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavRoute
import org.koin.compose.koinInject

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountCard
import com.neoutils.finsight.ui.component.AccountCardVariant
import com.neoutils.finsight.ui.component.BalanceCard
import com.neoutils.finsight.ui.component.BalanceCardConfig
import com.neoutils.finsight.ui.component.BudgetProgressCard
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.CategorySpendingCard
import com.neoutils.finsight.ui.component.CreditCardCard
import com.neoutils.finsight.ui.component.CreditCardCardVariant
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.TransactionCard
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun DashboardComponentContent(
    variant: DashboardComponentVariant,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current

    val openTransactions = { filterType: TransactionType?, filterTarget: TransactionTarget? ->
        navController.navigate(TransactionsRoute(filterType, filterTarget))
    }

    when (variant) {
        is DashboardComponentVariant.TotalBalance -> {
            TotalBalanceCard(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.ConcreteBalanceStats -> {
            DashboardConcreteBalanceSection(
                variant = variant,
                openTransactions = openTransactions,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.PendingBalanceStats -> {
            DashboardPendingBalanceSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.CreditCardBalanceStats -> {
            DashboardCreditCardBalanceSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.AccountsOverview -> {
            DashboardAccountsRow(
                variant = variant,
                onOpenAccounts = { navController.navigate(AccountsRoute()) },
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.CreditCardsPager -> {
            DashboardCreditCardsSection(
                variant = variant,
                onOpenCreditCards = { navController.navigate(CreditCardsRoute()) },
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.SpendingByCategory -> {
            DashboardSpendingByCategorySection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.IncomeByCategory -> {
            DashboardIncomeByCategorySection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.Budgets -> {
            DashboardBudgetsSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.PendingRecurring -> {
            DashboardPendingRecurringSection(
                variant = variant,
                onOpenRecurring = { navController.navigate(RecurringRoute) },
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.Recents -> {
            DashboardRecentsSection(
                variant = variant,
                openTransactions = openTransactions,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.QuickActions -> {
            DashboardQuickActionsSection(
                variant = variant,
                onNavigate = { navController.navigate(it) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DashboardPendingRecurringSection(
    variant: DashboardComponentVariant.PendingRecurring,
    onOpenRecurring: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val recurringEntry = koinInject<RecurringEntry>()
    val component = variant.component
    val showHeader = variant.config.showHeader()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = stringResource(Res.string.dashboard_pending_recurring),
                onClick = {
                    if (variant is DashboardComponentVariant.PendingRecurring.Viewing) {
                        onOpenRecurring()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        component.recurringList.forEach { recurring ->
            PendingRecurringCard(
                recurring = recurring,
                onClick = {
                    if (variant is DashboardComponentVariant.PendingRecurring.Viewing) {
                        val currentDate = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                        val targetDate = currentDate.yearMonth
                            .safeOnDay(recurring.dayOfMonth)
                            .takeIf { it <= currentDate }
                            ?: currentDate
                        modalManager.show(recurringEntry.confirmRecurringModal(recurring, targetDate))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun DashboardRecentsSection(
    variant: DashboardComponentVariant.Recents,
    openTransactions: (TransactionType?, TransactionTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailController = LocalDetailPaneController.current
    val transactionsEntry = koinInject<TransactionsEntry>()
    val component = variant.component
    val showHeader = variant.config.showHeader()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = stringResource(Res.string.dashboard_recents),
                onClick = {
                    if (variant is DashboardComponentVariant.Recents.Viewing) {
                        openTransactions(null, null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        component.transactions.forEachIndexed { index, transaction ->
            val isLastWithFade = component.hasMore && index == component.transactions.lastIndex
            val transactionUi = transaction.toTransactionUi(lookup = component.facadeLookup) ?: return@forEachIndexed
            TransactionCard(
                transaction = transactionUi,
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
                                            colors = listOf(Color.Black, Color.Transparent),
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                        } else {
                            Modifier
                        }
                    ),
                onClick = {
                    if (variant is DashboardComponentVariant.Recents.Viewing) {
                        when {
                            isLastWithFade -> openTransactions(null, null)
                            transactionUi.direction.isAdjustment -> detailController.show(transactionsEntry.viewAdjustmentModal(transaction.id))
                            else -> detailController.show(transactionsEntry.viewTransactionModal(transaction.id))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DashboardQuickActionsSection(
    variant: DashboardComponentVariant.QuickActions,
    onNavigate: (route: NavRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val showHeader = variant.config.showHeader()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = stringResource(Res.string.component_quick_actions),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        component.actions.forEach { action ->
            DashboardQuickActionCard(
                action = action,
                onOpen = { type ->
                    if (variant is DashboardComponentVariant.QuickActions.Viewing) {
                        onNavigate(type.route)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun DashboardConcreteBalanceSection(
    variant: DashboardComponentVariant.ConcreteBalanceStats,
    openTransactions: (TransactionType?, TransactionTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = variant.component

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            BalanceCard(
                balance = component.income,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Income,
                onClick = {
                    if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                        openTransactions(TransactionType.INCOME, null)
                    }
                },
            )

            BalanceCard(
                balance = component.expense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Expense,
                onClick = {
                    if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                        openTransactions(TransactionType.EXPENSE, null)
                    }
                },
            )
        }
    }
}

@Composable
private fun DashboardPendingBalanceSection(
    variant: DashboardComponentVariant.PendingBalanceStats,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val showBothCards = component.pendingIncome <= 0.0 && component.pendingExpense <= 0.0

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (component.pendingIncome > 0.0 || showBothCards) {
            BalanceCard(
                balance = component.pendingIncome,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.PendingIncome,
            )
        }

        if (component.pendingExpense > 0.0 || showBothCards) {
            BalanceCard(
                balance = component.pendingExpense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.PendingExpense,
            )
        }
    }
}

@Composable
private fun DashboardCreditCardBalanceSection(
    variant: DashboardComponentVariant.CreditCardBalanceStats,
    modifier: Modifier = Modifier,
) {
    val component = variant.component

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        BalanceCard(
            balance = component.payment,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.InvoicePayment,
        )

        BalanceCard(
            balance = component.expense,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.CreditCardExpense,
        )
    }
}

@Composable
private fun DashboardCreditCardsSection(
    variant: DashboardComponentVariant.CreditCardsPager,
    onOpenCreditCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val modalManager = LocalModalManager.current
    val creditCardsEntry = koinInject<CreditCardsEntry>()
    val component = variant.component
    val showHeader = variant.config.showHeader()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = stringResource(Res.string.dashboard_credit_cards),
                onClick = {
                    if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                        onOpenCreditCards()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        when (component) {
            DashboardComponent.CreditCardsPager.Empty -> {
                DashboardCreditCardsEmptyCard(
                    onCreateCard = {
                        if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                            modalManager.show(creditCardsEntry.creditCardFormModal())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            is DashboardComponent.CreditCardsPager.Content -> {
                val pagerState = rememberPagerState(
                    pageCount = { component.creditCards.size },
                )

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 8.dp,
                    userScrollEnabled = variant is DashboardComponentVariant.CreditCardsPager.Viewing,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val creditCardUi = component.creditCards[page]
                    val bill = creditCardUi.invoiceUi
                    val domainInvoice = component.domainInvoices[page]

                    CreditCardCard(
                        cardId = creditCardUi.cardId,
                        iconKey = creditCardUi.iconKey,
                        name = creditCardUi.name,
                        closingDay = creditCardUi.closingDay,
                        dueDay = creditCardUi.dueDay,
                        limit = creditCardUi.limit,
                        invoiceUi = creditCardUi.invoiceUi,
                        modifier = Modifier.fillMaxWidth(),
                        variant = CreditCardCardVariant.Dashboard(
                            onClick = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    navController.navigate(
                                        CreditCardsRoute(creditCardId = creditCardUi.cardId)
                                    )
                                }
                            },
                            onCloseInvoice = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(creditCardsEntry.closeInvoiceModal(it.id, it.closingDate))
                                    }
                                }
                            },
                            onPayInvoice = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    if (domainInvoice != null && bill != null) {
                                        modalManager.show(
                                            creditCardsEntry.payInvoiceModal(invoice = domainInvoice, currentBillAmount = bill.amount)
                                        )
                                    }
                                }
                            },
                            onAdvancePayment = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    if (domainInvoice != null && bill != null) {
                                        modalManager.show(
                                            creditCardsEntry.advancePaymentModal(invoice = domainInvoice, currentBillAmount = bill.amount)
                                        )
                                    }
                                }
                            },
                            onEditAmount = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    domainInvoice?.let {
                                        modalManager.show(creditCardsEntry.editInvoiceBalanceModal(it))
                                    }
                                }
                            },
                        ),
                    )
                }

                if (component.creditCards.size > 1) {
                    PageIndicator(
                        count = component.creditCards.size,
                        current = pagerState.currentPage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardSpendingByCategorySection(
    variant: DashboardComponentVariant.SpendingByCategory,
    modifier: Modifier = Modifier,
) {
    val detailController = LocalDetailPaneController.current
    val categoriesEntry = koinInject<CategoriesEntry>()
    val component = variant.component

    CategorySpendingCard(
        categorySpending = component.categorySpending,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onCategoryClick = { category ->
            if (variant is DashboardComponentVariant.SpendingByCategory.Viewing) {
                detailController.show(categoriesEntry.viewCategoryModal(category.id))
            }
        }
    )
}

@Composable
private fun DashboardIncomeByCategorySection(
    variant: DashboardComponentVariant.IncomeByCategory,
    modifier: Modifier = Modifier,
) {
    val detailController = LocalDetailPaneController.current
    val categoriesEntry = koinInject<CategoriesEntry>()
    val component = variant.component

    CategorySpendingCard(
        categorySpending = component.categoryIncome,
        title = stringResource(Res.string.component_income_by_category),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onCategoryClick = { category ->
            if (variant is DashboardComponentVariant.IncomeByCategory.Viewing) {
                detailController.show(categoriesEntry.viewCategoryModal(category.id))
            }
        }
    )
}

@Composable
private fun DashboardBudgetsSection(
    variant: DashboardComponentVariant.Budgets,
    modifier: Modifier = Modifier,
) {
    val detailController = LocalDetailPaneController.current
    val budgetsEntry = koinInject<BudgetsEntry>()
    val component = variant.component

    BudgetProgressCard(
        budgetProgress = component.budgetProgress,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onBudgetClick = { budget ->
            if (variant is DashboardComponentVariant.Budgets.Viewing) {
                detailController.show(budgetsEntry.viewBudgetModal(budget.budget.id))
            }
        },
    )
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (onClick != null) {
            TextButton(onClick = onClick) {
                Text(
                    text = stringResource(Res.string.dashboard_see_all),
                )
            }
        }
    }
}

@Composable
private fun DashboardQuickActionCard(
    action: NavDestination,
    onOpen: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(action.labelRes)

    Card(
        onClick = { onOpen(action) },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
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
                val category = recurring.category
                if (category != null) {
                    CategoryIconBox(
                        category = category,
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

@Composable
private fun TotalBalanceCard(
    variant: DashboardComponentVariant.TotalBalance,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val component = variant.component

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                .padding(
                    horizontal = 20.dp,
                    vertical = 22.dp
                ),
        ) {
            Text(
                text = stringResource(Res.string.dashboard_total_balance),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatter.format(component.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DashboardAccountsRow(
    variant: DashboardComponentVariant.AccountsOverview,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val modalManager = LocalModalManager.current
    val accountsEntry = koinInject<AccountsEntry>()
    val component = variant.component
    val showHeader = variant.config.showHeader()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = stringResource(Res.string.dashboard_accounts),
                onClick = {
                    if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                        onOpenAccounts()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = variant is DashboardComponentVariant.AccountsOverview.Viewing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = component.accounts.sortedByDescending { it.isDefault },
                key = { accountUi -> accountUi.id },
            ) { accountUi ->
                AccountCard(
                    iconKey = accountUi.iconKey,
                    name = accountUi.name,
                    isDefault = accountUi.isDefault,
                    variant = AccountCardVariant.Dashboard(
                        balance = accountUi.balance,
                        onClick = {
                            if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                                navController.navigate(AccountsRoute(accountId = accountUi.id))
                            }
                        },
                    ),
                )
            }

            item(key = "add_account") {
                DashboardAddAccountCard(
                    onClick = {
                        if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                            modalManager.show(accountsEntry.accountFormModal())
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DashboardCreditCardsEmptyCard(
    onCreateCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Surface(
                color = colorScheme.primary.copy(alpha = 0.12f),
                contentColor = colorScheme.primary,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_cards_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.dashboard_credit_cards_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onCreateCard,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.credit_cards_create))
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
