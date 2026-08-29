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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_empty_filter
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_filter_all
import com.neoutils.finsight.resources.recurring_collapse
import com.neoutils.finsight.resources.recurring_expand
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_screen_create
import com.neoutils.finsight.resources.recurring_screen_empty
import com.neoutils.finsight.resources.recurring_screen_title
import com.neoutils.finsight.resources.recurring_section_pending
import com.neoutils.finsight.resources.recurring_section_posted
import com.neoutils.finsight.resources.recurring_section_skipped
import com.neoutils.finsight.resources.recurring_section_upcoming
import com.neoutils.finsight.resources.recurring_summary_fixed_expense
import com.neoutils.finsight.resources.recurring_summary_fixed_income
import com.neoutils.finsight.resources.recurring_summary_forecast
import com.neoutils.finsight.resources.recurring_summary_settled
import com.neoutils.finsight.resources.recurring_summary_undenominated
import com.neoutils.finsight.resources.recurring_view_archived
import com.neoutils.finsight.ui.component.ConsolidationBadge
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.component.MonthPickerDropdownMenu
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormModal
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.navigation.ArchivedRecurringRoute
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

    // Which groups the user folded away, by the name of the state that heads them.
    //
    // It lives here and not in the heading because it is the *list* that stops emitting
    // the rows: a `LazyColumn` composes one item at a time, so a flag held inside a
    // heading would be forgotten the moment that heading scrolled out of the viewport.
    //
    // Keyed by the state and not by the month, so folding "Posted" away survives moving
    // to another month: it is a statement about how this user reads the screen, not
    // about the month being read.
    val collapsed = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }

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
                    // The cut by nature, in the top bar — same place and shape as the
                    // categories screen. It governs the list and never the summary
                    // card, and the bar's own edge is what separates the two.
                    if (uiState is RecurringUiState.Content) {
                        FilterSelector(
                            selected = uiState.filter,
                            onSelect = { viewModel.onAction(RecurringAction.SelectFilter(it)) },
                        )
                    }

                    // The way to the archive, in the overflow the credit cards screen
                    // already puts its own archive behind. Beside the cut and not inside
                    // it: the selector narrows what this screen lists, and this leaves
                    // the screen — one control that did both would leave the user unable
                    // to tell which of the two they had just done.
                    ArchiveOverflow(
                        onOpenArchive = { navController.navigate(ArchivedRecurringRoute) },
                    )
                }
            )
        },
        floatingActionButton = {
            // Not tied to Content: a month with no cycle at all must still offer the
            // create button.
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

                    // A month with no cycle is an item too, never a branch that replaces
                    // the screen: it is precisely when the summary has the most to say,
                    // and erasing it would leave the user with neither answer nor
                    // context. The cut by nature empties the list the same way, and gets
                    // the same treatment for the same reason.
                    if (uiState.sections.isEmpty()) {
                        item(key = "recurring_empty_filter") {
                            EmptyFilterState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp)
                                    .animateItem(),
                            )
                        }
                    }

                    // Four groups, at most, on one scroll. No `stickyHeader`: with a list
                    // this short a heading pinned to the top would spend permanent height
                    // naming a group that fits on the screen whole — and the summary card
                    // is already an item that scrolls away, which is the grammar the
                    // headings follow.
                    uiState.sections.forEach { section ->
                        val expanded = section.status.name !in collapsed

                        item(key = "recurring_section_${section.status.name}") {
                            SectionHeader(
                                status = section.status,
                                count = section.cycles.size,
                                expanded = expanded,
                                onToggle = {
                                    if (expanded) {
                                        collapsed += section.status.name
                                    } else {
                                        collapsed -= section.status.name
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .animateItem(),
                            )
                        }

                        // A folded group emits no row at all — the heading and its count
                        // stay, which is what makes folding one worth doing: the group
                        // goes on being named and counted while it stops taking height.
                        if (expanded) items(
                            items = section.cycles,
                            key = { "recurring_${it.recurring.id}" },
                        ) { cycle ->
                            // One row for the whole list, and it leads to the same place
                            // from every section: this is the screen of the rules the user
                            // keeps, and the row that shows what one of them posted is
                            // still that rule's row. Which of the two sources filled it is
                            // settled in the view model — the row arrives with the answer,
                            // and the section heading is what names it.
                            RecurringCard(
                                row = cycle.row,
                                onClick = {
                                    detailController.show(ViewRecurringModal(cycle.recurring.id))
                                },
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
}

/**
 * The way out of this screen and into the archive — one option, behind the overflow the
 * credit cards screen already uses for exactly this.
 *
 * It is offered whether or not anything is archived. A control that appeared and
 * disappeared with the contents of the destination it leads to would be a top bar that
 * changes shape for a reason the user cannot see, and the destination states its own
 * emptiness better than an absent button does.
 */
@Composable
private fun ArchiveOverflow(
    onOpenArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("recurring_more_options"),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Its own popup window: the app window's opt-in does not reach it, and its
            // single option is copy a translation reworks.
            modifier = Modifier.exposeTestTags(),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.recurring_view_archived)) },
                onClick = {
                    expanded = false
                    onOpenArchive()
                },
                modifier = Modifier.testTag("recurring_view_archived"),
            )
        }
    }
}

