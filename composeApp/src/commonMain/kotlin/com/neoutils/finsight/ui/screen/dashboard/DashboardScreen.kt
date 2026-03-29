@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalTime::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.safeOnDay
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.stringUiText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val navigationDispatcher = LocalNavigationDispatcher.current

    val updateTransition = updateTransition(targetState = uiState)

    Scaffold(
        topBar = {
            updateTransition.Crossfade(
                contentKey = { it::class },
            ) {
                when (it) {
                    is DashboardUiState.Editing -> {
                        DashboardEditToolbar(
                            onCancel = { viewModel.onAction(DashboardAction.CancelEdit) },
                            onConfirm = { viewModel.onAction(DashboardAction.ConfirmEdit) },
                        )
                    }

                    else -> {
                        TopAppBar(
                            title = {
                                Text(text = LocalDateFormats.current.yearMonth.format(uiState.yearMonth))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.background,
                            ),
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        updateTransition.Crossfade(
            contentKey = { it::class },
            modifier = Modifier.padding(paddingValues),
        ) { state ->
            when (state) {
                is DashboardUiState.Loading -> DashboardLoadingContent()
                is DashboardUiState.Viewing -> DashboardViewingContent(
                    state = state,
                    openTransactions = openTransactions,
                    onOpenQuickAction = { type ->
                        when (type) {
                            QuickActionType.BUDGETS -> navigationDispatcher.dispatch(NavigationDestination.Budgets)
                            QuickActionType.CATEGORIES -> navigationDispatcher.dispatch(NavigationDestination.Categories)
                            QuickActionType.CREDIT_CARDS -> navigationDispatcher.dispatch(NavigationDestination.CreditCards())
                            QuickActionType.ACCOUNTS -> navigationDispatcher.dispatch(NavigationDestination.Accounts())
                            QuickActionType.RECURRING -> navigationDispatcher.dispatch(NavigationDestination.Recurring)
                            QuickActionType.REPORTS -> navigationDispatcher.dispatch(NavigationDestination.ReportConfig)
                            QuickActionType.INSTALLMENTS -> navigationDispatcher.dispatch(NavigationDestination.Installments)
                            QuickActionType.SUPPORT -> navigationDispatcher.dispatch(NavigationDestination.Support)
                        }
                    },
                    modalManager = modalManager,
                    navigationDispatcher = navigationDispatcher,
                    onAction = viewModel::onAction,
                )

                is DashboardUiState.Editing -> DashboardEditingContent(
                    state = state,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@Composable
private fun DashboardLoadingContent() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun DashboardEditToolbar(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(Res.string.dashboard_edit_cancel))
            }
        },
        title = {
            Text(
                text = stringResource(Res.string.dashboard_edit_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.dashboard_edit_confirm))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.background,
        ),
    )
}

@Composable
private fun DashboardEditingContent(
    state: DashboardUiState.Editing,
    onAction: (DashboardAction) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    val reorderState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        onAction(DashboardAction.MoveComponent(from.index, to.index))
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 32.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.key }) { item ->
            ReorderableItem(reorderState, key = item.key) { isDragging ->
                DashboardEditItemWrapper(
                    item = item,
                    isDragging = isDragging,
                    onTap = {
                        modalManager.show(
                            DashboardComponentOptionsModal(item = item, onAction = onAction)
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DashboardEditItemWrapper(
    item: DashboardEditItem,
    isDragging: Boolean,
    onTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(if (isDragging) 8.dp else 0.dp, shape = RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .longPressDraggableHandle(
                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = stringUiText(item.title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DashboardViewingContent(
    state: DashboardUiState.Viewing,
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    onOpenQuickAction: (QuickActionType) -> Unit,
    modalManager: ModalManager,
    navigationDispatcher: NavigationDispatcher,
    onAction: (DashboardAction) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 32.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.components.forEach { component ->
            when (component) {
                is DashboardComponent.TotalBalance -> {
                    item(key = component.key) {
                        TotalBalanceCard(
                            balance = component.amount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.ConcreteBalanceStats -> {
                    item(key = component.key) {
                        DashboardConcreteBalanceSection(
                            component = component,
                            openTransactions = openTransactions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.PendingBalanceStats -> {
                    item(key = component.key) {
                        DashboardPendingBalanceSection(
                            component = component,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.AccountsOverview -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    item(key = component.key) {
                        DashboardAccountsRow(
                            accounts = component.accounts,
                            onOpenAccounts = { onOpenQuickAction(QuickActionType.ACCOUNTS) },
                            onAccountClick = { accountId ->
                                navigationDispatcher.dispatch(
                                    NavigationDestination.Accounts(accountId = accountId)
                                )
                            },
                            onAddAccount = {
                                modalManager.show(AccountFormModal())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.CreditCardsPager -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    item(key = component.key) {
                        DashboardCreditCardsSection(
                            component = component,
                            onOpenCreditCards = { onOpenQuickAction(QuickActionType.CREDIT_CARDS) },
                            navigationDispatcher = navigationDispatcher,
                            modalManager = modalManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.SpendingPager -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    item(key = component.key) {
                        DashboardSpendingSection(
                            component = component,
                            modalManager = modalManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }

                is DashboardComponent.PendingRecurring -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    item(key = "${component.key}_header") {
                        DashboardSectionHeader(
                            title = stringResource(Res.string.dashboard_pending_recurring),
                            onClick = { onOpenQuickAction(QuickActionType.RECURRING) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }

                    items(
                        items = component.recurringList,
                        key = { recurring -> "${component.key}_${recurring.id}" },
                    ) { recurring ->
                        PendingRecurringCard(
                            recurring = recurring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                            onClick = {
                                val targetDate = Clock.System.now()
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date.yearMonth
                                    .safeOnDay(recurring.dayOfMonth)

                                modalManager.show(ConfirmRecurringModal(recurring, targetDate))
                            },
                        )
                    }
                }

                is DashboardComponent.Recents -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    item(key = "${component.key}_header") {
                        DashboardSectionHeader(
                            title = stringResource(Res.string.dashboard_recents),
                            onClick = { openTransactions(null, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }

                    itemsIndexed(
                        items = component.operations,
                        key = { _, operation -> "${component.key}_${operation.id}" },
                    ) { index, operation ->
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
                                )
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
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
                            },
                        )
                    }
                }

                is DashboardComponent.QuickActions -> {

                    item { Spacer(Modifier.height(8.dp)) }

                    items(
                        items = component.actions,
                        key = { action ->
                            component.key + action.name
                        },
                    ) { action ->
                        DashboardQuickActionCard(
                            action = action,
                            onOpen = onOpenQuickAction,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardConcreteBalanceSection(
    component: DashboardComponent.ConcreteBalanceStats,
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BalanceCard(
                balance = component.income,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Income,
                onClick = { openTransactions(Transaction.Type.INCOME, null) },
            )

            BalanceCard(
                balance = component.expense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.Expense,
                onClick = { openTransactions(Transaction.Type.EXPENSE, null) },
            )
        }
    }
}

@Composable
private fun DashboardPendingBalanceSection(
    component: DashboardComponent.PendingBalanceStats,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (component.pendingIncome > 0.0) {
            BalanceCard(
                balance = component.pendingIncome,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.PendingIncome,
            )
        }

        if (component.pendingExpense > 0.0) {
            BalanceCard(
                balance = component.pendingExpense,
                modifier = Modifier.weight(1f),
                config = BalanceCardConfig.PendingExpense,
            )
        }
    }
}

@Composable
private fun DashboardCreditCardsSection(
    component: DashboardComponent.CreditCardsPager,
    onOpenCreditCards: () -> Unit,
    navigationDispatcher: NavigationDispatcher,
    modalManager: ModalManager,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        pageCount = { component.creditCards.size },
    )

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
            TextButton(onClick = onOpenCreditCards) {
                Text(text = stringResource(Res.string.dashboard_see_all))
            }
        }

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
                        navigationDispatcher.dispatch(
                            NavigationDestination.CreditCards(
                                creditCardId = creditCardUi.creditCard.id,
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
                                    currentBillAmount = it.amount,
                                )
                            )
                        }
                    },
                    onAdvancePayment = {
                        creditCardUi.invoiceUi?.let {
                            modalManager.show(
                                AdvancePaymentModal(
                                    invoice = it.invoice,
                                    currentBillAmount = it.amount,
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

@Composable
private fun DashboardSpendingSection(
    component: DashboardComponent.SpendingPager,
    modalManager: ModalManager,
    modifier: Modifier = Modifier,
) {
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
                    onCategoryClick = { modalManager.show(ViewCategoryModal(it)) },
                )

                SpendingPage.Budgets -> BudgetProgressCard(
                    budgetProgress = component.budgetProgress,
                    modifier = Modifier.fillMaxWidth(),
                    onBudgetClick = { modalManager.show(ViewBudgetModal(it)) },
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

/**
 * Intercepts long press at [PointerEventPass.Initial] so it fires even on components that have
 * their own tap/click handlers. Without this, child [clickable]/[combinedClickable] modifiers
 * running on [PointerEventPass.Main] compete with the outer detector and win, silently swallowing
 * the gesture before the long press threshold is reached.
 */
private fun Modifier.interceptLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var released = false
        var canceled = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (!change.pressed) {
                    released = true
                    break
                }

                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    canceled = true
                    break
                }

                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: run {
                    canceled = true
                    break
                }

                if (finalChange.isConsumed) {
                    canceled = true
                    break
                }
            }
        }
        if (!released && !canceled) onLongPress()
    }
}

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
                AccountCard(
                    account = accountUi.account,
                    variant = AccountCardVariant.Dashboard(
                        balance = accountUi.balance,
                        onClick = { onAccountClick(accountUi.account.id) },
                    ),
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

class DashboardComponentOptionsModal(
    private val item: DashboardEditItem,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val modalManager = LocalModalManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringUiText(item.title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.remove_component)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onAction(DashboardAction.RemoveComponent(item.key))
                    modalManager.dismiss()
                },
            )
        }
    }
}
