@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.recurring
import com.neoutils.finsight.ui.util.isWideWindow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.ui.component.BackButton
import org.koin.compose.koinInject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.neoutils.finsight.ui.util.exposeTestTags
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_card_monthly_amount
import com.neoutils.finsight.resources.recurring_empty_filter
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_filter_active
import com.neoutils.finsight.resources.recurring_filter_archived
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_status_archived
import com.neoutils.finsight.resources.recurring_screen_create
import com.neoutils.finsight.resources.recurring_screen_day
import com.neoutils.finsight.resources.recurring_screen_empty
import com.neoutils.finsight.resources.recurring_screen_title
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Warning
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecurringScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: RecurringViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val detailController = LocalDetailPaneController.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("recurring")
    }

    Scaffold(
        modifier = Modifier.testTag("screen_recurring"),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.recurring_screen_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    if (!isWideWindow()) {
                        BackButton(onClick = onNavigateBack)
                    }
                },
                actions = {
                    // One selector, in the top bar — same place and shape as the
                    // categories screen.
                    if (uiState is RecurringUiState.Content) {
                        FilterSelector(
                            selected = uiState.filter,
                            onSelect = { viewModel.onAction(RecurringAction.SelectFilter(it)) },
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Not tied to Content: filtering by Archived in an app with none used to
            // hide the create button.
            if (uiState !is RecurringUiState.Loading) {
                FloatingActionButton(
                    onClick = { modalManager.show(RecurringFormModal()) },
                    // The empty state's button carries the same id: a flow asks to create
                    // a recurring, not for whichever affordance renders that offer.
                    modifier = Modifier.testTag("recurring_add"),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        },
    ) { paddingValues ->
        when (val uiState = uiState) {
            is RecurringUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is RecurringUiState.Empty -> {
                EmptyDatabaseState(
                    onCreateClick = { modalManager.show(RecurringFormModal()) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            is RecurringUiState.Content if uiState.filteredRecurring.isEmpty() -> {
                EmptyFilterState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            is RecurringUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.filteredRecurring,
                        key = { "recurring_${it.recurring.id}" },
                    ) { item ->
                        RecurringCard(
                            recurring = item.recurring,
                            amount = item.amount,
                            onClick = { detailController.show(ViewRecurringModal(item.recurring.id)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSelector(
    selected: RecurringFilter,
    onSelect: (RecurringFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            TextButton(
                onClick = { menuExpanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Unspecified,
                ),
                modifier = Modifier.testTag("recurring_filter"),
            ) {
                Text(text = stringResource(selected.label))
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
            // Its own popup window, which the app window's opt-in does not reach. The
            // options are reached by id because "Active" and "Archived" are also the
            // words a recurring's *status* is rendered with.
            modifier = Modifier.exposeTestTags(),
        ) {
            RecurringFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(stringResource(filter.label)) },
                    modifier = Modifier.testTag("recurring_filter_option_${filter.name.lowercase()}"),
                    trailingIcon = if (selected == filter) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        onSelect(filter)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

private val RecurringFilter.label: StringResource
    get() = when (this) {
        RecurringFilter.ACTIVE -> Res.string.recurring_filter_active
        RecurringFilter.EXPENSE -> Res.string.recurring_expense
        RecurringFilter.INCOME -> Res.string.recurring_income
        RecurringFilter.ARCHIVED -> Res.string.recurring_filter_archived
    }

/** A filter with nothing to show, database not empty: a quiet note, no CTA. */
@Composable
private fun EmptyFilterState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Autorenew,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(40.dp),
        )
        Text(
            text = stringResource(Res.string.recurring_empty_filter),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/** The big CTA, earned only by a database with no recurring at all. */
@Composable
private fun EmptyDatabaseState(
    onCreateClick: () -> Unit,
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
                text = stringResource(Res.string.recurring_screen_empty),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recurring_add"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.recurring_screen_create))
            }
        }
    }
}

@Composable
private fun RecurringCard(
    recurring: Recurring,
    amount: DisplayAmount?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val typeColor = if (recurring.type.isIncome) Income else Expense
    val typeLabel = if (recurring.type.isIncome) {
        stringResource(Res.string.recurring_income)
    } else {
        stringResource(Res.string.recurring_expense)
    }

    Card(
        onClick = onClick,
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
                    modifier = Modifier.weight(1f),
                ) {
                    val category = recurring.category
                    if (category != null) {
                        CategoryIconBox(
                            category = category,
                            contentPadding = PaddingValues(8.dp),
                        )
                    } else {
                        Surface(
                            color = typeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                imageVector = if (recurring.type.isIncome) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text = recurring.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val categoryName = recurring.category
                        if (!recurring.title.isNullOrBlank() && categoryName != null) {
                            Text(
                                text = categoryName.name,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The colour alone cannot carry "archived" — it fails for anyone
                    // who does not read colour. Icon + label states it in the clear,
                    // as the category card already does.
                    if (recurring.isArchived) {
                        RecurringBadge(
                            label = stringResource(Res.string.recurring_status_archived),
                            color = Warning,
                            icon = Icons.Default.Archive,
                        )
                    }

                    RecurringBadge(label = typeLabel, color = typeColor)
                }
            }

            // No account left to denominate it: the figure is omitted rather than
            // rendered in a currency nobody chose for it.
            if (amount != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.recurring_card_monthly_amount),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatter.format(amount),
                        modifier = Modifier.testTag("recurring_card_amount"),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.recurring_screen_day, recurring.dayOfMonth),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                val creditCard = recurring.creditCard
                val account = recurring.account
                // An archived source keeps its name — that is the history — but reads
                // muted, the same way an archived category does (see `displayColor`).
                val sourceColor = if (recurring.hasUsableSource) {
                    colorScheme.onSurfaceVariant
                } else {
                    colorScheme.outline
                }
                when {
                    creditCard != null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = creditCard.name,
                            fontSize = 14.sp,
                            color = sourceColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    account != null -> Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = account.name,
                            fontSize = 14.sp,
                            color = sourceColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
