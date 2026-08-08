package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.ui.model.displayColor
import com.neoutils.finsight.ui.component.MoneyLayout
import com.neoutils.finsight.ui.component.MoneyText
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_spending_card_title
import org.jetbrains.compose.resources.stringResource

/**
 * @param onSeeRates where the badge that explains a missing bar leads. `null` leaves the
 * card without that way out — accepted only where the screen has nowhere to send the user.
 */
@Composable
fun CategorySpendingCard(
    categorySpending: List<CategorySpending>,
    title: String? = null,
    modifier: Modifier = Modifier,
    onSeeRates: (() -> Unit)? = null,
    onCategoryClick: (Category) -> Unit = {}
) {
    // A bar is missing when no share could be taken, and a share needs a whole: one
    // category in a currency no rate reaches leaves the period without one. The card has
    // to say so, because nothing else can — the amounts are exact and carry no mark, so
    // the only trace is what is *not* drawn, and absence explains nothing by itself.
    //
    // Guarded on a non-zero amount so a period where everything is zero — which also has
    // no shares — does not accuse a rate of being missing.
    val hasMissingShare = categorySpending.any { it.percentage == null } &&
        categorySpending.any { spending -> spending.amount.terms.any { it.value != 0.0 } }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = title ?: stringResource(Res.string.category_spending_card_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )

                // The card's top-right corner, which is where every badge in the app sits:
                // one place to look for "why does this read the way it does", found without
                // reading the title first. The user learns one affordance, not one per kind
                // of gap — which is now literally true, since a missing bar is this badge's
                // red level and no longer a component of its own.
                if (onSeeRates != null) {
                    ConsolidationBadge(
                        figures = categorySpending.map { it.amount },
                        onSeeRates = onSeeRates,
                        unresolved = hasMissingShare,
                    )
                }
            }

            categorySpending.forEach { spending ->
                CategorySpendingItem(
                    spending = spending,
                    modifier = Modifier
                        .clickable { onCategoryClick(spending.category) }
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CategorySpendingItem(
    spending: CategorySpending,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // A fixed size, not a square of whatever the row happens to be tall. Sizing the
        // icon off the row made the row's own content decide it, so a row that drops its
        // bar — because the share cannot be taken — shrank its icon too, and the card read
        // as broken rather than as one line short. It is the size `BudgetProgressCard`
        // already uses for the same shape.
        CategoryIconBox(
            category = spending.category,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.size(40.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = spending.category.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The name yields the room, never the figure: a category is
                    // recognisable from its first words and from the icon beside it,
                    // while an amount missing a term is a number that lies (design D20).
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )

                // This row has **no vertical room to give**: its height is set by the
                // icon and the bar under it, so a stacked second term would grow the
                // line and pull the bar out of alignment with every other row of the
                // card. The terms go on one line instead — declared here, drawn there.
                MoneyText(
                    figure = spending.amount,
                    style = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    layout = MoneyLayout.INLINE,
                    // Neutral on purpose: the same card renders spending and income
                    // by category, and the tag names the figure, not the widget's use.
                    modifier = Modifier.testTag("category_spending_amount"),
                )
            }

            // No share, no bar. A share needs a whole, and there is none when some
            // category of the month sits in a currency no rate reaches — a bar at zero
            // would assert it spent nothing, and a bar at one hundred would assert the
            // rest spent nothing (design D9). The amount above is untouched either way:
            // it is the ledger's own figure, in its own currency, and always readable.
            val share = spending.percentage ?: return@Column

            LinearProgressIndicator(
                progress = { (share / 100).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = spending.category.displayColor,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                gapSize = (-4).dp,
            )
        }
    }
}
