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
import com.neoutils.finsight.domain.model.Transaction
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
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalNavigationDispatcher
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.stringUiText
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
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.CreditCardsPager -> {
            DashboardCreditCardsSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.SpendingPager -> {
            DashboardSpendingSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.PendingRecurring -> {
            DashboardPendingRecurringSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.Recents -> {
            DashboardRecentsSection(
                variant = variant,
                modifier = modifier,
            )
        }

        is DashboardComponentVariant.QuickActions -> {
            DashboardQuickActionsSection(
                variant = variant,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DashboardPendingRecurringSection(
    variant: DashboardComponentVariant.PendingRecurring,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val component = variant.component

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardSectionHeader(
            title = stringResource(Res.string.dashboard_pending_recurring),
            onClick = {
                if (variant is DashboardComponentVariant.PendingRecurring.Viewing) {
                    variant.onOpenQuickAction(QuickActionType.RECURRING)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        component.recurringList.forEach { recurring ->
            PendingRecurringCard(
                recurring = recurring,
                onClick = {
                    if (variant is DashboardComponentVariant.PendingRecurring.Viewing) {
                        val targetDate = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date.yearMonth
                            .safeOnDay(recurring.dayOfMonth)
                        modalManager.show(ConfirmRecurringModal(recurring, targetDate))
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
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val component = variant.component

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardSectionHeader(
            title = stringResource(Res.string.dashboard_recents),
            onClick = {
                if (variant is DashboardComponentVariant.Recents.Viewing) {
                    variant.openTransactions(null, null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
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
                            isLastWithFade -> variant.openTransactions(null, null)
                            operation.type.isAdjustment -> modalManager.show(ViewAdjustmentModal(operation))
                            else -> modalManager.show(ViewOperationModal(operation))
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
    modifier: Modifier = Modifier,
) {
    val component = variant.component

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        component.actions.forEach { action ->
            DashboardQuickActionCard(
                action = action,
                onOpen = { type ->
                    if (variant is DashboardComponentVariant.QuickActions.Viewing) {
                        variant.onOpenQuickAction(type)
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
                        variant.openTransactions(Transaction.Type.INCOME, null)
                    }
                },
            )

            BalanceCard(
                balance = component.expense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Expense,
                onClick = {
                    if (variant is DashboardComponentVariant.ConcreteBalanceStats.Viewing) {
                        variant.openTransactions(Transaction.Type.EXPENSE, null)
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
    modifier: Modifier = Modifier,
) {
    val navigationDispatcher = LocalNavigationDispatcher.current
    val modalManager = LocalModalManager.current
    val component = variant.component

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
                text = stringResource(Res.string.dashboard_credit_cards),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = {
                    if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                        variant.onOpenQuickAction(QuickActionType.CREDIT_CARDS)
                    }
                },
            ) {
                Text(text = stringResource(Res.string.dashboard_see_all))
            }
        }

        when (component) {
            DashboardComponent.CreditCardsPager.Empty -> {
                DashboardCreditCardsEmptyCard(
                    onCreateCard = {
                        if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                            modalManager.show(CreditCardFormModal())
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
                                        modalManager.show(CloseInvoiceModal(it.id, it.closingDate))
                                    }
                                }
                            },
                            onPayInvoice = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(
                                            PayInvoiceModal(invoice = it.invoice, currentBillAmount = it.amount)
                                        )
                                    }
                                }
                            },
                            onAdvancePayment = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(
                                            AdvancePaymentModal(invoice = it.invoice, currentBillAmount = it.amount)
                                        )
                                    }
                                }
                            },
                            onEditAmount = {
                                if (variant is DashboardComponentVariant.CreditCardsPager.Viewing) {
                                    creditCardUi.invoiceUi?.let {
                                        modalManager.show(EditInvoiceBalanceModal(initialInvoice = it.invoice))
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
private fun DashboardSpendingSection(
    variant: DashboardComponentVariant.SpendingPager,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val component = variant.component

    val pages = buildList {
        if (component.budgetProgress.isNotEmpty()) add(SpendingPage.Budgets)
        if (component.categorySpending.isNotEmpty()) add(SpendingPage.Categories)
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (pages[page]) {
                SpendingPage.Categories -> CategorySpendingCard(
                    categorySpending = component.categorySpending,
                    modifier = Modifier.fillMaxWidth(),
                    onCategoryClick = { category ->
                        if (variant is DashboardComponentVariant.SpendingPager.Viewing) {
                            modalManager.show(ViewCategoryModal(category))
                        }
                    },
                )

                SpendingPage.Budgets -> BudgetProgressCard(
                    budgetProgress = component.budgetProgress,
                    modifier = Modifier.fillMaxWidth(),
                    onBudgetClick = { budget ->
                        if (variant is DashboardComponentVariant.SpendingPager.Viewing) {
                            modalManager.show(ViewBudgetModal(budget))
                        }
                    },
                )
            }
        }

        if (pages.size > 1) {
            PageIndicator(
                count = pages.size,
                current = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onClick) {
            Text(
                text = stringResource(Res.string.dashboard_see_all),
            )
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
    modifier: Modifier = Modifier,
) {
    val navigationDispatcher = LocalNavigationDispatcher.current
    val modalManager = LocalModalManager.current
    val component = variant.component

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
            TextButton(
                onClick = {
                    if (variant is DashboardComponentVariant.AccountsOverview.Viewing) {
                        variant.onOpenQuickAction(QuickActionType.ACCOUNTS)
                    }
                },
            ) {
                Text(text = stringResource(Res.string.dashboard_see_all))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            modalManager.show(AccountFormModal())
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
                shape = RoundedCornerShape(12.dp),
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
