@file:OptIn(
    FormatStringsInDatetimeFormats::class,
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
)

package com.neoutils.finsight.ui.screen.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.EmptyStateMessage
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.TransactionCard
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.SummaryCard
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor
import com.neoutils.finsight.ui.theme.InvoicePayment as PaymentColor
import com.neoutils.finsight.ui.theme.Transfer as TransferColor

@Composable
fun TransactionsScreen(
    categoryLabel: TransactionLabel? = null,
    target: TransactionTarget? = null,
    viewModel: TransactionsViewModel = koinViewModel {
        parametersOf(categoryLabel, target)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TransactionsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun TransactionsContent(
    uiState: TransactionsUiState,
    onAction: (TransactionsAction) -> Unit,
) {
    val detailController = LocalDetailPaneController.current
    val dateFormats = LocalDateFormats.current
    val navController = LocalNavController.current

    Scaffold(
        modifier = Modifier.testTag("screen_transactions"),
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // The month used to live in a top bar, which is where this inset came
                // from; the bar is gone, the inset still has to be honoured.
                .statusBarsPadding()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "summary_card"
            ) {
                SummaryCard(
                    onSeeRates = { navController.navigate(ExchangeRatesRoute) },
                    balanceOverview = uiState.balanceOverview,
                    selectedScope = uiState.selectedScope,
                    selectedYearMonth = uiState.selectedYearMonth,
                    onScopeSelected = { scope ->
                        onAction(TransactionsAction.SelectScope(scope))
                    },
                    onMonthSelected = { selected ->
                        onAction(TransactionsAction.SelectMonth(selected))
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    isCurrentMonth = uiState.isCurrentMonth,
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

            when (val listState = uiState.listState) {
                // Nothing has been read yet: the screen says nothing rather than
                // claiming an emptiness it cannot yet know about.
                ListState.Loading -> Unit

                ListState.EmptyLedger,
                is ListState.EmptyScope -> item(
                    key = "empty_state"
                ) {
                    // Centred inside a column narrower than the screen: on a desktop or a
                    // tablet, text running the full width would read as a paragraph rather
                    // than as a short notice.
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .animateItem(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TransactionsEmptyState(
                            listState = listState,
                            onAction = onAction,
                            modifier = Modifier
                                .widthIn(max = 400.dp)
                                .padding(
                                    horizontal = 24.dp,
                                    vertical = 48.dp
                                )
                        )
                    }
                }

                is ListState.Content -> listState.transactions.forEach { (date, transactions) ->
                    item(
                        key = "date_title_$date"
                    ) {
                        Text(
                            text = dateFormats.formatRelativeDate(date),
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
                                if (transactionUi.label == TransactionLabel.ADJUSTMENT) {
                                    detailController.show(ViewAdjustmentModal(transactionUi.id))
                                } else {
                                    detailController.show(ViewTransactionModal(transactionUi.id))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * What stands where the list would be. The two emptinesses read differently because the
 * ways out differ: with nothing recorded at all no filter can reveal anything, so the
 * text points at the add button the chrome already offers; with a cut that shows nothing,
 * loosening it is the way out — and only then, and only if a filter is actually narrowing,
 * is clearing worth offering.
 */
@Composable
private fun TransactionsEmptyState(
    listState: ListState,
    onAction: (TransactionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLedgerEmpty = listState is ListState.EmptyLedger

    EmptyStateMessage(
        icon = if (isLedgerEmpty) {
            Icons.AutoMirrored.Outlined.ReceiptLong
        } else {
            Icons.Outlined.FilterAltOff
        },
        title = stringResource(
            if (isLedgerEmpty) {
                Res.string.transactions_empty_title
            } else {
                Res.string.transactions_empty_filter_title
            }
        ),
        description = stringResource(
            if (isLedgerEmpty) {
                Res.string.transactions_empty_body
            } else {
                Res.string.transactions_empty_filter_body
            }
        ),
        modifier = modifier,
        action = if (listState is ListState.EmptyScope && listState.canClearFilters) {
            {
                Button(
                    onClick = { onAction(TransactionsAction.ClearFilters) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(Res.string.transactions_empty_filter_action))
                }
            }
        } else null,
    )
}

@Composable
private fun FiltersRow(
    uiState: TransactionsUiState,
    onAction: (TransactionsAction) -> Unit,
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
                    selectedSubject = uiState.selectedSubject,
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
                    selectedLabel = uiState.selectedLabel,
                    onAction = onAction
                )
            }
        }

        // The scope already decides between account and card, so the chip only has work
        // to do in the overall mode — offering it elsewhere would allow two controls to
        // contradict each other (scope = accounts, chip = card → an empty list).
        if (uiState.mustShowTargetFilter) {
            item(
                key = "target_filter"
            ) {
                Box {
                    TargetFilterChip(
                        selectedTarget = uiState.selectedTarget,
                        onAction = onAction
                    )
                }
            }
        }

        item(
            key = "recurring_filter"
        ) {
            Box {
                RecurringFilterChip(
                    enabled = uiState.showRecurringOnly,
                    onAction = onAction,
                )
            }
        }

        if (uiState.mustShowInstallmentFilter) {
            item(
                key = "installment_filter"
            ) {
                Box {
                    InstallmentFilterChip(
                        enabled = uiState.showInstallmentOnly,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    selectedSubject: SpendingSubject?,
    categories: List<Category>,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Only a category has a declared nature. Lending one to the unclassified value would
    // be inventing state it does not have, so it wears the plain selected colours.
    val chipColor = when (selectedSubject) {
        is SpendingSubject.Categorized -> when (selectedSubject.category.type) {
            Category.Type.INCOME -> IncomeColor
            Category.Type.EXPENSE -> ExpenseColor
        }

        SpendingSubject.Uncategorized, null -> null
    }

    val label = when (selectedSubject) {
        is SpendingSubject.Categorized -> selectedSubject.category.name
        // The same key the breakdown names this value with: one concept, one word.
        SpendingSubject.Uncategorized -> stringResource(Res.string.category_spending_uncategorized)
        null -> stringResource(Res.string.transactions_filter_category)
    }

    FilterChip(
        selected = selectedSubject != null,
        onClick = { expanded = true },
        label = { Text(label) },
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
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_category_all)) },
            onClick = {
                onAction(TransactionsAction.SelectSubject(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(TransactionsAction.SelectSubject(SpendingSubject.Categorized(category)))
                    expanded = false
                }
            )
        }

        // Last and set apart, as in the breakdown: whoever reads the list of categories
        // must not run into something that is not one in the middle of it.
        HorizontalDivider()

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.category_spending_uncategorized)) },
            onClick = {
                onAction(TransactionsAction.SelectSubject(SpendingSubject.Uncategorized))
                expanded = false
            }
        )
    }
}

/** The nature's name, shared by the chip and its dropdown so they cannot drift. */
@Composable
private fun labelName(label: TransactionLabel) = stringResource(
    when (label) {
        TransactionLabel.INCOME -> Res.string.transactions_filter_type_income
        TransactionLabel.EXPENSE -> Res.string.transactions_filter_type_expense
        TransactionLabel.TRANSFER -> Res.string.transactions_filter_type_transfer
        TransactionLabel.PAYMENT -> Res.string.transactions_filter_type_payment
        TransactionLabel.ADJUSTMENT -> Res.string.transactions_filter_type_adjustment
    }
)

@Composable
private fun TypeFilterChip(
    selectedLabel: TransactionLabel?,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        when (selectedLabel) {
            TransactionLabel.INCOME -> IncomeColor
            TransactionLabel.EXPENSE -> ExpenseColor
            TransactionLabel.TRANSFER -> TransferColor
            TransactionLabel.PAYMENT -> PaymentColor
            TransactionLabel.ADJUSTMENT -> AdjustmentColor

            null -> null
        }

    FilterChip(
        selected = selectedLabel != null,
        onClick = { expanded = true },
        label = {
            Text(
                selectedLabel
                    ?.let { labelName(it) }
                    ?: stringResource(Res.string.transactions_filter_type)
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
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_type_all)) },
            onClick = {
                onAction(TransactionsAction.SelectLabel(null))
                expanded = false
            }
        )

        TransactionLabel.entries.forEach { label ->
            DropdownMenuItem(
                text = { Text(labelName(label)) },
                onClick = {
                    onAction(TransactionsAction.SelectLabel(label))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun RecurringFilterChip(
    enabled: Boolean,
    onAction: (TransactionsAction) -> Unit,
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(TransactionsAction.ToggleRecurring(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_recurring))
        },
    )
}

@Composable
private fun InstallmentFilterChip(
    enabled: Boolean,
    onAction: (TransactionsAction) -> Unit,
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(TransactionsAction.ToggleInstallment(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_installment))
        },
    )
}

@Composable
private fun TargetFilterChip(
    selectedTarget: TransactionTarget?,
    onAction: (TransactionsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FilterChip(
        selected = selectedTarget != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedTarget) {
                    TransactionTarget.ACCOUNT -> stringResource(Res.string.transactions_filter_account)
                    TransactionTarget.CREDIT_CARD -> stringResource(Res.string.transactions_filter_credit_card)
                    null -> stringResource(Res.string.transactions_filter_account)
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_category_all)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(null))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_account)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(TransactionTarget.ACCOUNT))
                expanded = false
            }
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.transactions_filter_credit_card_label)) },
            onClick = {
                onAction(TransactionsAction.SelectTarget(TransactionTarget.CREDIT_CARD))
                expanded = false
            }
        )
    }
}
