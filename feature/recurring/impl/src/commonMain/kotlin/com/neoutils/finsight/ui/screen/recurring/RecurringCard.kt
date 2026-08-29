package com.neoutils.finsight.ui.screen.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.formatOrUnresolved
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_expense
import com.neoutils.finsight.resources.recurring_income
import com.neoutils.finsight.resources.recurring_source_unusable
import com.neoutils.finsight.ui.component.CategoryIconBox
import com.neoutils.finsight.ui.icons.VectorLazyIcon
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * One cycle of one rule the user keeps, in the four things that tell it from the next
 * one: what it **is**, where it **posts**, **how much**, and **when**.
 *
 * **It is the only row of this list.** What fills it may have been read from the template
 * that projects the cycle or from the ledger that recorded it, and it is not this
 * component's business which: the choice belongs to the view model, and what arrives here
 * is [RecurringRowUi] — what the row asserts, with the source already resolved away. Two
 * components for four sections put the same facts in different columns and left the
 * height of the list without an owner.
 *
 * **It states no state of its own.** Which section a row is in comes from the heading
 * above it, said once for the whole group; a mark repeated on every row of a group
 * distinguishes no row from its neighbour, which is the test by which this row decides
 * what to assert. *Archived* fails the same test for a second reason: an archived template
 * generates no cycle in any month, so it is not in this list at all, and in the
 * destination where it does live every row is archived.
 *
 * A 2×2 grid rather than a line with a subtitle, because a card's name is long — "Nubank
 * Ultravioleta" — and on one secondary line it would be truncated *after* the day. In
 * columns the moment is always whole, and the pair (figure, moment) read together is the
 * only thing on the screen that states the cycle itself.
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
internal fun RecurringCard(
    row: RecurringRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val typeColor = if (row.direction.isIncome) Income else Expense
    val typeLabel = if (row.direction.isIncome) {
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
            val category = row.category
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
                    icon = VectorLazyIcon(row.direction.directionIcon),
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
                    Text(
                        text = stringUiText(row.identity),
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
                        imageVector = row.direction.directionIcon,
                        contentDescription = typeLabel,
                        tint = typeColor,
                        modifier = Modifier.size(16.dp),
                    )
                }

                SourceLine(source = row.source)
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ROW_LINE_GAP),
            ) {
                // A magnitude, and no sign: this is an item surface, it shows one figure
                // and takes part in no displayed sum. The summary above does not sum these
                // rows, and no column of this screen closes on a total. The organisation
                // into sections is no authorisation to sign one either, whichever source
                // the row was read from.
                //
                // When no account denominates the template the unresolved mark stands in
                // its place, on the same node, so the row keeps its height and the absence
                // is said out loud instead of being said by absence. The cause is on the
                // line below. A row read from the ledger never reaches it: the money moved,
                // and it was recorded in the currency it moved in.
                Text(
                    text = formatter.formatOrUnresolved(row.amount),
                    modifier = Modifier.testTag("recurring_card_amount"),
                    style = typography.titleMedium,
                    color = typeColor,
                    maxLines = 1,
                )

                // The day the template projects, or the date the fact was registered on —
                // one slot, because the two answer the same question about the cycle.
                Text(
                    text = stringUiText(row.moment),
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
 * deleted or archived, and the template cannot post at all. It is said by a glyph and a
 * sentence, never by a tone alone: colour on its own carries no state.
 *
 * The glyph and the tone are what say *unusable*; the words go on saying **which** source,
 * for as long as there is one to name (see [sourceName]).
 *
 * Which is why the glyph carries a description exactly when the sentence is not already
 * it. A source that is merely archived spends the sentence on its name, leaving *unusable*
 * to the glyph and the tone — and a tone is not read at all; a source that is gone has no
 * name, so the sentence *is* the word, and describing the glyph with it too would have the
 * row say it twice.
 */
@Composable
private fun SourceLine(source: RecurringRowSource) {
    val icon: ImageVector
    val text: String
    val color: Color
    val iconDescription: String?

    if (!source.isUsable) {
        icon = Icons.Outlined.LinkOff
        color = Warning
        val unusable = stringResource(Res.string.recurring_source_unusable)
        // The sentence is the last resort, not the branch's answer: it speaks only for the
        // source that is gone, because a source that is merely archived still has a name
        // and the name is what tells two identical labels apart.
        text = source.name ?: unusable
        iconDescription = unusable.takeIf { source.name != null }
    } else {
        color = colorScheme.onSurfaceVariant
        // A usable source is named by the words beside it, and the glyph only says which
        // of the two kinds it is — which the account's own name already carries.
        iconDescription = null
        icon = if (source.isCard) Icons.Default.CreditCard else Icons.Default.AccountBalance
        text = source.name.orEmpty()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
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

/** The glyph of the nature, the one the transaction list already uses for it. */
private val TransactionType.directionIcon: ImageVector
    get() = if (isIncome) {
        Icons.AutoMirrored.Filled.TrendingUp
    } else {
        Icons.AutoMirrored.Filled.TrendingDown
    }

/**
 * The icon's container — the 40dp/radius-8 module of the analytic cards, not the
 * 48dp/radius-12 one of the identity rows.
 *
 * It is what **governs the row's height**, and the row measures 64dp: this constant plus
 * the card's 12dp of padding on each side. Neither text column reaches it — a single line
 * of `Text` measures by its font metrics rather than by the `lineHeight` its style
 * declares, so the pair (`titleMedium`, `labelMedium`) beside it comes to well under 40dp
 * and the chip decides.
 *
 * That is what keeps the height constant across every variant — with a category and
 * without, archived and active, denominated and not, read from the template and read from
 * the ledger: the chip is in all of them and no text ever overtakes it. The list has one
 * height, and `animateItem()` reorders without a jump.
 */
private val CHIP_SIZE = 40.dp

/**
 * Between the two lines of each column, and the same on both so the row reads as one
 * grid rather than as two stacks that happen to sit side by side.
 *
 * It does not decide the height — [CHIP_SIZE] does — but the two are set together
 * because it is what could take that decision away: grow it enough to push a column
 * past 40dp and the height changes hands, and stops being the same in the variants
 * whose columns are shorter.
 */
private val ROW_LINE_GAP = 4.dp
