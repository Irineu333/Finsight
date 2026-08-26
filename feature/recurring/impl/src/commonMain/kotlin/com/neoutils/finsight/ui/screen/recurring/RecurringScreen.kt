@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.recurring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.formatOrUnresolved
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_empty_filter
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_filter_active
import com.neoutils.finsight.resources.recurring_filter_archived
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_screen_create
import com.neoutils.finsight.resources.recurring_screen_day
import com.neoutils.finsight.resources.recurring_screen_empty
import com.neoutils.finsight.resources.recurring_screen_title
import com.neoutils.finsight.resources.recurring_source_unusable
import com.neoutils.finsight.resources.recurring_status_archived
import com.neoutils.finsight.resources.recurring_summary_collapse
import com.neoutils.finsight.resources.recurring_summary_counter
import com.neoutils.finsight.resources.recurring_summary_expand
import com.neoutils.finsight.resources.recurring_summary_fixed_expense
import com.neoutils.finsight.resources.recurring_summary_fixed_income
import com.neoutils.finsight.resources.recurring_summary_forecast
import com.neoutils.finsight.resources.recurring_summary_settled
import com.neoutils.finsight.resources.recurring_summary_skipped
import com.neoutils.finsight.resources.recurring_summary_undenominated
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.component.ConsolidationBadge
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.icons.VectorLazyIcon
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.util.exposeTestTags
import com.neoutils.finsight.ui.util.isWideWindow
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
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
    val navController = LocalNavController.current

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
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    // One selector, in the top bar — same place and shape as the
                    // categories screen. It governs the list and never the summary
                    // card, and the bar's own edge is what separates the two.
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

            // The only emptiness that still takes the whole screen: with no template at
            // all there is no month to summarise, and the offer to create the first one
            // is the screen.
            is RecurringUiState.Empty -> {
                EmptyDatabaseState(
                    onCreateClick = { modalManager.show(RecurringFormModal()) },
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
                    // An item of the list, not a header above it: it scrolls away and
                    // does not come back, which is what makes it affordable to be tall.
                    item(key = "recurring_month_summary") {
                        RecurringMonthCard(
                            summary = uiState.summary,
                            selectedYearMonth = uiState.selectedYearMonth,
                            onMonthSelected = {
                                viewModel.onAction(RecurringAction.SelectMonth(it))
                            },
                            onSeeRates = { navController.navigate(ExchangeRatesRoute) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }

                    // An empty cut is an item too, never a branch that replaces the
                    // screen: it is precisely when the summary has the most to say, and
                    // erasing it would leave the user with neither answer nor context.
                    if (uiState.filteredRecurring.isEmpty()) {
                        item(key = "recurring_empty_filter") {
                            EmptyFilterState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp)
                                    .animateItem(),
                            )
                        }
                    }

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
        modifier = modifier,
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

/**
 * The month, split into what is **fact** and what is **forecast**.
 *
 * It inherits `SummaryCard`'s grammar — the chip inside the card, one badge for the whole
 * card instead of a note per figure, each figure arriving with its sign policy already
 * attached, and a conditional annotation being *absent* rather than zero — and inverts
 * exactly one thing: there the chips govern the card and the list, here the chip governs
 * only the card and the filter in the top bar only the list.
 *
 * The two blocks are not two halves of one class of thing. A posted figure is money in
 * the ledger; a not-yet-posted one is a claim about a month that may end without it being
 * written. So each block is named in words — position and weight alone would leave the
 * difference to whoever reads typography — and the fact comes first.
 */
@Composable
private fun RecurringMonthCard(
    summary: RecurringMonthSummary,
    selectedYearMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onSeeRates: () -> Unit,
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
                // The chip carries its own inset, so a flat 20 all round would read as
                // more room above than below: the top is discounted by that inset.
                .padding(start = 20.dp, top = 20.dp - CHIP_INSET, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeriodChip(
                    selectedYearMonth = selectedYearMonth,
                    onMonthSelected = onMonthSelected,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Four figures live in this card, so the mark is a prefix on each and the
                // thing that explains it is one button for the whole card (design D21).
                ConsolidationBadge(
                    figures = summary.figures,
                    onSeeRates = onSeeRates,
                    modifier = Modifier.testTag("recurring_summary_badge"),
                )
            }

            SummaryBlock(
                label = stringResource(Res.string.recurring_summary_settled),
                expense = summary.settledExpense,
                income = summary.settledIncome,
                amountStyle = SETTLED_AMOUNT_STYLE,
                headerTestTag = "recurring_summary_settled_header",
                expenseTestTag = "recurring_summary_settled_expense",
                incomeTestTag = "recurring_summary_settled_income",
            )

            SummaryBlock(
                label = stringResource(Res.string.recurring_summary_forecast),
                expense = summary.forecastExpense,
                income = summary.forecastIncome,
                amountStyle = FORECAST_AMOUNT_STYLE,
                headerTestTag = "recurring_summary_forecast_header",
                expenseTestTag = "recurring_summary_forecast_expense",
                incomeTestTag = "recurring_summary_forecast_income",
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The counter, and the only place a skipped cycle is representable at
                // all: it is neither a posting nor something still owed, so it is absent
                // from all four figures above — correctly, and that is exactly why the
                // card would otherwise fail to account for a forecast that shrank while
                // no fact grew. Not a filled proportion, because a proportion would have
                // to decide visually what a skipped cycle fills.
                Text(
                    text = listOfNotNull(
                        stringResource(
                            Res.string.recurring_summary_counter,
                            summary.handled,
                            summary.total,
                        ),
                        summary.skipped.takeIf { it > 0 }?.let { skipped ->
                            pluralStringResource(Res.plurals.recurring_summary_skipped, skipped, skipped)
                        },
                    ).joinToString(separator = " · "),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("recurring_summary_counter"),
                )

                // Its own sentence, and never the badge's: the badge's copy is about a
                // missing rate, and this is a template with no account. Two failures with
                // two different ways out — pointing the template somewhere real, and
                // registering a rate — and reusing that copy here would say the wrong one
                // with authority.
                if (summary.undenominated > 0) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.recurring_summary_undenominated,
                            summary.undenominated,
                            summary.undenominated,
                        ),
                        style = typography.bodySmall,
                        color = Warning,
                    )
                }
            }
        }
    }
}

