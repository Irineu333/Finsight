package com.neoutils.finsight.ui.screen.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatOrUnresolved
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budgets_row_categories_more
import com.neoutils.finsight.resources.budgets_row_derived_limit
import com.neoutils.finsight.resources.budgets_row_exceeded
import com.neoutils.finsight.resources.budgets_row_spent
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.theme.Primary1
import com.neoutils.finsight.ui.theme.budgetProgressColor
import org.jetbrains.compose.resources.stringResource

/**
 * One budget, in the four things that tell it from the next one: its **identity**, its
 * **ceiling**, **what it measures** and **how much of it is gone**.
 *
 * **The ceiling is the row's hero figure, and the spending is not.** This screen is where
 * a budget is created, edited and deleted: the ceiling is its *definition*, and it is what
 * separates "Transporte de R$ 300" from "Casa de R$ 2.500". How much has been spent is a
 * question of *following* a budget, and it has its own surface — the dashboard's
 * `BudgetProgressCard`, which goes on showing the spent/limit pair and is deliberately not
 * aligned to this row. The ceiling has a second property that qualifies it for the place:
 * it was typed, in the currency chosen at creation, so it is the only figure here that no
 * rate can turn into the absence mark.
 *
 * **The progress is a ring around the chip, and that is what buys the density.** Wrapping
 * the icon the row would have drawn anyway, it costs *zero* additional height — which is
 * what leaves both text slots free for the ceiling and the categories. A bar under the name
 * would have taken one of them and pushed the row half as tall again.
 *
 * **It is two stacks, not a paired grid.** The identity — chip, title, categories — says
 * which budget this is; the figures — the derived ceiling's declaration, the ceiling, the
 * spending — say what it is worth. Nothing on one side belongs beside anything in
 * particular on the other, so nothing is aligned to it, and the figures growing a line for
 * a derived ceiling rearranges nothing on the identity's side.
 *
 * The ring's cost is paid elsewhere, deliberately: an arc compares worse between rows than
 * a bar does, and the list answers that by arriving **pre-compared** (see
 * `sortedByConsumption`); and a full ring cannot tell 100% from 300%, which is why going
 * over is said by a glyph and by arithmetic instead — never by the indicator, and never by
 * colour alone.
 *
 * It does **not** anticipate the detail sheet. What is left, by how much it was exceeded,
 * the full list of category names and the base income are all a tap away, labelled; a row
 * that previewed them paid height to add nothing.
 *
 * **The period slot.** The ceiling's period belongs under the identity, because the same
 * figure means opposite things by week and by month — but every budget is monthly today,
 * and a period every row carries distinguishes no row from its neighbour. So the place is
 * kept (`budgets_period_monthly` / `budgets_period_weekly` exist for it) and nothing is
 * drawn until the domain has more than one period.
 */
@Composable
internal fun BudgetCard(
    progress: BudgetProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No fraction means no "how full" to colour by, so the accent falls back to the
    // neutral one rather than to the colour an empty ring would wear.
    val accent = progress.progress?.let { budgetProgressColor(it) } ?: colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier.testTag("budget_card"),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        // Two stacks side by side, and stacks rather than a paired grid because the two
        // sides answer different questions: the left says **which budget this is**, the
        // right **what it is worth**. Nothing on the left belongs beside anything in
        // particular on the right, so nothing is aligned to it.
        //
        // The figures are the taller stack — three lines where the ceiling is derived, two
        // otherwise — so they set the row's height, and the chip and the identity centre
        // against them as one block. Centred and not hung from the top because the two are
        // read together: the chip is the identity's, and a name pinned to the first line
        // would drift away from the ring beside it on the rows that grow.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                budget = progress.budget,
                fraction = progress.progress,
                accent = accent,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ROW_LINE_GAP),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress.budget.title,
                        style = TITLE_STYLE,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Truncates before it pushes the glyph off the line: a budget is
                        // recognisable from its first words and from the chip beside it,
                        // while the mark of going over is the whole state.
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )

                    // Going over, as a glyph with the state spelled in its content
                    // description. It is not optional decoration: the ring saturates, and
                    // with the ceiling as the hero figure there is no label left to swap
                    // from "remaining" to "exceeded by".
                    if (progress.isExceeded) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropUp,
                            contentDescription = stringResource(Res.string.budgets_row_exceeded),
                            tint = budgetProgressColor(progress = 1f),
                            modifier = Modifier
                                .testTag("budget_exceeded_mark")
                                .size(18.dp),
                        )
                    }
                }

                CategoryStack(categories = progress.budget.categories)

                // The period the ceiling refers to belongs here, under the identity. Every
                // budget is monthly today, and a period every row carries distinguishes no
                // row from its neighbour, so the place is kept and nothing is drawn.
            }

            // The figures, right-aligned and unweighted: they are measured before the
            // identity, which is what makes the identity the side that yields.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ROW_LINE_GAP),
            ) {
                progress.budget.derivedLimitPercentage?.let { percentage ->
                    DerivedLimitMark(
                        percentage = percentage,
                        source = progress.recurringLabel,
                    )
                }

                Ceiling(progress = progress)

                SpentFigure(progress = progress)
            }
        }
    }
}

