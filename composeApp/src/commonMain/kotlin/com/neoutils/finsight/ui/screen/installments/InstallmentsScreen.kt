@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.neoutils.finsight.ui.screen.installments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextDecoration
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OperationCard
import com.neoutils.finsight.ui.modal.addInstallment.AddInstallmentModal
import com.neoutils.finsight.ui.modal.deleteInstallment.DeleteInstallmentModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.theme.Expense as ExpenseColor
import com.neoutils.finsight.ui.theme.Income as IncomeColor
import com.neoutils.finsight.ui.theme.Adjustment as AdjustmentColor
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Info
import com.neoutils.finsight.ui.theme.Warning
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InstallmentsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: InstallmentsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InstallmentsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun InstallmentsContent(
    uiState: InstallmentsUiState,
    onAction: (InstallmentsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Parcelamentos")
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
                    var menuExpanded by remember { mutableStateOf(false) }

                    Box {
                        CompositionLocalProvider(
                            LocalContentColor provides colorScheme.onBackground,
                            LocalTextStyle provides MaterialTheme.typography.labelLarge,
                        ) {
                            TextButton(
                                onClick = { menuExpanded = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.Unspecified,
                                ),
                            ) {
                                Text(
                                    text = when (uiState.selectedFilter) {
                                        InstallmentFilter.ACTIVE -> "Ativos"
                                        InstallmentFilter.COMPLETED -> "Concluídos"
                                        InstallmentFilter.ALL -> "Todos"
                                    },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            listOf(
                                InstallmentFilter.ACTIVE to "Ativos",
                                InstallmentFilter.COMPLETED to "Concluídos",
                                InstallmentFilter.ALL to "Todos",
                            ).forEach { (filter, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = if (uiState.selectedFilter == filter) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onAction(InstallmentsAction.SelectFilter(filter))
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.installments.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(AddInstallmentModal())
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { paddingValues ->
        if (uiState.installments.isEmpty()) {
            EmptyInstallmentsState(
                onCreateInstallment = { modalManager.show(AddInstallmentModal()) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "installments_pager") {
                    InstallmentPager(
                        installments = uiState.installments,
                        selectedIndex = uiState.selectedInstallmentIndex,
                        onSelectInstallment = { index ->
                            onAction(InstallmentsAction.SelectInstallment(index))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.selectedInstallment?.let { selected ->
                    if (selected.isDeletable) {
                        item(key = "delete_action") {
                            OutlinedButton(
                                onClick = {
                                    modalManager.show(
                                        DeleteInstallmentModal(
                                            installment = selected.installment,
                                            operations = selected.operations,
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .animateItem(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.error,
                                ),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        colorScheme.error.copy(alpha = 0.5f)
                                    )
                                ),
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Excluir Parcelamento",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                item(key = "filters_row") {
                    FiltersRow(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                    )
                }

                items(
                    items = uiState.filteredOperations,
                    key = Operation::id,
                ) { operation ->
                    OperationCard(
                        operation = operation,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .animateItem(),
                        amountDecoration = when (operation.targetInvoice?.status) {

                            Invoice.Status.PAID,
                            Invoice.Status.RETROACTIVE -> TextDecoration.LineThrough

                            else -> TextDecoration.None
                        },
                        onClick = {
                            when (operation.type) {
                                Transaction.Type.ADJUSTMENT -> {
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
        }
    }
}

@Composable
private fun EmptyInstallmentsState(
    onCreateInstallment: () -> Unit,
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
                text = "Sem parcelamentos",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateInstallment,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Criar parcelamento")
            }
        }
    }
}

@Composable
private fun InstallmentPager(
    installments: List<InstallmentWithOperationsUi>,
    selectedIndex: Int,
    onSelectInstallment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { installments.size },
    )

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onSelectInstallment(page)
            }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in installments.indices && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 8.dp,
    ) { page ->
        InstallmentSummaryCard(
            ui = installments[page],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InstallmentSummaryCard(
    ui: InstallmentWithOperationsUi,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ui.category != null) {
                        CategoryIconBox(
                            category = ui.category,
                            contentPadding = PaddingValues(8.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = ui.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        if (ui.categoryName != null) {
                            Text(
                                text = ui.categoryName,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                StatusBadge(isActive = ui.isActive)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Valor Total",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ui.installment.totalAmount.toMoneyFormat(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Parcela Atual",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Row {
                        Text(
                            text = ui.currentNumber.toString().padStart(2, '0'),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = " / ${ui.installment.count}",
                            fontSize = 16.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "Restante",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = ui.remainingAmount.toMoneyFormat(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )
        }
    }
}

@Composable
private fun StatusBadge(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isActive) Warning else Income

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = if (isActive) "Ativo" else "Concluído",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        )
    }
}

@Composable
private fun FiltersRow(
    uiState: InstallmentsUiState,
    onAction: (InstallmentsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "category_filter") {
            Box {
                CategoryFilterChip(
                    selectedCategory = uiState.selectedCategory,
                    categories = uiState.categories,
                    onAction = onAction,
                )
            }
        }

        item(key = "type_filter") {
            Box {
                TypeFilterChip(
                    selectedType = uiState.selectedType,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    selectedCategory: Category?,
    categories: List<Category>,
    onAction: (InstallmentsAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor = selectedCategory?.let { category ->
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
                contentDescription = null,
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color,
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text("Todas") },
            onClick = {
                onAction(InstallmentsAction.SelectCategory(null))
                expanded = false
            },
        )

        categories.forEach { category ->
            DropdownMenuItem(
                text = { Text(category.name) },
                onClick = {
                    onAction(InstallmentsAction.SelectCategory(category))
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun TypeFilterChip(
    selectedType: Transaction.Type?,
    onAction: (InstallmentsAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val chipColor = when (selectedType) {
        Transaction.Type.EXPENSE -> ExpenseColor
        Transaction.Type.ADJUSTMENT -> AdjustmentColor
        Transaction.Type.INCOME -> IncomeColor
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
                    Transaction.Type.INCOME -> "Receita"
                    else -> "Tipo"
                }
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        },
        colors = chipColor?.let { color ->
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color,
                selectedLeadingIconColor = color,
            )
        } ?: FilterChipDefaults.filterChipColors()
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text("Todos") },
            onClick = {
                onAction(InstallmentsAction.SelectType(null))
                expanded = false
            },
        )

        listOf(
            Transaction.Type.EXPENSE to "Despesa",
            Transaction.Type.INCOME to "Receita",
            Transaction.Type.ADJUSTMENT to "Ajuste",
        ).forEach { (type, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onAction(InstallmentsAction.SelectType(type))
                    expanded = false
                },
            )
        }
    }
}