/**
 * One half of the month, behind a header that folds it away.
 *
 * **A block with nothing in it opens folded.** Two lines of `R$ 0,00` are the card taking
 * room to assert an absence, and the block that is empty is usually the one the user is
 * not asking about — the fact half early in a month, the forecast half at the end of one.
 * The label stays either way, so what is folded is still named: a card that hid the words
 * as well would be a card that shrank for a reason the user cannot see.
 *
 * **The user's own toggle wins from then on**, and is re-seeded only when the block
 * crosses between having movement and not. Re-deriving it on every change would fold away
 * a block that had just been opened, the moment the month selector moved.
 */
@Composable
private fun SummaryBlock(
    label: String,
    expense: ConsolidatedAmount,
    income: ConsolidatedAmount,
    amountStyle: TextStyle,
    headerTestTag: String,
    expenseTestTag: String,
    incomeTestTag: String,
) {
    val holdsNothing = holdsNothing(expense, income)
    var expanded by rememberSaveable(holdsNothing) { mutableStateOf(!holdsNothing) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f)

    Column {
        Row(
            modifier = Modifier
                // Pulled left by exactly the inset the padding below puts back, so the
                // target has room around the words while the label still starts on the
                // card's own column — the figures under it are flush with that column,
                // and a header indented by its own breathing room reads as a mistake.
                .offset(x = -HEADER_HIT_INSET)
                .clip(RoundedCornerShape(4.dp))
                // The label and its arrow, and nothing past them: the two read as one
                // control, and the empty width to their right belongs to the card. A
                // target that ran the full width would put a ripple under the figures'
                // column, where nothing is clickable.
                .clickable { expanded = !expanded }
                .padding(horizontal = HEADER_HIT_INSET)
                .testTag(headerTestTag),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )

            // One glyph, turned — the same `KeyboardArrowDown` every other "there is more
            // here" in the app draws. Which way it points is not the only thing that says
            // the state: the description names the action in words.
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) Res.string.recurring_summary_collapse
                    else Res.string.recurring_summary_expand
                ),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }

        AnimatedVisibility(visible = expanded) {
            // The spacing lives in here rather than on the column above, so a folded
            // block leaves no gap behind it.
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryFigure(
                    label = stringResource(Res.string.recurring_summary_fixed_expense),
                    amount = expense,
                    color = Expense,
                    style = amountStyle,
                    testTag = expenseTestTag,
                )

                SummaryFigure(
                    label = stringResource(Res.string.recurring_summary_fixed_income),
                    amount = income,
                    color = Income,
                    style = amountStyle,
                    testTag = incomeTestTag,
                )
            }
        }
    }
}

