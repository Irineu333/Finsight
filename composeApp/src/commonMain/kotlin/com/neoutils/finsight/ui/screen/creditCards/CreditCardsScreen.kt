@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.neoutils.finsight.ui.screen.creditCards

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor
import com.neoutils.finsight.ui.theme.InvoicePayment as BillPaymentColor

@Composable
fun CreditCardsScreen(
    initialCreditCardId: Long? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: CreditCardsViewModel = koinViewModel {
        parametersOf(initialCreditCardId)
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CreditCardsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun CreditCardsContent(
    uiState: CreditCardsUiState,
    onAction: (CreditCardsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val modalManager = LocalModalManager.current
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.credit_cards_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is CreditCardsUiState.Content) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(CreditCardFormModal())
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        when (uiState) {
            CreditCardsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            CreditCardsUiState.Empty -> {
                EmptyCreditCardsState(
                    onCreateCreditCard = { modalManager.show(CreditCardFormModal()) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            is CreditCardsUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(
                        key = "credit_card_pager"
                    ) {
                        CreditCardPager(
                            creditCards = uiState.creditCards,
                            selectedIndex = uiState.selectedCardIndex,
                            onSelectCard = { index ->
                                onAction(CreditCardsAction.SelectCard(index))
                            },
                            onCardClick = { creditCardUi ->
                                navigator.navigate(
                                    NavigationAction.InvoiceTransactions(creditCardUi.creditCard.id)
                                )
                            },
                            onEditInvoice = { invoice ->
                                modalManager.show(
                                    EditInvoiceBalanceModal(
                                        initialInvoice = invoice
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    uiState.creditCards.getOrNull(uiState.selectedCardIndex)?.let { selectedCard ->
                        item(
                            key = "card_actions"
                        ) {
                            CardActions(
                                creditCardUi = selectedCard,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .animateContentSize()
                            )
                        }
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
    }
}

@Composable
private fun EmptyCreditCardsState(
    onCreateCreditCard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.credit_cards_empty),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateCreditCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.credit_cards_create))
            }
        }
    }
}

@Composable
private fun CreditCardPager(
    creditCards: List<CreditCardUi>,
    selectedIndex: Int,
    onSelectCard: (Int) -> Unit,
    onCardClick: (CreditCardUi) -> Unit,
    onEditInvoice: (invoice: Invoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { creditCards.size }
    )

    LaunchedEffect(Unit) {
        snapshotFlow {
            pagerState.currentPage
        }.collect {
            if (pagerState.currentPage != selectedIndex) {
                onSelectCard(pagerState.currentPage)
            }
        }
    }

//    LaunchedEffect(selectedIndex) {
//        if (pagerState.currentPage != selectedIndex) {
//            pagerState.scrollToPage(selectedIndex)
//        }
//    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp,
    ) { page ->
        CreditCardUI(
            ui = creditCards[page],
            modifier = Modifier.fillMaxWidth(),
            onClick = { onCardClick(creditCards[page]) },
            onEditInvoice = onEditInvoice,
        )
    }
}

@Composable
private fun CardActions(
    creditCardUi: CreditCardUi,
    modifier: Modifier = Modifier,
) {
    val modalManager = LocalModalManager.current
    val creditCard = creditCardUi.creditCard
    val invoiceUi = creditCardUi.invoiceUi

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
                    modalManager.show(DeleteCreditCardModal(creditCard))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Expense
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Expense.copy(alpha = 0.5f))
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
                    text = stringResource(Res.string.credit_cards_delete),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(
                onClick = {
                    modalManager.show(CreditCardFormModal(creditCard))
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
                    text = stringResource(Res.string.credit_cards_edit),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        invoiceUi?.let { invoice ->
            if (invoice.status.isOpen) {
                OutlinedButton(
                    onClick = {
                        modalManager.show(
                            AdvancePaymentModal(
                                invoice = invoice.invoice,
                                currentBillAmount = invoice.amount,
                            )
                        )
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
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.credit_cards_advance_payment),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (invoice.isClosable) {
                OutlinedButton(
                    onClick = {
                        modalManager.show(CloseInvoiceModal(invoice.id, invoice.closingDate))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFA726)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFA726).copy(alpha = 0.5f))
                    ),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.credit_cards_close_invoice),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (invoice.status.isClosed) {
                OutlinedButton(
                    onClick = {
                        modalManager.show(ReopenInvoiceModal(invoice.id))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFA726)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFA726).copy(alpha = 0.5f))
                    ),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.credit_cards_reopen_invoice),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        modalManager.show(
                            PayInvoiceModal(
                                invoice = invoice.invoice,
                                currentBillAmount = invoice.amount
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.credit_cards_pay_invoice),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersRow(
    uiState: CreditCardsUiState.Content,
    onAction: (CreditCardsAction) -> Unit,
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
private fun CategoryFilterChip(
    selectedCategory: Category?,
    categories: List<Category>,
    onAction: (CreditCardsAction) -> Unit
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
        label = { Text(selectedCategory?.name ?: stringResource(Res.string.credit_cards_filter_category)) },
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
            text = { Text(stringResource(Res.string.credit_cards_filter_category_all)) },
            onClick = {
                onAction(CreditCardsAction.SelectCategory(null))
                expanded = false
            }
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(CreditCardsAction.SelectCategory(category))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: Transaction.Type?,
    onAction: (CreditCardsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor =
        when (selectedType) {
            Transaction.Type.EXPENSE -> ExpenseColor
            Transaction.Type.ADJUSTMENT -> AdjustmentColor
            Transaction.Type.INCOME -> BillPaymentColor
            else -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.EXPENSE -> stringResource(Res.string.credit_cards_filter_type_expense)
                    Transaction.Type.ADJUSTMENT -> stringResource(Res.string.credit_cards_filter_type_adjustment)
                    Transaction.Type.INCOME -> stringResource(Res.string.credit_cards_filter_type_payment)
                    else -> stringResource(Res.string.credit_cards_filter_type)
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
        onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.credit_cards_filter_type_all)) },
            onClick = {
                onAction(CreditCardsAction.SelectType(null))
                expanded = false
            }
        )

        listOf(
            Transaction.Type.EXPENSE to stringResource(Res.string.credit_cards_filter_type_expense),
            Transaction.Type.ADJUSTMENT to stringResource(Res.string.credit_cards_filter_type_adjustment),
            Transaction.Type.INCOME to stringResource(Res.string.credit_cards_filter_type_payment),
        ).forEach { (type, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onAction(CreditCardsAction.SelectType(type))
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun RecurringFilterChip(
    enabled: Boolean,
    onAction: (CreditCardsAction) -> Unit
) {
    FilterChip(
        selected = enabled,
        onClick = { onAction(CreditCardsAction.ToggleRecurring(!enabled)) },
        label = {
            Text(stringResource(Res.string.transactions_filter_recurring))
        },
    )
}
