@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)

package com.neoutils.finsight.ui.screen.accounts

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountModal
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceModal
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.accounts_advance_payments
import com.neoutils.finsight.resources.accounts_adjustments
import com.neoutils.finsight.resources.accounts_balance
import com.neoutils.finsight.resources.accounts_default
import com.neoutils.finsight.resources.accounts_delete
import com.neoutils.finsight.resources.accounts_edit
import com.neoutils.finsight.resources.accounts_expenses
import com.neoutils.finsight.resources.accounts_filter_category
import com.neoutils.finsight.resources.accounts_filter_category_all
import com.neoutils.finsight.resources.accounts_filter_type
import com.neoutils.finsight.resources.accounts_filter_type_adjustment
import com.neoutils.finsight.resources.accounts_filter_type_all
import com.neoutils.finsight.resources.accounts_filter_type_expense
import com.neoutils.finsight.resources.accounts_filter_type_income
import com.neoutils.finsight.resources.accounts_income
import com.neoutils.finsight.resources.accounts_initial_balance
import com.neoutils.finsight.resources.accounts_invoices
import com.neoutils.finsight.resources.accounts_title
import com.neoutils.finsight.resources.accounts_transfer
import com.neoutils.finsight.resources.transactions_filter_recurring
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.absoluteValue
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor

