@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.budgets
import com.neoutils.finsight.ui.util.isWideWindow

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
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.ChromeEffect
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.ConsolidationListNotice
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budgets_create
import com.neoutils.finsight.resources.budgets_empty
import com.neoutils.finsight.resources.budgets_title
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BudgetsViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val detailController = LocalDetailPaneController.current
    val navController = LocalNavController.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("budgets")
    }

    // Only in `Content`, as the button used to be: publishing unconditionally would put it on
    // screen while the month is still being read.
    //
    // The empty state renders this same command as its own full-width button, so a second one over
    // the content would be the same offer twice — and the one the shell would serve there, having
    // been handed no action, is not even this screen's.
    ChromeEffect(
        config = if (uiState is BudgetsUiState.Empty) {
            ChromeConfig.NoButtonOverContent
        } else {
            ChromeConfig.Default
        },
        actions = if (uiState is BudgetsUiState.Content) {
            remember(modalManager) {
                listOf(
                    ChromeAction(
                        icon = Icons.Default.Add,
                        labelRes = Res.string.budgets_create,
                        // The same command as the empty state's button, so it carries the same
                        // id: a flow asks for "create a budget", not for whichever affordance
                        // the current state happens to render it as.
                        testTag = "budgets_add",
                        onClick = { modalManager.show(BudgetFormModal()) },
                    )
                )
            }
        } else {
            emptyList()
        }
    )

    Scaffold(
        modifier = Modifier.testTag("screen_budgets"),
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
                        onMonthSelected = { viewModel.onAction(BudgetsAction.SelectMonth(it)) },
                    )
                },
            )
        },
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
                    // One notice for the whole list, and only where a row had to fall
                    // back on the absence mark. The cause is the archive missing a rate —
                    // global, and identical on every row it touches — so it is stated
                    // once here instead of costing width on every row of the screen.
                    //
                    // Emitted conditionally rather than composed to nothing: an item that
                    // draws nothing still takes the list's 8dp of spacing.
                    if (uiState.budgetProgress.any { !it.isResolved }) {
                        item(key = "budgets_consolidation_notice") {
                            ConsolidationListNotice(
                                onSeeRates = { navController.navigate(ExchangeRatesRoute) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }

                    items(
                        items = uiState.budgetProgress,
                        key = { it.budget.id },
                    ) { progress ->
                        BudgetCard(
                            progress = progress,
                            onClick = { detailController.show(ViewBudgetModal(progress.budget.id, uiState.selectedMonth)) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budgets_add"),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.budgets_create))
            }
        }
    }
}