/** The ceiling — the row's hero figure, and the one no rate can take away. */
@Composable
private fun Ceiling(progress: BudgetProgress) {
    Text(
        text = LocalCurrencyFormatter.current.format(progress.limitAmount),
        modifier = Modifier.testTag("budget_limit_amount"),
        style = LIMIT_STYLE,
        color = colorScheme.onSurface,
        maxLines = 1,
    )
}

/**
 * How much of the ceiling is gone, printed under the ceiling itself so that going over is
 * legible **by arithmetic** before any glyph or colour says it.
 *
 * **The row is a surface of its own grammar** in the sense of `money-display`: it has the
 * width of one amount and no more, so where the spending gathers a part no rate reaches it
 * shows the absence mark rather than the parts. The detail sheet is the surface with the
 * room, and it still shows them.
 */
@Composable
private fun SpentFigure(progress: BudgetProgress) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocalCurrencyFormatter.current.formatOrUnresolved(progress.spentAmount),
            modifier = Modifier.testTag("budget_spent_amount"),
            style = typography.labelLarge,
            color = if (progress.isExceeded) {
                budgetProgressColor(progress = 1f)
            } else {
                colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        // The one label the row carries, and it is allowed precisely because there are
        // *two* money figures stacked here: it says which of the two this is, rather than
        // naming the nature of a figure that stands alone.
        Text(
            text = stringResource(Res.string.budgets_row_spent),
            style = typography.labelLarge,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The percentage a **derived** ceiling declares — `null` where the ceiling was typed.
 *
 * A `PERCENTAGE` budget reaches the surface as `budget.copy(amount = limit)`, so the
 * number the row prints is *this month's share of a base income* and is indistinguishable
 * from a figure the user typed. While the ceiling was the card's third datum that passed;
 * as the hero figure it would have the row claim permanence over a number that re-derives
 * itself every month.
 */
internal val Budget.derivedLimitPercentage: Int?
    get() = percentage?.toInt().takeIf { limitType == LimitType.PERCENTAGE }

/**
 * The mark of a derived ceiling: the glyph the app already spends on *recurrence*, the share
 * it takes, and **of what** it takes it.
 *
 * `Primary1`, and deliberately neither of the two nearby alternatives: amber would read as
 * a warning, and a derived ceiling is normal; `Info` is the app's colour for *edit*, which
 * is the very button this budget's detail sheet offers.
 *
 * **The share alone does not discriminate, which is why the source is named here.** "30%"
 * is the same mark on a budget that takes a share of the salary and on one that takes it of
 * the rent, and a user who keeps both would read one mark twice — failing the very test by
 * which this row decides what to assert. The detail sheet still enumerates the base income
 * and navigates to it; what the row adds is which of them this is.
 *
 * **The two parts break differently, and on purpose.** The percentage is the derivation and
 * never gives way; the name says *which* derivation and yields first, on one line, ellipsised
 * at the width of the row. Having a line to itself is what makes that a rare event rather
 * than the common one. A ceiling whose recurring is gone shows the share by itself rather
 * than a dangling separator.
 */
@Composable
private fun DerivedLimitMark(percentage: Int, source: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Primary1.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Autorenew,
            contentDescription = stringResource(Res.string.budgets_row_derived_limit),
            tint = Primary1,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "$percentage%",
            style = typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Primary1,
            maxLines = 1,
            // Unweighted, so it is measured before the name and keeps its width whatever
            // the line has left: it is the derivation, and half a percentage is nothing.
        )

        if (source != null) {
            Text(
                text = "·",
                style = typography.labelMedium,
                color = Primary1.copy(alpha = 0.6f),
                // Punctuation between two readings, not a reading of its own: spoken
                // aloud it is noise between "30%" and the name it separates.
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = source,
                style = typography.labelMedium,
                color = Primary1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Yields before the percentage, which is unweighted, and is capped so
                // that the figures column cannot widen past what the identity needs.
                modifier = Modifier.widthIn(max = DERIVED_SOURCE_MAX_WIDTH),
            )
        }
    }
}

/**
 * The budget's own chip, wrapped by how much of the ceiling is gone.
 *
 * **No fraction, no arc — but always the track.** An arc drawn at zero would claim
 * "nothing spent yet", which is exactly what is not known when part of the spending cannot
 * be priced; the track alone keeps the row the same height as its neighbours while
 * asserting nothing. Drawn here rather than by `CircularProgressIndicator` for that reason:
 * the two states differ by one arc, and nothing else about the ring may move between them.
 */
