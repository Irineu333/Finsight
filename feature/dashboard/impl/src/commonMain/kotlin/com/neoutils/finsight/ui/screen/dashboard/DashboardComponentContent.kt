@file:OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

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
import org.koin.compose.koinInject
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
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.core.ui.extension.LocalCurrencyFormatter
import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.dashboard.impl.resources.*
import com.neoutils.finsight.core.sharedui.resources.Res as SharedUiRes
import com.neoutils.finsight.core.sharedui.resources.credit_cards_empty
import com.neoutils.finsight.core.sharedui.resources.credit_cards_create
import com.neoutils.finsight.core.sharedui.component.AccountCard
import com.neoutils.finsight.core.sharedui.component.AccountCardVariant
import com.neoutils.finsight.core.ui.component.BalanceCard
import com.neoutils.finsight.core.ui.component.BalanceCardConfig
import com.neoutils.finsight.core.sharedui.component.BudgetProgressCard
import com.neoutils.finsight.core.sharedui.component.CategoryIconBox
import com.neoutils.finsight.core.sharedui.component.CategorySpendingCard
import com.neoutils.finsight.core.sharedui.component.CreditCardCard
import com.neoutils.finsight.core.sharedui.component.CreditCardCardVariant
import com.neoutils.finsight.core.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalNavigationDispatcher
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.core.sharedui.component.OperationCard
import com.neoutils.finsight.feature.accounts.modal.accountForm.AccountFormModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.AdvancePaymentModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.CloseInvoiceModalEntry
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.EditInvoiceBalanceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.PayInvoiceModalEntry
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewAdjustment.ViewAdjustmentModalEntry
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModalEntry
import com.neoutils.finsight.feature.transactions.modal.viewTransaction.ViewOperationModalEntry
import com.neoutils.finsight.core.ui.theme.Expense
import com.neoutils.finsight.core.ui.theme.Income
import com.neoutils.finsight.core.ui.util.stringUiText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun DashboardComponentContent(
    variant: DashboardComponentVariant,
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val navigationDispatcher = LocalNavigationDispatcher.current

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
                onOpenAccounts = { navigationDispatcher.dispatch(NavigationDestination.Accounts()) },
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.CreditCardsPager -> {
            DashboardCreditCardsSection(
                variant = variant,
                onOpenCreditCards = { navigationDispatcher.dispatch(NavigationDestination.CreditCards()) },
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
                onOpenRecurring = { navigationDispatcher.dispatch(NavigationDestination.Recurring) },
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
                onNavigate = { navigationDispatcher.dispatch(it) },
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
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
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
                        modalManager.show(confirmRecurringEntry.create(recurring, targetDate))
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
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
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
        component.operations.forEachIndexed { index, operation ->
            val isLastWithFade = component.hasMore && index == component.operations.lastIndex
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
                            operation.type.isAdjustment -> modalManager.show(viewAdjustmentEntry.create(operation))
                            else -> modalManager.show(viewOperationEntry.create(operation))
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
    onNavigate: (NavigationDestination) -> Unit,
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
                        onNavigate(type.destination)
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
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
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
                        openTransactions(Transaction.Type.INCOME, null)
                    }
                },
            )

            BalanceCard(
                balance = component.expense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Expense,
                onClick = {
                    if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                        openTransactions(Transaction.Type.EXPENSE, null)
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
    val navigationDispatcher = LocalNavigationDispatcher.current
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
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
                            modalManager.show(creditCardFormEntry.create())
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

                    CreditCardCard(
                        creditCard = creditCardUi.creditCard,
                        invoiceUi = creditCardUi.invoiceUi,
                        modifier = Modifier.fillMaxWidth(),
                        variant = CreditCardCardVariant.Dashboard(
                            onClick = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    navigationDispatcher.dispatch(
                                        NavigationDestination.CreditCards(creditCardId = creditCardUi.creditCard.id)
                                    )
                                }
                            },
                            onCloseInvoice = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(closeInvoiceEntry.create(it.id, it.closingDate))
                                    }
                                }
                            },
                            onPayInvoice = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(
                                            payInvoiceEntry.create(invoice = it.invoice, currentBillAmount = it.amount)
                                        )
                                    }
                                }
                            },
                            onAdvancePayment = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(
                                            advancePaymentEntry.create(invoice = it.invoice, currentBillAmount = it.amount)
                                        )
                                    }
                                }
                            },
                            onEditAmount = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(editInvoiceBalanceEntry.create(initialInvoice = it.invoice))
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
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
    val component = variant.component

    CategorySpendingCard(
        categorySpending = component.categorySpending,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onCategoryClick = { category ->
            if (variant is DashboardComponentVariant.SpendingByCategory.Viewing) {
                modalManager.show(viewCategoryEntry.create(category))
            }
        }
    )
}

@Composable
private fun DashboardIncomeByCategorySection(
    variant: DashboardComponentVariant.IncomeByCategory,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
    val component = variant.component

    CategorySpendingCard(
        categorySpending = component.categoryIncome,
        title = stringResource(Res.string.component_income_by_category),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onCategoryClick = { category ->
            if (variant is DashboardComponentVariant.IncomeByCategory.Viewing) {
                modalManager.show(viewCategoryEntry.create(category))
            }
        }
    )
}

@Composable
private fun DashboardBudgetsSection(
    variant: DashboardComponentVariant.Budgets,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
    val component = variant.component

    BudgetProgressCard(
        budgetProgress = component.budgetProgress,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onBudgetClick = { budget ->
            if (variant is DashboardComponentVariant.Budgets.Viewing) {
                modalManager.show(viewBudgetEntry.create(budget))
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
    action: QuickActionType,
    onOpen: (QuickActionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringUiText(action.title)

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
    val navigationDispatcher = LocalNavigationDispatcher.current
    val modalManager = LocalModalManager.current
    val accountFormEntry = koinInject<AccountFormModalEntry>()
    val creditCardFormEntry = koinInject<CreditCardFormModalEntry>()
    val confirmRecurringEntry = koinInject<ConfirmRecurringModalEntry>()
    val viewAdjustmentEntry = koinInject<ViewAdjustmentModalEntry>()
    val viewOperationEntry = koinInject<ViewOperationModalEntry>()
    val viewCategoryEntry = koinInject<ViewCategoryModalEntry>()
    val viewBudgetEntry = koinInject<ViewBudgetModalEntry>()
    val closeInvoiceEntry = koinInject<CloseInvoiceModalEntry>()
    val payInvoiceEntry = koinInject<PayInvoiceModalEntry>()
    val advancePaymentEntry = koinInject<AdvancePaymentModalEntry>()
    val editInvoiceBalanceEntry = koinInject<EditInvoiceBalanceModalEntry>()
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
                items = component.accounts.sortedByDescending { it.account.isDefault },
                key = { accountUi -> accountUi.account.id },
            ) { accountUi ->
                AccountCard(
                    account = accountUi.account,
                    variant = AccountCardVariant.Dashboard(
                        balance = accountUi.balance,
                        onClick = {
                            if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                                navigationDispatcher.dispatch(NavigationDestination.Accounts(accountId = accountUi.account.id))
                            }
                        },
                    ),
                )
            }

            item(key = "add_account") {
                DashboardAddAccountCard(
                    onClick = {
                        if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                            modalManager.show(accountFormEntry.create())
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
                text = stringResource(SharedUiRes.string.credit_cards_empty),
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
                Text(text = stringResource(SharedUiRes.string.credit_cards_create))
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
