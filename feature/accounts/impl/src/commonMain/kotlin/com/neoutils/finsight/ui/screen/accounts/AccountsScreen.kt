@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)

package com.neoutils.finsight.ui.screen.accounts
import com.neoutils.finsight.ui.util.isWideWindow

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.AccountCard
import com.neoutils.finsight.ui.component.AccountCardVariant
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.model.AccountRetireOffer
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.navigation.ArchivedAccountsRoute
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.ui.component.TransactionCard
import com.neoutils.finsight.ui.modal.accountForm.AccountFormModal
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.ui.modal.archiveAccount.ArchiveAccountModal
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountModal
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceModal
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsModal
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.accounts_edit
import com.neoutils.finsight.resources.accounts_filter_category
import com.neoutils.finsight.resources.accounts_filter_category_all
import com.neoutils.finsight.resources.accounts_filter_type
import com.neoutils.finsight.resources.accounts_filter_type_adjustment
import com.neoutils.finsight.resources.accounts_filter_type_all
import com.neoutils.finsight.resources.accounts_filter_type_expense
import com.neoutils.finsight.resources.accounts_filter_type_income
import com.neoutils.finsight.resources.accounts_more_options_content_description
import com.neoutils.finsight.resources.accounts_title
import com.neoutils.finsight.resources.accounts_transfer
import com.neoutils.finsight.resources.accounts_view_archived
import com.neoutils.finsight.resources.retire_action_unavailable_default
import com.neoutils.finsight.resources.transactions_filter_recurring
import com.neoutils.finsight.ui.theme.Expense
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
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
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analytics.logScreenView("accounts")
    }

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
    val detailController = LocalDetailPaneController.current
    val transactionsEntry = koinInject<TransactionsEntry>()

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
                    if (!isWideWindow()) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    MonthSelector(
                        selectedMonth = uiState.selectedMonth,
                        onMonthSelected = { selected ->
                            onAction(AccountsAction.SelectMonth(selected))
                        }
                    )

                    // The MonthSelector already owns the actions slot, so archived
                    // accounts are reached from an overflow menu beside it — mirroring
                    // the credit cards screen's gesture (design D6).
                    val navController = LocalNavController.current
                    var expanded by remember { mutableStateOf(false) }

                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.accounts_more_options_content_description),
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.accounts_view_archived)) },
                            onClick = {
                                expanded = false
                                navController.navigate(ArchivedAccountsRoute)
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
            when (uiState) {
                is AccountsUiState.Loading -> {
                    item(
                        key = "account_pager_loading"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                is AccountsUiState.Content -> {
                    item(
                        key = "account_pager"
                    ) {
                        AccountPager(
                            accounts = uiState.accounts,
                            domainAccounts = uiState.domainAccounts,
                            selectedIndex = uiState.selectedAccountIndex,
                            onSelectAccount = { index ->
                                onAction(AccountsAction.SelectAccount(index))
                            },
                            onEditBalance = { account ->
                                modalManager.show(
                                    EditAccountBalanceModal(
                                        type = EditAccountBalanceModal.Type.FINAL,
                                        targetMonth = uiState.selectedMonth,
                                        account = account,
                                    )
                                )
                            },
                            onEditOpeningBalance = { account ->
                                modalManager.show(
                                    EditAccountBalanceModal(
                                        type = EditAccountBalanceModal.Type.INITIAL,
                                        targetMonth = uiState.selectedMonth,
                                        account = account,
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(
                        key = "account_actions"
                    ) {
                        AccountActions(
                            account = uiState.domainAccounts[uiState.selectedAccountIndex],
                            retireOffer = uiState.accounts[uiState.selectedAccountIndex].retireOffer,
                            canTransfer = uiState.accounts.size > 1,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .animateContentSize()
                        )
                    }

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
                uiState.transactions.forEach { (date, transactions) ->
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
                        items = transactions,
                        key = { it.id }
                    ) { transactionUi ->
                        TransactionCard(
                            transaction = transactionUi,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .animateItem(),
                            onClick = {
                                when (transactionUi.direction) {
                                    TransactionType.ADJUSTMENT -> {
                                        detailController.show(transactionsEntry.viewAdjustmentModal(transactionUi.id))
                                    }

                                    else -> {
                                        detailController.show(
                                            transactionsEntry.viewTransactionModal(
                                                transactionUi.id,
                                                uiState.selectedAccountId?.let { TransactionPerspective(it) },
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun AccountPager(
    accounts: List<AccountUi>,
    domainAccounts: List<Account>,
    selectedIndex: Int,
    onSelectAccount: (Int) -> Unit,
    onEditBalance: (Account) -> Unit,
    onEditOpeningBalance: (Account) -> Unit,
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
            iconKey = domainAccounts[page].iconKey,
            name = domainAccounts[page].name,
            isDefault = domainAccounts[page].isDefault,
            variant = AccountCardVariant.Detail(
                accountUi = accounts[page],
                onEditBalance = { onEditBalance(domainAccounts[page]) },
                onEditOpeningBalance = { onEditOpeningBalance(domainAccounts[page]) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccountActions(
    account: Account,
    retireOffer: AccountRetireOffer,
    canTransfer: Boolean,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current

    val editButton = @Composable { buttonModifier: Modifier ->
        OutlinedActionButton(
            label = stringResource(Res.string.accounts_edit),
            icon = Icons.Default.Edit,
            contentColor = Info,
            onClick = {
                modalManager.show(AccountFormModal(account))
            },
            modifier = buttonModifier,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The default account offers no retire at all — the third case owned by
        // `accountRetireOfferOf`. The domain refuses it too (CANNOT_ARCHIVE_DEFAULT);
        // here we replace the button with guidance to elect another default first,
        // and the edit button takes the full width the retire button vacated.
        when (retireOffer) {
            is AccountRetireOffer.Retire -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Which of the two is offered is a presentation rule (`retireActionOf`);
                // which one actually happens is the ledger's, in ArchiveAccountUseCase.
                OutlinedActionButton(
                    label = stringResource(retireOffer.action.label),
                    icon = retireOffer.action.icon,
                    contentColor = Expense,
                    onClick = {
                        modalManager.show(
                            when (retireOffer.action) {
                                RetireAction.DELETE -> DeleteAccountModal(account)
                                RetireAction.ARCHIVE -> ArchiveAccountModal(account)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                )

                editButton(Modifier.weight(1f))
            }

            AccountRetireOffer.UnavailableDefault -> {
                editButton(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(Res.string.retire_action_unavailable_default),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (canTransfer) {
            OutlinedActionButton(
                label = stringResource(Res.string.accounts_transfer),
                icon = Icons.Default.SwapHoriz,
                contentColor = colorScheme.primary,
                onClick = {
                    modalManager.show(TransferBetweenAccountsModal(account))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FiltersRow(
    uiState: AccountsUiState.Content,
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
                    color = colorScheme.onBackground
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colorScheme.onBackground,
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

    val chipColor = selectedCategory?.displayColor

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
    selectedType: TransactionType?,
    onAction: (AccountsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor = selectedType?.let { type ->
        when (type) {
            TransactionType.INCOME -> IncomeColor
            TransactionType.EXPENSE -> ExpenseColor
            TransactionType.ADJUSTMENT -> AdjustmentColor
        }
    }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    TransactionType.INCOME -> stringResource(Res.string.accounts_filter_type_income)
                    TransactionType.EXPENSE -> stringResource(Res.string.accounts_filter_type_expense)
                    TransactionType.ADJUSTMENT -> stringResource(Res.string.accounts_filter_type_adjustment)
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
                onAction(AccountsAction.SelectType(TransactionType.INCOME))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_expense)) },
            onClick = {
                onAction(AccountsAction.SelectType(TransactionType.EXPENSE))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.accounts_filter_type_adjustment)) },
            onClick = {
                onAction(AccountsAction.SelectType(TransactionType.ADJUSTMENT))
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