/**
 * Whether a block of the summary has nothing under its label — which is what decides
 * that it opens folded.
 *
 * It reads **every term of both figures**, and that is the whole rule: the reducer
 * answers one term per currency, so a month may hold `US$ 50,00` beside a `R$ 0,00` that
 * only means "there are reais accounts and nothing moved in them". A check that looked at
 * one term would fold away a month with money in it, and the user would have to guess
 * that the block was hiding something.
 */
internal fun holdsNothing(expense: ConsolidatedAmount, income: ConsolidatedAmount): Boolean =
    expense.isZero && income.isZero

@Composable
private fun SummaryFigure(
    label: String,
    amount: ConsolidatedAmount,
    color: Color,
    style: TextStyle,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The label yields the room, never the figure.
            modifier = Modifier.weight(weight = 1f, fill = false),
        )

        // The tag names the amount and not the row: a row holds a label too, and a tag on
        // it would let an assertion pass by reading the word instead of the figure.
        MoneyText(
            figure = amount,
            style = style.copy(color = color),
            modifier = Modifier.testTag(testTag),
        )
    }
}

/**
 * The month selector of the card — the same chip `SummaryCard` draws, over the same
 * public `MonthPickerDropdownMenu`. Copied at ~25 lines rather than promoted: the wrapper
 * is private to that file, and a third use is what earns a move to `:core:designsystem`.
 */
@Composable
private fun PeriodChip(
    selectedYearMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurface,
            modifier = Modifier.testTag("recurring_summary_month"),
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 8.dp,
                    top = CHIP_INSET,
                    bottom = CHIP_INSET,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = LocalDateFormats.current.yearMonth.format(selectedYearMonth),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        MonthPickerDropdownMenu(
            expanded = expanded,
            selectedYearMonth = selectedYearMonth,
            onDismissRequest = { expanded = false },
            onMonthSelected = { selected ->
                onMonthSelected(selected)
                expanded = false
            },
        )
    }
}