@Composable
private fun ProgressRing(
    budget: Budget,
    fraction: Float?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .size(RING_SIZE)
            .drawBehind {
                val stroke = RING_STROKE.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )

                if (fraction != null) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CategoryIconBox(
            icon = budget.icon,
            tint = accent,
            shape = CircleShape,
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.size(CHIP_SIZE),
        )
    }
}

/**
 * What the budget measures — the second thing that tells one row from its neighbour, since
 * two budgets of the same name are separated only by the categories they watch.
 *
 * Icons rather than names, for a reason of layout that only appears once drawn: the stack
 * has a **bounded width**, so the left of the row never competes with the figure on the
 * right. With names, the title and the categories would truncate on different lines at the
 * same time. Whatever does not fit is declared by a counted overflow, never dropped.
 *
 * **They carry no category colour, and that is a correction rather than a taste.**
 * `categoryDisplayColor` answers by `Category.Type`, not by category, and a budget holds
 * only expense categories — so every icon of every budget would come out in the very
 * colour the ring uses to mean *exceeded*. A budget at 15% of its ceiling would show a
 * green ring beside three red marks. On this row **colour has a single owner: the
 * progress.**
 */
@Composable
private fun CategoryStack(categories: List<Category>) {
    if (categories.isEmpty()) return

    val shape = RoundedCornerShape(5.dp)

    Row(horizontalArrangement = Arrangement.spacedBy(CATEGORY_CHIP_OVERLAP)) {
        categories.take(MAX_CATEGORY_CHIPS).forEach { category ->
            Box(
                modifier = Modifier
                    .size(CATEGORY_CHIP_SIZE)
                    .clip(shape)
                    .background(colorScheme.surfaceContainerHighest)
                    // The card's own colour at the chip's edge, so two overlapping chips
                    // read as two rather than as one blot.
                    .border(width = 1.dp, color = colorScheme.surfaceContainer, shape = shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = category.icon(),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        val overflow = categories.size - MAX_CATEGORY_CHIPS
        if (overflow > 0) {
            Text(
                text = stringResource(Res.string.budgets_row_categories_more, overflow),
                style = typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/**
 * **The two things the row leads with read at the same size**, and are told apart by weight
 * alone: the ceiling carries the extra weight because it is the figure the row exists to
 * state, and the identity carries none because it is a name and not a number. Sized apart
 * as well, the title read as a caption over its own budget.
 *
 * Their line height is [androidx.compose.material3.Typography.titleMedium]'s, which is what
 * sets the row's height: the figures' two lines (24 + [ROW_LINE_GAP] + 20) come to 46dp, and
 * every variant of the row measures that same 46dp — exceeded and not, resolved and not. A
 * **derived** ceiling is the one exception, and a declared one: its declaration takes a
 * third line in the figures' stack, and the row grows by it rather than half-saying it.
 */
private val HEADLINE_SIZE = 17.sp

private val TITLE_STYLE
    @Composable get() = typography.titleMedium.copy(fontSize = HEADLINE_SIZE)

private val LIMIT_STYLE
    @Composable get() = typography.titleMedium.copy(
        fontSize = HEADLINE_SIZE,
        fontWeight = FontWeight.SemiBold,
    )

/**
 * The ring, and the chip inside it.
 *
 * The ring does **not** govern the row's height: the figures' stack measures 46dp and clears
 * it. What the ring does is stay under that in every variant, so a row's height is decided
 * by what it has to say and never by the chip — which matters more here than elsewhere,
 * because this list reorders itself as the month goes on and `animateItem()` should have
 * one height to animate between for rows that say the same things.
 *
 * The three move together. Growing the ring alone would eat the chip's padding until the
 * glyph touched the arc; growing the chip alone would do the same from the inside.
 */
private val RING_SIZE = 42.dp
private val RING_STROKE = 3.5.dp
private val CHIP_SIZE = 30.dp

/**
 * Between the two lines of the grid. It is the term that decides which side governs the
 * height: at 2dp the text column comes to 46dp and clears [RING_SIZE].
 */
private val ROW_LINE_GAP = 2.dp

/**
 * Three chips is what the stack shows before it starts counting. The number is a width
 * budget, not a judgement about categories: three chips plus a counted overflow leave the
 * ceiling its room on the line above at 360dp, which is the narrowest the app draws.
 */
private const val MAX_CATEGORY_CHIPS = 3
private val CATEGORY_CHIP_SIZE = 19.dp
private val CATEGORY_CHIP_OVERLAP = (-5).dp

/**
 * How wide the name of the base income may grow.
 *
 * The figures column is unweighted — measured before the identity, so that the identity is
 * the side that yields — and the declaration is the only thing in it whose width the app
 * does not control. Left uncapped, one long recurring name would set the width of the whole
 * column and take the title's room with it.
 *
 * It is a **layout budget and not a type budget**, which is why it is in dp: what it
 * protects is the share of the row left to the identity, and a larger font scale should
 * shorten the name rather than the title.
 */
private val DERIVED_SOURCE_MAX_WIDTH = 104.dp

