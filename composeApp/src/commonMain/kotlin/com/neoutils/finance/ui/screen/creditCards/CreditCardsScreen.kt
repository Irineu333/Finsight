@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)

package com.neoutils.finance.ui.screen.creditCards

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.component.*
import com.neoutils.finance.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardModal
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceModal
import com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceModal
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionModal
import com.neoutils.finance.ui.theme.Expense
import com.neoutils.finance.ui.theme.Info
import com.neoutils.finance.util.DateFormats
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neoutils.finance.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finance.ui.theme.Expense as ExpenseColor
import com.neoutils.finance.ui.theme.Income as IncomeColor
import com.neoutils.finance.ui.theme.InvoicePayment as BillPaymentColor

private val formats = DateFormats()

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
                    Text(text = "Cartões de Crédito")
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
                    onEditInvoice = { invoiceId, amount ->
                        modalManager.show(
                            EditBalanceModal(
                                type = EditBalanceModal.Type.CREDIT_CARD,
                                currentBalance = amount,
                                invoiceId = invoiceId
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

            if (uiState.creditCards.isNotEmpty()) {
                item(
                    key = "filters_row"
                ) {
                    FiltersRow(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }

            uiState.transactions.forEach { (date, transactions) ->
                item(
                    key = "date_title_$date"
                ) {
                    Text(
                        text = formats.formatRelativeDate(date),
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
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        category = transaction.category,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = {
                            when (transaction.type) {
                                Transaction.Type.ADJUSTMENT -> {
                                    modalManager.show(ViewAdjustmentModal(transaction))
                                }

                                else -> {
                                    modalManager.show(ViewTransactionModal(transaction))
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
private fun CreditCardPager(
    creditCards: List<CreditCardUi>,
    selectedIndex: Int,
    onSelectCard: (Int) -> Unit,
    onCardClick: (CreditCardUi) -> Unit,
    onEditInvoice: (Long, Double) -> Unit,
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
                    text = "Excluir",
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
                    text = "Editar",
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
                        text = "Antecipar Pagamento",
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
                        text = "Fechar Fatura",
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
                        text = "Reabrir Fatura",
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
                        text = "Pagar Fatura",
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
    uiState: CreditCardsUiState,
    onAction: (CreditCardsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        label = { Text(selectedCategory?.name ?: "Categoria") },
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
            text = { Text("Todas") },
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
            Transaction.Type.ADVANCE_PAYMENT -> BillPaymentColor
            else -> null
        }

    FilterChip(
        selected = selectedType != null,
        onClick = { expanded = true },
        label = {
            Text(
                when (selectedType) {
                    Transaction.Type.EXPENSE -> "Despesa"
                    Transaction.Type.ADJUSTMENT -> "Ajuste"
                    Transaction.Type.ADVANCE_PAYMENT -> "Antecipação"
                    else -> "Tipo"
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
            text = { Text("Todos") },
            onClick = {
                onAction(CreditCardsAction.SelectType(null))
                expanded = false
            }
        )

        listOf(
            Transaction.Type.EXPENSE to "Despesa",
            Transaction.Type.ADJUSTMENT to "Ajuste",
            Transaction.Type.ADVANCE_PAYMENT to "Antecipação",
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