/**
 * One rule the user keeps, in the four things that tell it from the next one: what it
 * **is**, where it **posts**, **how much**, and **when**.
 *
 * A 2×2 grid rather than a line with a subtitle, because a card's name is long — "Nubank
 * Ultravioleta" — and on one secondary line it would be truncated *after* the day. In
 * columns the day is always whole, and the pair (figure, day) read together is the only
 * thing on the screen that states the rule itself.
 *
 * It does **not** anticipate the detail sheet. Type, amount, day, status, account or card
 * and category are all a tap away, labelled; a row that previewed all six paid height to
 * add nothing. What is left is what discriminates.
 *
 * The chip is the 40dp/radius-8 module of the analytic cards, not the 48dp/radius-12 one
 * of the identity rows. Not a saving of 8dp — a filiation: this is a list of rules the
 * user maintains, and the dashboard's pending card answers a different question ("confirm
 * this cycle?"), which is why it has neither day nor source.
 */
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
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val category = recurring.category
            if (category != null) {
                CategoryIconBox(
                    category = category,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.size(CHIP_SIZE),
                )
            } else {
                // No category to read a colour and a glyph off: the row says what it can,
                // which is which way the money goes.
                CategoryIconBox(
                    icon = VectorLazyIcon(recurring.directionIcon),
                    tint = typeColor,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.size(CHIP_SIZE),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ROW_LINE_GAP),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Archived is a glyph with a description and no longer a badge with a
                    // container. `account-lifecycle` never asked for the badge — it asks
                    // that an archived recurring leave the active views and stay reachable
                    // through the dedicated cut — and since the two never share a screen,
                    // the badge was discriminating nothing.
                    if (recurring.isArchived) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = stringResource(Res.string.recurring_status_archived),
                            tint = Warning,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Text(
                        text = recurring.label,
                        style = typography.titleSmall,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The identity yields the room, never the figure: a rule is
                        // recognisable from its first words and from the chip beside it,
                        // while an amount missing part of itself is a number that lies.
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )

                    // The direction as a glyph, with the nature spelled in its content
                    // description: colour alone carries no state, and `money-display`
                    // forbids signing the figure of an item surface, so the badge could
                    // not simply become a `-` on the amount.
                    Icon(
                        imageVector = recurring.directionIcon,
                        contentDescription = typeLabel,
                        tint = typeColor,
                        modifier = Modifier.size(16.dp),
                    )
                }

                SourceLine(recurring = recurring)
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ROW_LINE_GAP),
            ) {
                // A magnitude, and no sign: this is an item surface, it shows one figure
                // and takes part in no displayed sum. The summary above does not sum these
                // rows, and no column of this screen closes on a total.
                //
                // When no account denominates the template the unresolved mark stands in
                // its place, on the same node, so the row keeps its height and the absence
                // is said out loud instead of being said by absence. The cause is on the
                // line below.
                Text(
                    text = formatter.formatOrUnresolved(amount),
                    modifier = Modifier.testTag("recurring_card_amount"),
                    style = typography.titleMedium,
                    color = typeColor,
                    maxLines = 1,
                )

                Text(
                    text = stringResource(Res.string.recurring_screen_day, recurring.dayOfMonth),
                    style = typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where the money leaves from — or that it cannot.
 *
 * An unusable source is the gravest thing this row can state: the account or card was
 * deleted or archived, and the template cannot post at all. It used to be a swap of
 * `onSurfaceVariant` for `outline`, sixty lines under a comment saying colour alone does
 * not carry state. It is now a glyph and a sentence.
 *
 * The glyph and the tone are what say *unusable*; the words go on saying **which** source,
 * for as long as there is one to name (see [sourceName]).
 */
@Composable
private fun SourceLine(recurring: Recurring) {
    val creditCard = recurring.creditCard
    val account = recurring.account

    val icon: ImageVector
    val text: String
    val color: Color

    if (!recurring.hasUsableSource) {
        icon = Icons.Outlined.LinkOff
        color = Warning
        // The sentence is the last resort, not the branch's answer: it speaks only for the
        // source that is gone, because a source that is merely archived still has a name
        // and the name is what tells two identical labels apart.
        text = recurring.sourceName() ?: stringResource(Res.string.recurring_source_unusable)
    } else {
        color = colorScheme.onSurfaceVariant
        if (creditCard != null) {
            icon = Icons.Default.CreditCard
            text = creditCard.name
        } else {
            icon = Icons.Default.AccountBalance
            text = account?.name.orEmpty()
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the row calls the template's source — `null` only when there is nothing left to
 * call it.
 *
 * **Archived and removed are two absences, and the row used to read them as one.** Both
 * make `Recurring.hasUsableSource` false, and the unusable branch never reached a name, so
 * two "Aluguel" in two archived banks read identically — losing precisely the distinction
 * the row exists to make. A removed source is `null` on both sides (the foreign key is
 * `SET_NULL`) and genuinely has no name; an archived one exists, is named, and archiving is
 * offered to the user as reversible — it may take away the path to the account, never the
 * account's name.
 *
 * The card comes first, as everywhere else that resolves a template's source: it is the
 * more specific of the two, and a template that names one is denominated by it.
 */
internal fun Recurring.sourceName(): String? = creditCard?.name ?: account?.name

/** The glyph of the nature, the one the transaction list already uses for it. */
private val Recurring.directionIcon: ImageVector
    get() = if (type.isIncome) {
        Icons.AutoMirrored.Filled.TrendingUp
    } else {
        Icons.AutoMirrored.Filled.TrendingDown
    }

/**
 * What governs the height of every variant of the row — with a category and without,
 * archived and active, denominated and not. Two lines of text on either side stay under
 * it, so the list has one height and `animateItem()` reorders without a jump.
 */
private val CHIP_SIZE = 40.dp

/**
 * Between the two lines of each column, and the same on both so the row reads as one
 * grid rather than as two stacks that happen to sit side by side. Set together with
 * [CHIP_SIZE]: it is what decides whether the chip or the text governs the height, and
 * the height must stay the same in every variant either way.
 */
private val ROW_LINE_GAP = 4.dp

/** The chip's own vertical breathing room, as in `SummaryCard`. */
private val CHIP_INSET = 6.dp

/**
 * The room the summary header's tap target takes around its own words — **sideways only**.
 *
 * Vertically it takes none: the header sits between two labelled blocks of a dense card,
 * where every dp it claims is one the card grows by, and it is a fold rather than a
 * primary action. So the target is exactly as tall as the line it wraps, and what keeps
 * it from reading as a box drawn on the text is the small radius, not height.
 *
 * It is spent twice — as padding, and as the offset that cancels it — so the two cannot
 * drift apart and leave the label indented out of the card's own column.
 */
private val HEADER_HIT_INSET = 4.dp

private val SETTLED_AMOUNT_STYLE = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)

/**
 * A step below the fact, and the same colour: what separates the two blocks is the
 * label, the order and the weight — never the palette, which belongs to the nature of
 * the money in all four figures.
 */
private val FORECAST_AMOUNT_STYLE = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
