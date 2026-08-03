@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.exchangeRateHistory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rate_history_empty
import com.neoutils.finsight.resources.exchange_rate_history_filter_clear
import com.neoutils.finsight.resources.exchange_rate_history_filter_currency
import com.neoutils.finsight.resources.exchange_rate_history_filter_currency_any
import com.neoutils.finsight.resources.exchange_rate_history_filter_date
import com.neoutils.finsight.resources.exchange_rate_history_filter_date_any
import com.neoutils.finsight.resources.exchange_rate_history_filter_date_range
import com.neoutils.finsight.resources.exchange_rate_history_filter_source
import com.neoutils.finsight.resources.exchange_rate_history_filter_source_any
import com.neoutils.finsight.resources.exchange_rate_history_title
import com.neoutils.finsight.resources.exchange_rates_source_derived
import com.neoutils.finsight.resources.exchange_rates_source_remote
import com.neoutils.finsight.resources.exchange_rates_source_user
import com.neoutils.finsight.ui.component.ExchangeRateRow
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.date.DateRangePickerModal
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every observation the archive holds — the same list the archive has always shown, with
 * a new owner and three filters.
 *
 * It **changed hands, not shape**: the rows are the ones the in-force view used to list,
 * grouped by the counterpart currency, each describing itself whole and none shown
 * inverted with respect to the observation that produced it. Editing and removing are
 * still the `ExchangeRateFormModal`, unchanged.
 *
 * What is new are the filters, and they are not decoration: the automatic upkeep writes a
 * row per pair per day, so without them the removal that exists as the corollary of a rate
 * outliving its operation would stop being reachable in practice.
 */
@Composable
fun ExchangeRateHistoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExchangeRateHistoryViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modalManager = LocalModalManager.current
    val dateFormats = LocalDateFormats.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("exchange_rate_history")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.exchange_rate_history_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterBar(
                uiState = uiState,
                onFilterByDate = viewModel::onFilterByDate,
                onFilterByCurrency = viewModel::onFilterByCurrency,
                onFilterBySource = viewModel::onFilterBySource,
                onClearFilters = viewModel::onClearFilters,
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.isEmpty -> EmptyState(modifier = Modifier.fillMaxSize())

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    uiState.groups.forEach { group ->
                        item(key = "group-${group.date}") {
                            // The date alone, and nothing about currency: every row below
                            // states its own pair on both ends, so a heading naming a
                            // currency would say a third time what the rows already say.
                            Text(
                                text = dateFormats.monthDayYear.format(group.date),
                                style = typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                            )
                        }

                        items(group.rates, key = { it.rate.id }) { item ->
                            ExchangeRateRow(
                                rate = item.rate,
                                isOutdated = item.isOutdated,
                                onClick = { modalManager.show(ExchangeRateFormModal(item.rate)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The three filters, as chips that state what they are narrowing by rather than what they
 * could narrow by: an unset chip reads *any date*, *any currency*, *any source*, so the
 * bar always says what the list below it is.
 */
@Composable
private fun FilterBar(
    uiState: ExchangeRateHistoryUiState,
    onFilterByDate: (LocalDate?, LocalDate?) -> Unit,
    onFilterByCurrency: (String?) -> Unit,
    onFilterBySource: (ExchangeRate.Source?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val modalManager = LocalModalManager.current
    val dateFormats = LocalDateFormats.current
    val filters = uiState.filters

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = filters.start != null || filters.end != null,
            onClick = {
                modalManager.show(
                    DateRangePickerModal(
                        initialStartDate = filters.start,
                        initialEndDate = filters.end,
                        onRangeSelected = { start, end -> onFilterByDate(start, end) },
                    )
                )
            },
            label = {
                Text(
                    text = when {
                        filters.start != null && filters.end != null -> stringResource(
                            Res.string.exchange_rate_history_filter_date_range,
                            dateFormats.monthDayYear.format(filters.start),
                            dateFormats.monthDayYear.format(filters.end),
                        )

                        else -> stringResource(Res.string.exchange_rate_history_filter_date_any)
                    }
                )
            },
            leadingIcon = { FilterLabel(Res.string.exchange_rate_history_filter_date) },
        )

        DropdownFilterChip(
            label = Res.string.exchange_rate_history_filter_currency,
            selectedText = filters.currency
                ?: stringResource(Res.string.exchange_rate_history_filter_currency_any),
            isSelected = filters.currency != null,
            options = uiState.currencies.map { it to it },
            anyOption = stringResource(Res.string.exchange_rate_history_filter_currency_any),
            onSelect = onFilterByCurrency,
        )

        DropdownFilterChip(
            label = Res.string.exchange_rate_history_filter_source,
            selectedText = filters.source?.let { stringResource(it.labelRes()) }
                ?: stringResource(Res.string.exchange_rate_history_filter_source_any),
            isSelected = filters.source != null,
            options = ExchangeRate.Source.entries.map { it.name to stringResource(it.labelRes()) },
            anyOption = stringResource(Res.string.exchange_rate_history_filter_source_any),
            onSelect = { name -> onFilterBySource(name?.let(ExchangeRate.Source::valueOf)) },
        )

        if (filters.isActive) {
            TextButton(onClick = onClearFilters) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.exchange_rate_history_filter_clear),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterLabel(resource: StringResource) {
    Text(
        text = stringResource(resource),
        style = typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DropdownFilterChip(
    label: StringResource,
    selectedText: String,
    isSelected: Boolean,
    options: List<Pair<String, String>>,
    anyOption: String,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = isSelected,
            onClick = { expanded = true },
            label = { Text(text = selectedText) },
            leadingIcon = { FilterLabel(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(anyOption) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** The three origins the filter distinguishes, named exactly as the rows name them. */
private fun ExchangeRate.Source.labelRes() = when (this) {
    ExchangeRate.Source.DERIVED -> Res.string.exchange_rates_source_derived
    ExchangeRate.Source.REMOTE -> Res.string.exchange_rates_source_remote
    ExchangeRate.Source.USER -> Res.string.exchange_rates_source_user
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
            text = stringResource(Res.string.exchange_rate_history_empty),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
