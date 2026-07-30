@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rate_outdated
import com.neoutils.finsight.resources.exchange_rate_source_operation
import com.neoutils.finsight.resources.exchange_rate_source_user
import com.neoutils.finsight.resources.exchange_rate_value
import com.neoutils.finsight.resources.exchange_rates_empty
import com.neoutils.finsight.resources.exchange_rates_empty_title
import com.neoutils.finsight.resources.exchange_rates_title
import com.neoutils.finsight.ui.component.EmptyStateMessage
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.util.isWideWindow
import com.neoutils.finsight.util.dayMonthYear
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
                title = { Text(text = stringResource(Res.string.exchange_rates_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                    actionIconContentColor = colorScheme.onBackground,
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
            FloatingActionButton(
                onClick = { modalManager.show(ExchangeRateFormModal(base = uiState.baseCurrency)) },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        },
    ) { paddingValues ->
        if (uiState.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage(
                    icon = Icons.Default.CurrencyExchange,
                    title = stringResource(Res.string.exchange_rates_empty_title),
                    description = stringResource(Res.string.exchange_rates_empty),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            items(uiState.rates, key = { "${it.rate.currency}-${it.rate.date}-${it.rate.source}" }) { item ->
                ExchangeRateRow(
                    item = item,
                    baseCurrency = uiState.baseCurrency,
                    onEdit = {
                        modalManager.show(
                            ExchangeRateFormModal(base = uiState.baseCurrency, rate = item.rate)
                        )
                    },
                    onRemove = { viewModel.onAction(ExchangeRatesAction.Remove(item.rate)) },
                )
            }
        }
    }
}

@Composable
private fun ExchangeRateRow(
    item: ExchangeRateUi,
    baseCurrency: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val formatter = LocalCurrencyFormatter.current

    ListItem(
        headlineContent = {
            Text(
                text = stringResource(
                    Res.string.exchange_rate_value,
                    item.rate.currency,
                    formatter.format(item.rate.rate, baseCurrency),
                ),
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The date shows whether the rate is stale or not — a rate without its date
                // says nothing about the figure it governs.
                Text(
                    text = dayMonthYear.format(item.rate.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )

                Provenance(source = item.rate.source)

                // Colour alone cannot carry "out of date" — it fails for anyone who does not
                // read colour, so the word comes with it.
                if (item.isOutdated) {
                    Text(
                        text = stringResource(Res.string.exchange_rate_outdated),
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    )
}

/**
 * Where the rate came from, in the idiom the app already uses for provenance of state: a
 * 16dp icon plus a `labelSmall` in `onSurfaceVariant`. Both glyphs already carry a fixed
 * meaning here — `SwapHoriz` is the transfer icon, `ModeEdit` is "you edited this".
 */
@Composable
private fun Provenance(source: ExchangeRate.Source) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (source) {
                ExchangeRate.Source.OPERATION -> Icons.Default.SwapHoriz
                ExchangeRate.Source.USER -> Icons.Default.ModeEdit
            },
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(
                when (source) {
                    ExchangeRate.Source.OPERATION -> Res.string.exchange_rate_source_operation
                    ExchangeRate.Source.USER -> Res.string.exchange_rate_source_user
                }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}
