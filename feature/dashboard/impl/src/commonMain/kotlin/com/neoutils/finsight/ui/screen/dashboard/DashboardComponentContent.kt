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
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import androidx.navigation.NavController
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AccountCard
import com.neoutils.finsight.ui.component.ConsolidationBadge
import com.neoutils.finsight.ui.component.AccountCardVariant
import com.neoutils.finsight.ui.component.BalanceCard
import com.neoutils.finsight.ui.component.BalanceCardConfig
import com.neoutils.finsight.ui.component.BudgetProgressCard
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.CategorySpendingCard
import com.neoutils.finsight.ui.component.CreditCardCard
import com.neoutils.finsight.ui.component.CreditCardCardVariant
import com.neoutils.finsight.ui.component.creditCardSharedElement
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.component.TransactionCard
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

    val openTransactions = { filterLabel: TransactionLabel?, filterTarget: TransactionTarget? ->
        navController.navigate(TransactionsRoute(filterLabel, filterTarget))
    }

    when (variant) {
        is DashboardComponentVariant.TotalBalance -> {
            TotalBalanceCard(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.OverallBalanceStats -> {
            DashboardOverallBalanceSection(
                variant = variant,
                openTransactions = openTransactions,
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

        is DashboardComponentVariant.MonthSettlement -> {
            DashboardMonthSettlementSection(
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
    val clock = koinInject<Clock>()
    val component = variant.component
    val showHeader = variant.config.showHeader(variant.key)

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
        component.recurringList.forEach { item ->
            val recurring = item.recurring
            PendingRecurringCard(
                recurring = recurring,
                amount = item.amount,
                onClick = {
                    if (variant is DashboardComponentVariant.PendingRecurring.Viewing) {
                        val currentDate = clock.today()
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
    openTransactions: (TransactionLabel?, TransactionTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailController = LocalDetailPaneController.current
    val transactionsEntry = koinInject<TransactionsEntry>()
    val component = variant.component
    val showHeader = variant.config.showHeader(variant.key)

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
        component.transactions.forEachIndexed { index, transactionUi ->
            val isLastWithFade = component.hasMore && index == component.transactions.lastIndex
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
                            transactionUi.direction.isAdjustment -> detailController.show(transactionsEntry.viewAdjustmentModal(transactionUi.id))
                            else -> detailController.show(transactionsEntry.viewTransactionModal(transactionUi.id))
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
    val showHeader = variant.config.showHeader(variant.key)

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
                    .padding(horizontal = 16.dp)
                    // The same handle the bottom bar and the rail give this destination: one
                    // section, one tag, wherever the shell happens to offer it.
                    .testTag(action.testTag),
            )
        }
    }
}

/**
 * The shape every flow widget shares: an optional header naming its perimeter — without
 * it the neutral and the accounts perimeters are indistinguishable, since income is
 * literally the same number in both (design D5) — over a pair of balance cards.
 */
@Composable
private fun DashboardFlowStatsSection(
    title: String,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
    cards: @Composable RowScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (showHeader) {
            DashboardSectionHeader(
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = cards,
        )
    }
}

@Composable
private fun DashboardOverallBalanceSection(
    variant: DashboardComponentVariant.OverallBalanceStats,
    openTransactions: (TransactionLabel?, TransactionTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val seeRates = LocalNavController.current.seeRates()

    DashboardFlowStatsSection(
        title = stringResource(Res.string.dashboard_overall_balance),
        showHeader = variant.config.showHeader(variant.key),
        modifier = modifier,
    ) {
        BalanceCard(
            balance = component.income,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.Income,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_income_amount",
            onClick = {
                if (variant is DashboardComponentVariant.OverallBalanceStats.Viewing) {
                    openTransactions(TransactionLabel.INCOME, null)
                }
            },
        )

        BalanceCard(
            balance = component.expense,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.Expense,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_expenses_amount",
            onClick = {
                if (variant is DashboardComponentVariant.OverallBalanceStats.Viewing) {
                    openTransactions(TransactionLabel.EXPENSE, null)
                }
            },
        )
    }
}

@Composable
private fun DashboardConcreteBalanceSection(
    variant: DashboardComponentVariant.ConcreteBalanceStats,
    openTransactions: (TransactionLabel?, TransactionTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val seeRates = LocalNavController.current.seeRates()

    DashboardFlowStatsSection(
        title = stringResource(Res.string.dashboard_balance),
        showHeader = variant.config.showHeader(variant.key),
        modifier = modifier,
    ) {
        BalanceCard(
            balance = component.income,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.AccountIncome,
            onSeeRates = seeRates,
            onClick = {
                if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                    openTransactions(TransactionLabel.INCOME, null)
                }
            },
        )

        BalanceCard(
            balance = component.expense,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.AccountExpense,
            onSeeRates = seeRates,
            onClick = {
                if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                    openTransactions(TransactionLabel.EXPENSE, null)
                }
            },
        )
    }
}

@Composable
private fun DashboardPendingBalanceSection(
    variant: DashboardComponentVariant.PendingBalanceStats,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val seeRates = LocalNavController.current.seeRates()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        BalanceCard(
            balance = component.pendingIncome,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.PendingIncome,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_pending_income_amount",
        )

        BalanceCard(
            balance = component.pendingExpense,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.PendingExpense,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_pending_expense_amount",
        )
    }
}

/**
 * The pair always reads as a pair: both classes are rendered whatever the sources say,
 * and a class with nothing in it reads zero rather than disappearing. The header carries
 * the whole meaning of the figure — "coming in" and "going out" name a direction, and it
 * is the title that names the window they are about — so it is not optional here.
 */
@Composable
private fun DashboardMonthSettlementSection(
    variant: DashboardComponentVariant.MonthSettlement,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val seeRates = LocalNavController.current.seeRates()

    DashboardFlowStatsSection(
        title = stringResource(Res.string.component_month_settlement),
        showHeader = true,
        modifier = modifier,
    ) {
        BalanceCard(
            balance = component.incoming,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.SettlementIncoming,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_settlement_incoming_amount",
        )

        BalanceCard(
            balance = component.outgoing,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.SettlementOutgoing,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_settlement_outgoing_amount",
        )
    }
}

@Composable
private fun DashboardCreditCardBalanceSection(
    variant: DashboardComponentVariant.CreditCardBalanceStats,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val seeRates = LocalNavController.current.seeRates()

    DashboardFlowStatsSection(
        title = stringResource(Res.string.dashboard_credit_card_balance),
        showHeader = variant.config.showHeader(variant.key),
        modifier = modifier,
    ) {
        BalanceCard(
            balance = component.payment,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.InvoicePayment,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_credit_card_payment_amount",
        )

        BalanceCard(
            balance = component.expense,
            modifier = Modifier.weight(1f),
            config = BalanceCardConfig.CreditCardExpense,
            onSeeRates = seeRates,
            amountTestTag = "dashboard_credit_card_expenses_amount",
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
    val showHeader = variant.config.showHeader(variant.key)

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
                        iconKey = creditCardUi.iconKey,
                        name = creditCardUi.name,
                        closingDay = creditCardUi.closingDay,
                        dueDay = creditCardUi.dueDay,
                        limit = component.limits[page],
                        invoiceUi = creditCardUi.invoiceUi,
                        testTagPrefix = "dashboard_credit_card",
                        // Only the current page is promoted: a neighbour composed by the pager's
                        // contentPadding would be lifted to the overlay and lose its clip.
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (page == pagerState.currentPage) {
                                    Modifier.creditCardSharedElement(creditCardUi.cardId)
                                } else Modifier
                            ),
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
    val navController = LocalNavController.current

    CategorySpendingCard(
        categorySpending = component.categorySpending,
        type = Category.Type.EXPENSE,
        onSeeRates = { navController.navigate(ExchangeRatesRoute) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onSubjectClick = { subject ->
            // Only a category has a modal to open: the unclassified line has no id to
            // navigate by, and no destination of its own yet.
            if (variant is DashboardComponentVariant.SpendingByCategory.Viewing &&
                subject is SpendingSubject.Categorized
            ) {
                detailController.show(categoriesEntry.viewCategoryModal(subject.category.id))
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
    val navController = LocalNavController.current

    CategorySpendingCard(
        categorySpending = component.categoryIncome,
        type = Category.Type.INCOME,
        title = stringResource(Res.string.component_income_by_category),
        onSeeRates = { navController.navigate(ExchangeRatesRoute) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onSubjectClick = { subject ->
            // Only a category has a modal to open: the unclassified line has no id to
            // navigate by, and no destination of its own yet.
            if (variant is DashboardComponentVariant.IncomeByCategory.Viewing &&
                subject is SpendingSubject.Categorized
            ) {
                detailController.show(categoriesEntry.viewCategoryModal(subject.category.id))
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
    val navController = LocalNavController.current
    val component = variant.component

    BudgetProgressCard(
        budgetProgress = component.budgetProgress,
        onSeeRates = { navController.navigate(ExchangeRatesRoute) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onBudgetClick = { budget ->
            if (variant is DashboardComponentVariant.Budgets.Viewing) {
                detailController.show(budgetsEntry.viewBudgetModal(budget.budget.id, component.targetMonth))
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
    amount: DisplayAmount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            MoneyText(
                amount = amount,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor,
                ),
                modifier = Modifier.testTag("dashboard_pending_recurring_amount"),
            )
        }
    }
}

/**
 * The dashboard's headline figure. It sums every account, so it is consolidated and may
 * hold more than one term — which is why it is rendered by [MoneyText] and never
 * juxtaposed in a line: at `headlineMedium` a second term has nowhere to go sideways
 * (design D22).
 */
@Composable
private fun TotalBalanceCard(
    variant: DashboardComponentVariant.TotalBalance,
    modifier: Modifier = Modifier,
) {
    val component = variant.component
    val navController = LocalNavController.current

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
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_total_balance),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant,
                )

                // The card's top-right corner, not under the figure: `≈` at headline size
                // is not a touch target, and the explanation it needs is one tap for the
                // user who wants it instead of a permanent line for everyone (D21/D25).
                // Every badge in the app sits in this corner, so it is looked for once.
                ConsolidationBadge(
                    figures = listOf(component.amount),
                    onSeeRates = navController.seeRates(),
                    // Named here and not inside the badge: it is one component drawn by a
                    // dozen surfaces, and a tag of its own would be ambiguous the moment
                    // two of them share a screen. Same reason `BalanceCard` names the
                    // amount from the outside.
                    modifier = Modifier.testTag("dashboard_total_balance_badge"),
                )
            }

            MoneyText(
                figure = component.amount,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                ),
                // This card reads from its left edge: the label above, the figure below.
                align = TextAlign.Start,
                modifier = Modifier.testTag("dashboard_total_balance_amount"),
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
    val showHeader = variant.config.showHeader(variant.key)

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

/**
 * Where a consolidation badge leads, on a screen where every one of them leads to the same
 * place — the rate archive, which is the only thing that resolves any of it (design D25).
 *
 * The dashboard draws nine consolidated figures across five widgets, so this is written
 * once rather than nine times, and no widget gets to send the user somewhere else.
 */
private fun NavController.seeRates(): () -> Unit = { navigate(ExchangeRatesRoute) }
