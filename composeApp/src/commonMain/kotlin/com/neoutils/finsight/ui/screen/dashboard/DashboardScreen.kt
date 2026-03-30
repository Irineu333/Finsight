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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
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

private sealed interface EditListEntry {
    data class Component(val item: DashboardEditItem, val isActive: Boolean) : EditListEntry
    data object SectionHeader : EditListEntry
    data object AvailablePlaceholder : EditListEntry
}

private val EditListEntry.entryKey: String
    get() = when (this) {
        is EditListEntry.Component -> item.key
        EditListEntry.SectionHeader -> EDIT_SECTION_HEADER_KEY
        EditListEntry.AvailablePlaceholder -> EDIT_AVAILABLE_PLACEHOLDER_KEY
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
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (fromKey == EDIT_SECTION_HEADER_KEY || fromKey == EDIT_AVAILABLE_PLACEHOLDER_KEY) return@rememberReorderableLazyListState
        onAction(DashboardAction.MoveComponent(fromKey, toKey))
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val listEntries = remember(state.items, state.availableItems) {
        buildList {
            state.items.forEach { add(EditListEntry.Component(it, isActive = true)) }
            add(EditListEntry.SectionHeader)
            if (state.availableItems.isEmpty()) {
                add(EditListEntry.AvailablePlaceholder)
            } else {
                state.availableItems.forEach {
                    add(EditListEntry.Component(it, isActive = false))
                }
            }
        }
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
        items(listEntries, key = { it.entryKey }) { entry ->
            when (entry) {
                is EditListEntry.Component -> {
                    ReorderableItem(reorderState, key = entry.item.key) {
                        DashboardEditItemWrapper(
                            item = entry.item,
                            onTap = {
                                if (entry.isActive) {
                                    modalManager.show(
                                        DashboardComponentOptionsModal(
                                            item = entry.item,
                                            accounts = state.accounts,
                                            creditCards = state.creditCards,
                                            onAction = onAction,
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.alpha(alpha = if (entry.isActive) 1f else 0.6f)
                        )
                    }
                }

                EditListEntry.SectionHeader -> {
                    ReorderableItem(reorderState, key = "section_header") {
                        Text(
                            text = stringResource(Res.string.dashboard_edit_available_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                EditListEntry.AvailablePlaceholder -> {
                    ReorderableItem(reorderState, key = "available_placeholder") {
                        DashboardAvailablePlaceholder(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DashboardEditItemWrapper(
    item: DashboardEditItem,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxWidth()) {
        DashboardComponentContent(
            variant = item.preview,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onTap)
                .longPressDraggableHandle(
                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                ),
        )
    }
}

@Composable
private fun DashboardAvailablePlaceholder(modifier: Modifier = Modifier) {
    val borderColor = colorScheme.outlineVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(80.dp)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            },
    ) {
        Text(
            text = stringResource(Res.string.dashboard_edit_available_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun DashboardViewingContent(
    state: DashboardUiState.Viewing,
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    onOpenQuickAction: (QuickActionType) -> Unit,

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
            val config = state.configByKey[component.key] ?: emptyMap()
            val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"
            val variant = component.toViewingVariant(
                openTransactions = openTransactions,
                onOpenQuickAction = onOpenQuickAction,
            )
            item(key = component.key) {
                Column(modifier = Modifier.animateItem()) {
                    if (topSpacing) Spacer(modifier = Modifier.height(16.dp))
                    DashboardComponentContent(
                        variant = variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardComponentContent(
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
    variant: DashboardComponentVariant.CreditCardsPager,
    modifier: Modifier = Modifier,
) {
    val navigationDispatcher = LocalNavigationDispatcher.current
    val modalManager = LocalModalManager.current
    val component = variant.component

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

/**
 * Intercepts long press at [PointerEventPass.Initial] so it fires even on components that have
 * their own tap/click handlers. Without this, child [clickable]/[combinedClickable] modifiers
 * running on [PointerEventPass.Main] compete with the outer detector and win, silently swallowing
 * the gesture before the long press threshold is reached. Once the long press wins, it consumes
 * the remaining pointer events until release so the inner tap action cannot complete on pointer up.
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
        if (released || canceled) {
            return@awaitEachGesture
        }

        onLongPress()

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
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
    private val accounts: List<Account>,
    private val creditCards: List<CreditCard>,
    private val onAction: (DashboardAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        var config by remember { mutableStateOf(item.config) }

        fun updateConfig(newConfig: Map<String, String>) {
            config = newConfig
            onAction(DashboardAction.UpdateComponentConfig(item.key, newConfig))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringUiText(item.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"
            ListItem(
                headlineContent = { Text(stringResource(Res.string.component_config_top_spacing)) },
                leadingContent = { Icon(Icons.Rounded.SpaceBar, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = topSpacing,
                        onCheckedChange = { enabled ->
                            updateConfig(config.toMutableMap().apply {
                                put(DashboardComponentConfig.TOP_SPACING, enabled.toString())
                            })
                        },
                    )
                },
            )

            when (item.key) {
                DashboardComponent.AccountsOverview.KEY -> {
                    HorizontalDivider()
                    AccountsOverviewConfigContent(
                        accounts = accounts,
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponent.CreditCardsPager.KEY -> {
                    HorizontalDivider()
                    CreditCardsPagerConfigContent(
                        creditCards = creditCards,
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponent.SpendingPager.KEY -> {
                    HorizontalDivider()
                    SpendingPagerConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponent.PendingRecurring.KEY -> {
                    HorizontalDivider()
                    PendingRecurringConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponent.Recents.KEY -> {
                    HorizontalDivider()
                    RecentsConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }

                DashboardComponent.QuickActions.KEY -> {
                    HorizontalDivider()
                    QuickActionsConfigContent(
                        config = config,
                        onConfigChange = ::updateConfig,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsOverviewConfigContent(
    accounts: List<Account>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val excludedIds = config[AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    accounts.forEach { account ->
        val included = account.id !in excludedIds
        ListItem(
            headlineContent = { Text(account.name) },
            trailingContent = {
                Switch(
                    checked = included,
                    onCheckedChange = { checked ->
                        val newExcluded = if (checked) excludedIds - account.id else excludedIds + account.id
                        onConfigChange(config.toMutableMap().apply {
                            put(AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS, newExcluded.joinToString(","))
                        })
                    },
                )
            },
        )
    }
}

@Composable
private fun CreditCardsPagerConfigContent(
    creditCards: List<CreditCard>,
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val excludedIds = config[CreditCardsPagerConfig.EXCLUDED_CARD_IDS]
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() }?.toSet()
        ?: emptySet()

    creditCards.forEach { card ->
        val included = card.id !in excludedIds
        ListItem(
            headlineContent = { Text(card.name) },
            trailingContent = {
                Switch(
                    checked = included,
                    onCheckedChange = { checked ->
                        val newExcluded = if (checked) excludedIds - card.id else excludedIds + card.id
                        onConfigChange(config.toMutableMap().apply {
                            put(CreditCardsPagerConfig.EXCLUDED_CARD_IDS, newExcluded.joinToString(","))
                        })
                    },
                )
            },
        )
    }
}

@Composable
private fun SpendingPagerConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(3, 5, 10, -1)
    val current = config[SpendingPagerConfig.MAX_CATEGORIES]?.toIntOrNull() ?: -1

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.component_config_max_categories),
            style = MaterialTheme.typography.bodyMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = current == value,
                    onClick = {
                        onConfigChange(config.toMutableMap().apply {
                            put(SpendingPagerConfig.MAX_CATEGORIES, value.toString())
                        })
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                ) {
                    Text(
                        text = if (value == -1) stringResource(Res.string.component_config_all) else value.toString(),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRecurringConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(7, 14, 30)
    val current = config[PendingRecurringConfig.DAYS_AHEAD]?.toIntOrNull()
        ?: PendingRecurringConfig.DEFAULT_DAYS_AHEAD

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.component_config_days_ahead),
            style = MaterialTheme.typography.bodyMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = current == value,
                    onClick = {
                        onConfigChange(config.toMutableMap().apply {
                            put(PendingRecurringConfig.DAYS_AHEAD, value.toString())
                        })
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                ) {
                    Text(text = "$value", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RecentsConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val options = listOf(4, 6, 8, 10)
    val current = config[RecentsConfig.COUNT]?.toIntOrNull() ?: RecentsConfig.DEFAULT_COUNT

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.component_config_count),
            style = MaterialTheme.typography.bodyMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = current == value,
                    onClick = {
                        onConfigChange(config.toMutableMap().apply {
                            put(RecentsConfig.COUNT, value.toString())
                        })
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                ) {
                    Text(text = "$value", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickActionsConfigContent(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
) {
    val hiddenActions = config[QuickActionsConfig.HIDDEN_ACTIONS]
        ?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    val visibleCount = QuickActionType.entries.count { it.name !in hiddenActions }

    QuickActionType.entries.forEach { action ->
        val isVisible = action.name !in hiddenActions
        ListItem(
            headlineContent = { Text(stringUiText(action.title)) },
            trailingContent = {
                Switch(
                    checked = isVisible,
                    enabled = !isVisible || visibleCount > 1,
                    onCheckedChange = { checked ->
                        val newHidden = if (checked) hiddenActions - action.name else hiddenActions + action.name
                        onConfigChange(config.toMutableMap().apply {
                            put(QuickActionsConfig.HIDDEN_ACTIONS, newHidden.joinToString(","))
                        })
                    },
                )
            },
        )
    }
}
