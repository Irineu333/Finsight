@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.budgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.ui.theme.budgetProgressColor
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budgets_category_plural
import com.neoutils.finsight.resources.budgets_category_singular
import com.neoutils.finsight.resources.budgets_create
import com.neoutils.finsight.resources.budgets_empty
import com.neoutils.finsight.resources.budgets_exceeded_by
import com.neoutils.finsight.resources.budgets_limit
import com.neoutils.finsight.resources.budgets_remaining
import com.neoutils.finsight.resources.budgets_spent
import com.neoutils.finsight.resources.budgets_title
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BudgetsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.budgets_title)) },
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
                    MonthSelector(
                        selectedMonth = uiState.selectedMonth,
                        onMonthSelected = { viewModel.onAction(BudgetsAction.SelectMonth(it)) },
                    )
                },
            )
        },
        floatingActionButton = {
            if (uiState is BudgetsUiState.Content) {
                FloatingActionButton(
                    onClick = { modalManager.show(BudgetFormModal()) },
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { paddingValues ->
        when (val uiState = uiState) {
            is BudgetsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is BudgetsUiState.Empty -> {
                EmptyBudgetsState(
                    onCreateBudget = { modalManager.show(BudgetFormModal()) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            is BudgetsUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.budgetProgress,
                        key = { it.budget.id },
                    ) { progress ->
                        BudgetProgressItem(
                            progress = progress,
                            onClick = { modalManager.show(ViewBudgetModal(progress)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMonthPickerExpanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val menuWidth = 320.dp
    val menuOffsetX = with(LocalDensity.current) {
        (anchorWidthPx.toDp() - menuWidth) / 2
    }

    Box(modifier = modifier.padding(end = 8.dp)) {
        Row(
            modifier = Modifier
                .onSizeChanged { anchorWidthPx = it.width }
                .clip(RoundedCornerShape(4.dp))
                .clickable { isMonthPickerExpanded = true }
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedContent(
                targetState = selectedMonth,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { month ->
                Text(
                    text = LocalDateFormats.current.yearMonth.format(month),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }

        MonthPickerDropdownMenu(
            expanded = isMonthPickerExpanded,
            selectedYearMonth = selectedMonth,
            onDismissRequest = { isMonthPickerExpanded = false },
            onMonthSelected = onMonthSelected,
            menuWidth = menuWidth,
            offset = DpOffset(x = menuOffsetX, y = 4.dp),
        )
    }
}

@Composable
private fun BudgetProgressItem(
    progress: BudgetProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        val accentColor = budgetProgressColor(progress.progress)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBox(
                    icon = progress.budget.icon,
                    tint = accentColor,
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.size(40.dp),
                )

                Column {
                    Text(
                        text = progress.budget.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    val categoryCount = progress.budget.categories.size
                    val categoryLabel = if (categoryCount == 1) {
                        stringResource(Res.string.budgets_category_singular)
                    } else {
                        stringResource(Res.string.budgets_category_plural, categoryCount)
                    }
                    Text(
                        text = if (progress.recurringLabel != null) {
                            "$categoryLabel, ${progress.recurringLabel}"
                        } else {
                            categoryLabel
                        },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.budgets_limit),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatter.format(progress.budget.amount),
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
                        text = stringResource(Res.string.budgets_spent),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatter.format(progress.spent),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (progress.isExceeded) stringResource(Res.string.budgets_exceeded_by) else stringResource(
                            Res.string.budgets_remaining
                        ),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (progress.isExceeded) {
                            formatter.format(progress.spent - progress.budget.amount)
                        } else {
                            formatter.format(progress.remaining)
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )
        }
    }
}


@Composable
private fun EmptyBudgetsState(
    onCreateBudget: () -> Unit,
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
                text = stringResource(Res.string.budgets_empty),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateBudget,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.budgets_create))
            }
        }
    }
}