/**
 * What names a group of cycles, how many are in it, and the control that folds it away.
 *
 * The heading is the legend for every row under it — it is what says *pending* or
 * *skipped*, once, so no row has to carry a mark of its own. The count comes with it
 * because the question the screen answers is not only *which* but *how many are left*,
 * and it is the figure the month summary stopped repeating when the sections took the
 * job over. **Both stay while the group is folded**, which is the whole reason folding
 * one is useful: a group the user has finished with keeps being named and counted, and
 * stops spending height.
 *
 * The order is label, arrow, count: the arrow belongs to the word it turns under, and
 * the count is what the fold leaves behind — so it sits outside the control rather than
 * between it and its own affordance.
 *
 * The fold borrows `SummaryBlock`'s grammar wholesale — the same turned
 * `KeyboardArrowDown`, the same target that wraps its own words rather than the row's
 * full width, the same inset spent twice so the label still starts on the list's own
 * column. Two folds on one screen that behaved differently would be two folds to learn.
 */
@Composable
private fun SectionHeader(
    status: RecurringCycleStatus,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f)

    Row(modifier = modifier) {
        Row(
            modifier = Modifier
                // Pulled left by exactly the inset the padding below puts back, so the
                // target has room around the words while the label still starts on the
                // column the cards below it are flush with.
                .offset(x = -HEADER_HIT_INSET)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                // The start inset is alignment and not breathing room — the offset above
                // cancels it, so the label still starts on the column the cards are flush
                // with. The other three sides are the target's own frame, and they are
                // the same on all three.
                .padding(
                    start = HEADER_HIT_INSET,
                    end = HEADER_FRAME,
                    top = HEADER_FRAME,
                    bottom = HEADER_FRAME,
                )
                .testTag("recurring_section_${status.name.lowercase()}"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(status.label),
                style = typography.titleMedium,
                color = colorScheme.onSurfaceVariant,
            )

            // Which way it points is not the only thing that says the state: the
            // description names the action in words.
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) Res.string.recurring_collapse else Res.string.recurring_expand
                ),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )

            // A numeral needs no translation, and the words beside it are already
            // translated.
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = count.toString(),
                    style = typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("recurring_section_${status.name.lowercase()}_count"),
                )
            }
        }
    }
}

private val RecurringCycleStatus.label: StringResource
    get() = when (this) {
        RecurringCycleStatus.PENDING -> Res.string.recurring_section_pending
        RecurringCycleStatus.UPCOMING -> Res.string.recurring_section_upcoming
        RecurringCycleStatus.POSTED -> Res.string.recurring_section_posted
        RecurringCycleStatus.SKIPPED -> Res.string.recurring_section_skipped
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
            // options are reached by id because "Expense" and "Income" are also the words
            // a recurring's *nature* is rendered with elsewhere on the row.
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
        RecurringFilter.ALL -> Res.string.recurring_filter_all
        RecurringFilter.EXPENSE -> Res.string.recurring_expense
        RecurringFilter.INCOME -> Res.string.recurring_income
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
 * exactly one thing: there the chips carry the whole cut, here the chip carries the month
 * — of the card *and* of the list, which shows that month's cycles — while the selector in
 * the top bar carries the cut by nature, which is the list's alone.
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

            // Its own sentence, and never the badge's: the badge's copy is about a
            // missing rate, and this is a template with no account. Two failures with
            // two different ways out — pointing the template somewhere real, and
            // registering a rate — and reusing that copy here would say the wrong one
            // with authority.
            //
            // It is the one annotation the card kept when the cycle counter left for the
            // sections below: no section counts it, because it is not a count of cycles.
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
                    if (expanded) Res.string.recurring_collapse
                    else Res.string.recurring_expand
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

/**
 * The frame a section heading's tap target draws around its own contents, on the three
 * sides that are not [HEADER_HIT_INSET]'s.
 *
 * Small, and the same on all three: what keeps the target from reading as a box drawn on
 * the words is the radius, not the room — and a frame thicker on one side than the others
 * reads as a mistake in a control whose whole shape is the ripple.
 */
private val HEADER_FRAME = 2.dp

private val SETTLED_AMOUNT_STYLE = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)

/**
 * A step below the fact, and the same colour: what separates the two blocks is the
 * label, the order and the weight — never the palette, which belongs to the nature of
 * the money in all four figures.
 */
private val FORECAST_AMOUNT_STYLE = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
