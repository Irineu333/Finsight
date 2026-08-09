package com.neoutils.finsight.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rates_outdated
import com.neoutils.finsight.resources.exchange_rates_quote_pair
import com.neoutils.finsight.resources.exchange_rates_source_derived
import com.neoutils.finsight.resources.exchange_rates_source_remote
import com.neoutils.finsight.resources.exchange_rates_source_user
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.RATE_SCALE
import org.jetbrains.compose.resources.stringResource

/**
 * **What one observation of the archive looks like**, in one place, for both surfaces
 * that show one: the in-force view, which lists the row answering for each pair, and the
 * history, which lists every row there is.
 *
 * They ask different questions of the archive and render the answer identically, so the
 * row lives here rather than in either of them — a card on `surfaceContainer`, led by the
 * accent glyph every leading icon of this app lives in, with the quote on top and the
 * provenance underneath.
 *
 * **The row describes itself whole** — `1 USD = 5,50 BRL` — so its meaning does not depend
 * on any heading above it, and it is never shown inverted with respect to the observation
 * that produced it: these screens are also the point of edit, and editing an inverted row
 * would open the correction of a number nobody observed.
 */
@Composable
internal fun ExchangeRateRow(
    rate: ExchangeRate,
    isOutdated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            CurrencyGlyph(symbol = LocalCurrencySymbols.current(rate.currency))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        Res.string.exchange_rates_quote_pair,
                        rate.currency,
                        // As many places as the rate needs, and not the currency's own
                        // two: `0,000691` is a real rate of a currency this app offers,
                        // and two places print it `0,00` — a rate of zero, which says
                        // something else. The maximum only *allows* digits, so an
                        // ordinary rate still reads `5,5`.
                        formatter.formatDecimal(rate.rate, RATE_SCALE),
                        rate.counterCurrency,
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The date is **always** shown, out of date or not: a rate is an
                    // observation about a day, and hiding when it is from hides what it
                    // means.
                    Text(
                        text = LocalDateFormats.current.monthDayYear.format(rate.date),
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    SourceLabel(
                        source = rate.source,
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )

                    if (isOutdated) OutdatedBadge()
                }
            }
        }
    }
}

/** Provenance in the shape `CategoryCard` established: a 16dp icon plus `labelSmall`. */
@Composable
internal fun SourceLabel(
    source: ExchangeRate.Source,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = when (source) {
                ExchangeRate.Source.DERIVED -> Icons.Default.SwapHoriz
                ExchangeRate.Source.REMOTE -> Icons.Default.CloudDownload
                ExchangeRate.Source.USER -> Icons.Default.ModeEdit
            },
            contentDescription = null,
            // The accent, not a signal: the three provenances differ by icon and by the
            // word beside them, never by colour. Reading grey here made a screen the app
            // owns look like one it disabled.
            tint = colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(
                when (source) {
                    ExchangeRate.Source.DERIVED -> Res.string.exchange_rates_source_derived
                    ExchangeRate.Source.REMOTE -> Res.string.exchange_rates_source_remote
                    ExchangeRate.Source.USER -> Res.string.exchange_rates_source_user
                }
            ),
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The colour is the signal and the word is the signal; neither stands alone, which is the
 * same doctrine the category card applies to "archived". The pill is the one the account
 * card wears for "default" — a badge of this app looks like this.
 */
@Composable
internal fun OutdatedBadge() {
    Surface(
        color = Warning.copy(alpha = 0.14f),
        contentColor = Warning,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = stringResource(Res.string.exchange_rates_outdated),
            style = typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
