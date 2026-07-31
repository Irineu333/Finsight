@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rates_add
import com.neoutils.finsight.resources.exchange_rates_base_hint
import com.neoutils.finsight.resources.exchange_rates_empty
import com.neoutils.finsight.resources.exchange_rates_outdated
import com.neoutils.finsight.resources.exchange_rates_screen_title
import com.neoutils.finsight.resources.exchange_rates_source_derived
import com.neoutils.finsight.resources.exchange_rates_source_user
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.util.isWideWindow
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The rate archive: what each currency was worth against the base, on which day, and
 * where that came from.
 *
 * **It is a list of this app, and it is built like every other one** — a card per row,
 * on `surfaceContainer`, led by the accent box that every leading glyph of this app
 * lives in, with the identity on the left and the money on the right. It used to be a
 * bare two-line text block on `surfaceContainerHighest`, with the quote crammed into
 * the title and the metadata trailing it as a third clause: the anatomy of a log line,
 * in an app whose every other list is cards. The vocabulary was already next door — the
 * settings screen states the base currency with exactly this glyph — and the archive
 * simply had not spoken it.
 *
 * The **date is always shown**, out of date or not — a rate is an observation about a
 * day, and hiding when it is from is hiding what it means. Staleness is stated with a
 * word as well as a colour, for the same reason "archived" is on a category card:
 * colour alone fails for anyone who does not read colour. It wears the pill the account
 * card established for "default", because that is what this app's badges look like.
 *
 * The base is named **once, above the list**, and not in every row. Every quote here is
 * so much base per one unit, so repeating it twenty times is noise — but leaving it
 * unsaid makes the column of money on the right mean nothing at all.
 */
@Composable
fun ExchangeRatesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExchangeRatesViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("exchange_rates")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.exchange_rates_screen_title)) },
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
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { modalManager.show(ExchangeRateFormModal()) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.exchange_rates_add),
                )
            }
        },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)

        when {
            // The archive used to render an empty list while it loaded — a screen with
            // nothing on it but a button, which reads as "there is nothing", the one
            // thing it does not yet know.
            uiState.isLoading -> {
                Box(modifier = content, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.isEmpty -> EmptyState(modifier = content)

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    modifier = content,
                ) {
                    item(key = "base") {
                        Text(
                            text = stringResource(
                                Res.string.exchange_rates_base_hint,
                                uiState.baseCurrency,
                            ),
                            style = typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }

                    items(uiState.rates, key = { it.rate.id }) { item ->
                        ExchangeRateCard(
                            item = item,
                            baseCurrency = uiState.baseCurrency,
                            onClick = { modalManager.show(ExchangeRateFormModal(item.rate)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateCard(
    item: ExchangeRateItem,
    baseCurrency: String,
    onClick: () -> Unit,
) {
    val formatter = LocalCurrencyFormatter.current
    val code = item.rate.currency

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            CurrencyGlyph(symbol = CurrencyCatalog.symbolOf(code))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.currency?.let { "${stringUiText(it.name)} · $code" } ?: code,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = LocalDateFormats.current.monthDayYear.format(item.rate.date),
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    SourceLabel(
                        source = item.rate.source,
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )

                    if (item.isOutdated) OutdatedBadge()
                }
            }

            // A rate **is** money: so many of the base per one unit of the currency. So
            // it reads through the app's own money formatter, in the base, and it sits
            // where this app puts money — at the end of the row.
            Text(
                text = formatter.format(item.rate.rate, baseCurrency),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Provenance in the shape `CategoryCard` established: a 16dp icon plus `labelSmall`. */
@Composable
private fun SourceLabel(
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
                ExchangeRate.Source.USER -> Icons.Default.ModeEdit
            },
            contentDescription = null,
            // The accent, not a signal: the two provenances differ by icon and by the
            // word beside them, never by colour. Reading grey here made a screen the
            // app owns look like one it disabled.
            tint = colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(
                when (source) {
                    ExchangeRate.Source.DERIVED -> Res.string.exchange_rates_source_derived
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
 * The colour is the signal and the word is the signal; neither stands alone, which is
 * the same doctrine the category card applies to "archived". The pill is the one the
 * account card wears for "default" — a badge of this app looks like this.
 */
@Composable
private fun OutdatedBadge() {
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

/** The shape every empty list of this app takes: the subject's own icon, then the word. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CurrencyExchange,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(40.dp),
        )
        Text(
            text = stringResource(Res.string.exchange_rates_empty),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
