package com.neoutils.finsight.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.money_approximate_see_rates
import com.neoutils.finsight.resources.money_unresolved_list_notice
import com.neoutils.finsight.ui.theme.Warning
import org.jetbrains.compose.resources.stringResource

/**
 * **The same news as [ConsolidationBadge]'s gravest level, said once for a whole list.**
 *
 * A row that had to fall back on the absence mark did so because a currency has no rate —
 * a fact about the **archive**, not about that row. Where several rows share the cause,
 * they share the explanation: a badge on each of them would spend width on every row of
 * the list to explain a case that is rare and identical wherever it appears.
 *
 * **Why a sibling and not a second shape of the badge.** The badge decides *for itself*
 * whether it appears and at which of the three levels, by deriving `ConsolidationNotice`
 * from the figures it was given — and that derivation is exactly what this does not want.
 * A converted figure is still one number, and a list is not owed a permanent banner for
 * it; what earns a whole line across the top is the one level at which a surface stopped
 * doing part of its job. So the caller declares that condition and this states it, while
 * the badge goes on grading the three levels wherever a single figure carries them.
 *
 * It is a **line of the list**, not a bar pinned above it: it scrolls away with the rows it
 * describes and takes no permanent height. Whether it appears at all is the caller's to
 * decide — an item that composes to nothing still costs the list's spacing.
 *
 * @param onSeeRates the way to the rate archive, which is what makes the notice actionable
 * rather than merely apologetic. The whole line carries it, and the trailing words name it.
 */
@Composable
fun ConsolidationListNotice(
    onSeeRates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Warning.copy(alpha = 0.1f))
            .border(width = 1.dp, color = Warning.copy(alpha = 0.4f), shape = shape)
            .clickable(onClick = onSeeRates)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            // Named by the sentence beside it, which is on the same line and always
            // present: describing the glyph too would have the notice say it twice.
            contentDescription = null,
            tint = Warning,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.money_unresolved_list_notice),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.money_approximate_see_rates),
            style = typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.primary,
        )
    }
}