@Composable
fun AccountsScreen(
    initialAccountId: Long? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: AccountsViewModel = koinViewModel {
        parametersOf(initialAccountId)
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AccountsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun AccountsContent(
    uiState: AccountsUiState,
    onAction: (AccountsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.accounts_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                    actionIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    uiState.selectedMonth?.let { month ->
                        MonthSelector(
                            selectedMonth = month,
                            onMonthSelected = { selected ->
                                onAction(AccountsAction.SelectMonth(selected))
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    modalManager.show(AccountFormModal())
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "account_pager"
            ) {
                AccountPager(
                    accounts = uiState.accounts,
                    selectedIndex = uiState.selectedAccountIndex,
                    onSelectAccount = { index ->
                        onAction(AccountsAction.SelectAccount(index))
                    },
                    onEditBalance = { account ->
                        uiState.selectedMonth?.let { month ->
                            modalManager.show(
                                EditAccountBalanceModal(
                                    type = EditAccountBalanceModal.Type.FINAL,
                                    targetMonth = month,
                                    account = account,
                                )
                            )
                        }
                    },
                    onEditInitialBalance = { account ->
                        uiState.selectedMonth?.let { month ->
                            modalManager.show(
                                EditAccountBalanceModal(
                                    type = EditAccountBalanceModal.Type.INITIAL,
                                    targetMonth = month,
                                    account = account,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.accounts.getOrNull(uiState.selectedAccountIndex)?.let { selectedAccount ->
                item(
                    key = "account_actions"
                ) {
                    AccountActions(
                        accountUi = selectedAccount,
                        canTransfer = uiState.accounts.size > 1,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateContentSize()
                    )
                }
            }

            if (uiState.accounts.isNotEmpty()) {
                item(
                    key = "filters_row"
                ) {
                    FiltersRow(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }

            uiState.operations.forEach { (date, operations) ->
                item(
                    key = "date_title_$date"
                ) {
                    Text(
                        text = LocalDateFormats.current.formatRelativeDate(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 16.dp)
                            .animateItem()
                    )
                }

                items(
                    items = operations,
                    key = { it.id }
                ) { operation ->
                    OperationCard(
                        operation = operation,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (operation.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(operation))
                                }

                                else -> {
                                    modalManager.show(ViewOperationModal(operation))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountPager(
    accounts: List<AccountUi>,
    selectedIndex: Int,
    onSelectAccount: (Int) -> Unit,
    onEditBalance: (Account) -> Unit,
    onEditInitialBalance: (Account) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { accounts.size }
    )

    LaunchedEffect(Unit) {
        snapshotFlow {
            pagerState.currentPage
        }
            .distinctUntilChanged()
            .collect { page ->
                onSelectAccount(page)
            }
    }


    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp,
    ) { page ->
        AccountCard(
            accountUi = accounts[page],
            modifier = Modifier.fillMaxWidth(),
            onEditBalance = {
                onEditBalance(accounts[page].account)
            },
            onEditInitialBalance = {
                onEditInitialBalance(accounts[page].account)
            }
        )
    }
}

@Composable
private fun AccountCard(
    accountUi: AccountUi,
    modifier: Modifier = Modifier,
    onEditBalance: () -> Unit = {},
    onEditInitialBalance: () -> Unit = {}
) {
    val account = accountUi.account
    val initialBalance = accountUi.initialBalance
    val balance = accountUi.balance
    val income = accountUi.income
    val expense = accountUi.expense
    val adjustment = accountUi.adjustment
    val invoicePayment = accountUi.invoicePayment
    val advancePayment = accountUi.advancePayment

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )

                if (account.isDefault) {
                    Text(
                        text = stringResource(Res.string.accounts_default),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextLight1,
                    )
                }
            }

            AccountSummaryRow(
                label = stringResource(Res.string.accounts_initial_balance),
                amount = initialBalance,
                color = Color.White,
                signDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE,
                onEditClick = onEditInitialBalance
            )

            AccountSummaryRow(
                label = stringResource(Res.string.accounts_income),
                amount = income,
                color = Income,
                signDisplay = AccountSignDisplay.ALWAYS_POSITIVE
            )

            AccountSummaryRow(
                label = stringResource(Res.string.accounts_expenses),
                amount = expense,
                color = Expense,
                signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE
            )

            if (adjustment != 0.0) {
                AccountSummaryRow(
                    label = stringResource(Res.string.accounts_adjustments),
                    amount = adjustment,
                    color = Adjustment,
                    signDisplay = AccountSignDisplay.SHOW_ALWAYS
                )
            }

            if (invoicePayment != 0.0) {
                AccountSummaryRow(
                    label = stringResource(Res.string.accounts_invoices),
                    amount = invoicePayment,
                    color = InvoicePayment,
                    signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE
                )
            }

            if (advancePayment != 0.0) {
                AccountSummaryRow(
                    label = stringResource(Res.string.accounts_advance_payments),
                    amount = advancePayment,
                    color = InvoicePayment,
                    signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE
                )
            }

            HorizontalDivider()

            AccountSummaryRow(
                label = stringResource(Res.string.accounts_balance),
                amount = balance,
                color = colorScheme.onSurface,
                isTotal = true,
                onEditClick = onEditBalance,
                signDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE
            )
        }
    }
}

@Composable
private fun AccountSummaryRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    signDisplay: AccountSignDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null
) {
    val formatter = LocalCurrencyFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 18.sp else 16.sp,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTotal) colorScheme.onSurface else TextLight1
        )

        val formattedAmount = when (signDisplay) {
            AccountSignDisplay.ALWAYS_POSITIVE -> {
                "+${formatter.format(amount.absoluteValue)}"
            }

            AccountSignDisplay.ALWAYS_NEGATIVE -> {
                "-${formatter.format(amount.absoluteValue)}"
            }

            AccountSignDisplay.SHOW_ONLY_NEGATIVE -> formatter.format(amount)

            AccountSignDisplay.SHOW_ALWAYS -> formatter.formatWithSign(amount)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (onEditClick != null) {
                        Modifier.clickable { onEditClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (onEditClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ModeEdit,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = formattedAmount,
                fontSize = if (isTotal) 20.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun AccountActions(
    accountUi: AccountUi,
    canTransfer: Boolean,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val account = accountUi.account

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    modalManager.show(DeleteAccountModal(account))
                },
                enabled = !account.isDefault,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Expense,
                    disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = !account.isDefault).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (account.isDefault) {
                            colorScheme.onSurface.copy(alpha = 0.12f)
                        } else {
                            Expense.copy(alpha = 0.5f)
                        }
                    )
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.accounts_delete),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(
                onClick = {
                    modalManager.show(AccountFormModal(account))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Info
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Info.copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.accounts_edit),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

        }

        if (canTransfer) {
            OutlinedButton(
                onClick = {
                    modalManager.show(TransferBetweenAccountsModal(account))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(colorScheme.primary.copy(alpha = 0.5f))
                ),
                contentPadding = PaddingValues(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.accounts_transfer),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FiltersRow(
    uiState: AccountsUiState,
    onAction: (AccountsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(
            key = "category_filter"
        ) {
            Box {
                CategoryFilterChip(
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
                    onAction = onAction
                )
            }
        }

        item(
            key = "type_filter"
        ) {
            Box {
                TypeFilterChip(
                    selectedType = uiState.selectedType,
                    onAction = onAction
                )
            }
        }

        item(
            key = "recurring_filter"
        ) {
            Box {
                RecurringFilterChip(
                    enabled = uiState.showRecurringOnly,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMonthPickerExpanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val menuWidth = 320.dp
    val menuOffsetX = with(LocalDensity.current) {
        (anchorWidthPx.toDp() - menuWidth) / 2
    }

    Box(
        modifier = modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .onSizeChanged { anchorWidthPx = it.width }
                .clip(RoundedCornerShape(4.dp))
                .clickable { isMonthPickerExpanded = true }
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState = selectedMonth,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { month ->
                Text(
                    text = LocalDateFormats.current.yearMonth.format(month),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        MonthPickerDropdownMenu(
            expanded = isMonthPickerExpanded,
            selectedYearMonth = selectedMonth,
            onDismissRequest = { isMonthPickerExpanded = false },
            onMonthSelected = onMonthSelected,
            menuWidth = menuWidth,
            offset = DpOffset(x = menuOffsetX, y = 4.dp)
        )
    }
}

@Composable
private fun CategoryFilterChip(
    selectedCategory: Category?,
    categories: List<Category>,
    onAction: (AccountsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        selectedCategory?.let { category ->
            when (category.type) {
                Category.Type.INCOME -> IncomeColor
                Category.Type.EXPENSE -> ExpenseColor
            }
        }

    FilterChip(
        selected = selectedCategory != null,
        onClick = { expanded = true },
        label = { Text(selectedCategory?.name ?: stringResource(Res.string.accounts_filter_category)) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_category_all)) },
            onClick = {
                onAction(AccountsAction.SelectCategory(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(AccountsAction.SelectCategory(category))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: Transaction.Type?,
    onAction: (AccountsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor = selectedType?.let { type ->
        when (type) {
            Transaction.Type.INCOME -> IncomeColor
            Transaction.Type.EXPENSE -> ExpenseColor
            else -> null
        }
    }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.INCOME -> stringResource(Res.string.accounts_filter_type_income)
                    Transaction.Type.EXPENSE -> stringResource(Res.string.accounts_filter_type_expense)
                    Transaction.Type.ADJUSTMENT -> stringResource(Res.string.accounts_filter_type_adjustment)
                    null -> stringResource(Res.string.accounts_filter_type)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_all)) },
            onClick = {
                onAction(AccountsAction.SelectType(null))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_income)) },
            onClick = {
                onAction(AccountsAction.SelectType(Transaction.Type.INCOME))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_expense)) },
            onClick = {
                onAction(AccountsAction.SelectType(Transaction.Type.EXPENSE))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_adjustment)) },
            onClick = {
                onAction(AccountsAction.SelectType(Transaction.Type.ADJUSTMENT))
                expanded = false
            }
        )

    }
}

@Composable
private fun RecurringFilterChip(
    enabled: Boolean,
    onAction: (AccountsAction) -> Unit
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(AccountsAction.ToggleRecurring(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_recurring))
        },
    )
}

private enum class AccountSignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ONLY_NEGATIVE,
    SHOW_ALWAYS,
}
