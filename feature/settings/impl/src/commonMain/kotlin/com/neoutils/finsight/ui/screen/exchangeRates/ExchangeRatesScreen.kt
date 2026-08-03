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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rates_add
import com.neoutils.finsight.resources.exchange_rates_currency_not_covered
import com.neoutils.finsight.resources.exchange_rates_sync_never
import com.neoutils.finsight.resources.exchange_rates_empty
import com.neoutils.finsight.resources.exchange_rates_group_header
import com.neoutils.finsight.resources.exchange_rates_open_history
import com.neoutils.finsight.resources.exchange_rates_screen_title
import com.neoutils.finsight.ui.component.ExchangeRateRow
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import com.neoutils.finsight.ui.theme.Warning
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
    onOpenHistory: (currency: String?) -> Unit = {},
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
                // Always: this is a sub-destination pushed by Settings, not a tab root, so the
                // rail is not its way back. Hiding it in wide windows stranded the archive
                // between the two breakpoints — the rail is shown from 600dp, but the pane that
                // would host it instead only exists from 840dp.
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenHistory(null) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(Res.string.exchange_rates_open_history),
                        )
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
        ExchangeRatesContent(
            uiState = uiState,
            onOpenHistory = onOpenHistory,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * The archive itself — the upkeep's state, the rate in force for each pair, and the two
 * states it can be in instead. [ExchangeRatesScreen] owns the chrome around it: the top
 * bar, the way to the history and the button that adds a rate.
 *
 * **The archive has one presentation, and it is a route.** It used to open in the detail
 * pane in extra-wide windows, beside the settings screen that led to it, and that is gone:
 * the archive is a place the user goes to and works in — filtering, correcting, removing —
 * not a thing glanced at beside something else, and a second presentation of it was a
 * second set of states to keep true for no question it answered better.
 *
 * **Nothing here leaks into a figure.** The ban on loading states is about a consolidated
 * figure — a balance may carry no spinner and may not fail — and this screen is not a
 * figure: it is the archive explaining itself. No other surface of the app shows the state
 * of the synchronisation.
 */
@Composable
private fun ExchangeRatesContent(
    uiState: ExchangeRatesUiState,
    onOpenHistory: (currency: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // The archive used to render an empty list while it loaded — a screen with
        // nothing on it but a button, which reads as "there is nothing", the one
        // thing it does not yet know.
        uiState.isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> Column(modifier = modifier) {
            // **Above the list and outside it**, because it speaks about the whole archive
            // and not about whatever follows it. Inside the list it sat in the slot a group
            // heading occupies, dressed like one — and since it states a date, the screen
            // read as though the rates were grouped by day.
            SyncStatusLine(status = uiState.sync)

            uiState.sync.notCoveredCurrencies.forEach { currency ->
                NotCoveredNotice(
                    currency = currency,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (uiState.isEmpty) {
                EmptyState(modifier = Modifier.fillMaxSize())
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                uiState.groups.forEach { group ->
                    item(key = "group-${group.counterCurrency}") {
                        Text(
                            text = stringResource(
                                Res.string.exchange_rates_group_header,
                                group.counterCurrency,
                            ),
                            style = typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }

                    // One row per pair, in the direction it was observed in, each declaring
                    // the pair, the value, the date and the origin of the observation that
                    // answers for it. Tapping reaches that pair's history — where
                    // correcting and removing live, over the observation itself.
                    items(group.rates, key = { it.rate.id }) { item ->
                        ExchangeRateRow(
                            rate = item.rate,
                            isOutdated = item.isOutdated,
                            onClick = { onOpenHistory(item.rate.currency) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * That the archive has **never** been brought up to date — and nothing at all once it has.
 *
 * **It speaks only in the state the user can act on.** Stating the date of every successful
 * round put a line of chrome above the archive that says nothing on the day it is read: the
 * upkeep working is the ordinary case, and announcing the ordinary case every time is how a
 * screen stops being read. *Never updated* is the other thing entirely — it is the one
 * state where the rates on screen are only the ones the user put there, and it is worth a
 * word.
 *
 * **Never a loading state**, either way: what is shown is a fact already persisted, and a
 * failed synchronisation is simply an instant that did not move.
 */
@Composable
private fun SyncStatusLine(status: RateSyncStatus) {
    if (status.lastSyncedOn != null) return

    // **An icon beside the words, on purpose.** A bare line of `onSurfaceVariant` text is
    // precisely what a group heading looks like in this app, so as a bare line this was
    // read as one. The icon is what makes it structurally a status and not a heading,
    // before a single word is read.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(Res.string.exchange_rates_sync_never),
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A currency the source does not quote, said out loud with what to do about it.
 *
 * It is a **second** state and not the same one as *not updated yet*: waiting fixes the
 * first and never the second, so collapsing them would leave the user in the worst case
 * with nothing explaining why.
 */
@Composable
private fun NotCoveredNotice(currency: String, modifier: Modifier = Modifier) {
    Surface(
        color = Warning.copy(alpha = 0.14f),
        contentColor = Warning,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.exchange_rates_currency_not_covered, currency),
            style = typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
