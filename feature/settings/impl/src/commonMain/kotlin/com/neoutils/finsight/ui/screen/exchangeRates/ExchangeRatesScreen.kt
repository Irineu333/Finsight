@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.decimal_separator
import com.neoutils.finsight.resources.exchange_rates_add
import com.neoutils.finsight.resources.exchange_rates_empty
import com.neoutils.finsight.resources.exchange_rates_outdated
import com.neoutils.finsight.resources.exchange_rates_quote
import com.neoutils.finsight.resources.exchange_rates_screen_title
import com.neoutils.finsight.resources.exchange_rates_source_derived
import com.neoutils.finsight.resources.exchange_rates_source_user
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.util.isWideWindow
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.formatRate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The rate archive: what each currency was worth against the base, on which day, and
 * where that came from.
 *
 * The **date is always shown**, out of date or not — a rate is an observation about a
 * day, and hiding when it is from is hiding what it means. Staleness is stated with a
 * word as well as a colour, for the same reason "archived" is on a category card:
 * colour alone fails for anyone who does not read colour.
 */
@Composable
fun ExchangeRatesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExchangeRatesViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val separator = stringResource(Res.string.decimal_separator)

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
        if (uiState.isEmpty) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
            ) {
                Text(
                    text = stringResource(Res.string.exchange_rates_empty),
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            items(uiState.rates, key = { it.rate.id }) { item ->
                ExchangeRateRow(
                    item = item,
                    baseCurrency = uiState.baseCurrency,
                    separator = separator,
                    onClick = { modalManager.show(ExchangeRateFormModal(item.rate)) },
                )
            }
        }
    }
}

@Composable
private fun ExchangeRateRow(
    item: ExchangeRateItem,
    baseCurrency: String,
    separator: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(
                    Res.string.exchange_rates_quote,
                    item.rate.currency,
                    formatRate(item.rate.rate, separator),
                    baseCurrency,
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = LocalDateFormats.current.monthDayYear.format(item.rate.date),
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )

                SourceLabel(item.rate.source)

                if (item.isOutdated) {
                    // The colour is the signal and the word is the signal; neither
                    // stands alone, which is the same doctrine the category card
                    // applies to "archived".
                    Text(
                        text = stringResource(Res.string.exchange_rates_outdated),
                        style = typography.labelSmall,
                        color = Warning,
                    )
                }
            }
        }
    }
}

/** Provenance in the shape `CategoryCard` established: a 16dp icon plus `labelSmall`. */
@Composable
private fun SourceLabel(source: ExchangeRate.Source) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (source) {
                ExchangeRate.Source.DERIVED -> Icons.Default.SwapHoriz
                ExchangeRate.Source.USER -> Icons.Default.ModeEdit
            },
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
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
        )
    }
}
