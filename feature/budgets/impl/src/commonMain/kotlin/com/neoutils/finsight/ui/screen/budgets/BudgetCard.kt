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
 * what leaves both text slots of the 2×2 grid free for the ceiling and the categories. A
 * bar under the name would have taken one of them and pushed the row half as tall again.
 *
 * The grid grows to a third line for exactly one thing: the declaration of a **derived**
 * ceiling, which is the only thing the row has to say that does not fit the other two lines
 * without shortening what it says.
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
 * **The period slot.** The ceiling's period belongs beside the identity, because the same
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
    val formatter = LocalCurrencyFormatter.current
    // Both figures are denominated by the limit, never by the base: a budget declares its
    // currency once, at creation, and what is shown under the ceiling is the spending
    // reduced *to it* (design D13). `BudgetProgress` carries them already denominated.
    //
    // No fraction means no "how full" to colour by, so the accent falls back to the
    // neutral one rather than to the colour an empty ring would wear.
    val accent = progress.progress?.let { budgetProgressColor(it) } ?: colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier.testTag("budget_card"),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
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
                // The declaration of a derived ceiling, on a line of its own, directly
                // over the figure it qualifies and aligned to it.
                //
                // **Above the ceiling and never beside the title**, because what it says
                // is that *that number* is a share and re-derives every month; on the
                // title's line it would say it about the budget's name, which is the one
                // thing on the row the user typed whole. A line of its own is also what
                // frees it from competing for width: the income's name fits entire in the
                // common case, and the identity gives up nothing for it.
                //
                // The row that carries it is taller than one that does not, and that is
                // the trade taken: a derived ceiling has more to say than a typed one, and
                // saying it by ellipsis would half-say the only thing that tells two
                // identical declarations apart.
                progress.budget.derivedLimitPercentage?.let { percentage ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DerivedLimitMark(
                            percentage = percentage,
                            source = progress.recurringLabel,
                            // Bounded by the line it sits on, so a name longer than the
                            // row is wide ellipsises instead of overflowing.
                            modifier = Modifier.weight(weight = 1f, fill = false),
                        )
                    }
                }

                // The ceiling is unweighted and so is measured first: the identity yields
                // the room, never the figure.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progress.budget.title,
                            style = typography.titleMedium,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Truncates before it pushes the glyph off the line: a budget
                            // is recognisable from its first words and from the chip
                            // beside it, while the mark of going over is the whole state.
                            modifier = Modifier.weight(weight = 1f, fill = false),
                        )

                        // Going over, as a glyph with the state spelled in its content
                        // description. It is not optional decoration: the ring saturates,
                        // and with the ceiling as the hero figure there is no label left
                        // to swap from "remaining" to "exceeded by".
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

                    Text(
                        text = formatter.format(progress.limitAmount),
                        modifier = Modifier.testTag("budget_limit_amount"),
                        style = LIMIT_STYLE,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryStack(categories = progress.budget.categories)

                    Spacer(modifier = Modifier.weight(1f))

                    // This row is a surface of its own grammar in the sense of
                    // `money-display`: it has the width of one amount and no more, so
                    // where the spending gathers a part no rate reaches it shows the
                    // absence mark rather than the parts. The detail sheet is the surface
                    // with the room, and it still shows them.
                    Text(
                        text = formatter.formatOrUnresolved(progress.spentAmount),
                        modifier = Modifier.testTag("budget_spent_amount"),
                        style = typography.labelLarge,
                        color = if (progress.isExceeded) {
                            budgetProgressColor(progress = 1f)
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    // The one label the row carries, and it is allowed precisely because
                    // there are *two* money figures stacked here: it says which of the two
                    // this is, rather than naming the nature of a figure that stands alone.
                    Text(
                        text = stringResource(Res.string.budgets_row_spent),
                        style = typography.labelLarge,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
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
                // Yields before the percentage, which is unweighted: what is left of
                // the line after the glyph and the share belongs to the name.
                modifier = Modifier.weight(weight = 1f, fill = false),
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
) {
    val trackColor = colorScheme.surfaceContainerHighest

    Box(
        modifier = Modifier
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
 * The ceiling's own style — a step above the identity in size and in weight, because it is
 * the figure the row exists to state.
 *
 * Its line height is [androidx.compose.material3.Typography.titleMedium]'s, which is what
 * sets the row's height: the two text lines together (24 + [ROW_LINE_GAP] + 20) come to
 * 46dp, and every variant of the row measures that same 46dp — exceeded and not, resolved
 * and not. A **derived** ceiling is the one exception, and a declared one: its declaration
 * takes a third line, and the row grows by it rather than half-saying it.
 */
private val LIMIT_STYLE
    @Composable get() = typography.titleMedium.copy(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )

/**
 * The ring, and the chip inside it.
 *
 * The ring does **not** govern the row's height: the text column measures 46dp and clears
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

