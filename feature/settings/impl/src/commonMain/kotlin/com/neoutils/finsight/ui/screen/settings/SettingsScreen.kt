@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.settings_base_currency_picker_title
import com.neoutils.finsight.resources.settings_base_currency_title
import com.neoutils.finsight.resources.settings_currencies_subtitle
import com.neoutils.finsight.resources.settings_currencies_title
import com.neoutils.finsight.resources.settings_exchange_rates_subtitle
import com.neoutils.finsight.resources.settings_exchange_rates_title
import com.neoutils.finsight.resources.settings_screen_title
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyOption
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyPickerModal
import com.neoutils.finsight.ui.component.CurrencyGlyphIcon
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesDetail
import com.neoutils.finsight.ui.util.isExtraWideWindow
import com.neoutils.finsight.ui.util.isWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings: the base currency, said once, and the way to the rate archive.
 *
 * The base currency is seeded from the device's locale on the first run, never moves on
 * its own, and **is switched here** — the row is the door, and it opens the shared
 * `CurrencyPickerModal` over the whole curated catalog.
 *
 * **The switch costs nothing up front** (design D6): no confirmation, no coverage
 * calculation, no blocking a currency the archive cannot reach and no rate demanded in
 * the flow. Switching to a currency the archive never priced makes consolidated figures
 * degrade into per-currency terms — behaviour that is already defined and already
 * tested, reached here through another door — and it is reversible at any moment,
 * because no stored row changes.
 *
 * It used to carry a paragraph explaining what the base is *not* used for. The
 * explanation was true and it was the wrong shape: two lines of prose under a 48dp row
 * is not a thing this app's screens do, and a settings screen of two entries cannot
 * afford to read like documentation. Where the distinction actually bites — a figure
 * that mixes currencies — the app already says so at the figure itself, with `≈` and
 * the badge that explains it.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenExchangeRates: () -> Unit = {},
    onOpenExchangeRateHistory: (currency: String?) -> Unit = {},
    onOpenCurrencies: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailController = LocalDetailPaneController.current
    val isExtraWide = isExtraWideWindow()

    LaunchedEffect(Unit) {
        analytics.logScreenView("settings")
    }

    // Presentation is decided at click time from the window width, exactly as Support decides it
    // for a conversation: in an extra-wide window the archive opens in the detail pane, beside the
    // settings it belongs to; otherwise it navigates full-screen, preserving the NavHost transition.
    val openExchangeRates: () -> Unit = {
        if (isExtraWide) detailController.show(ExchangeRatesDetail(onOpenExchangeRateHistory))
        else onOpenExchangeRates()
    }

    // The pane is app-scoped, so an archive opened here would linger when navigating to another
    // feature. Dismiss it when leaving Settings (this screen leaves composition).
    DisposableEffect(Unit) {
        onDispose {
            if (detailController.current is ExchangeRatesDetail) detailController.dismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.settings_screen_title)) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BaseCurrencySection(
                uiState = uiState,
                onSwitch = { viewModel.onAction(SettingsAction.SwitchBaseCurrency(it)) },
            )

            HorizontalDivider(color = colorScheme.outlineVariant)

            ExchangeRatesRow(onClick = openExchangeRates)

            HorizontalDivider(color = colorScheme.outlineVariant)

            CurrenciesRow(onClick = onOpenCurrencies)
        }
    }
}

@Composable
private fun BaseCurrencySection(
    uiState: SettingsUiState,
    onSwitch: (String) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val pickerTitle = stringResource(Res.string.settings_base_currency_picker_title)
    val options = uiState.selectableCurrencies.map {
        CurrencyOption(code = it.code, symbol = it.symbol, name = it.name ?: it.code)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Clipped before it is made clickable, so the ripple takes the row's corners —
        // the same shape `ExchangeRatesRow` below already wears.
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                modalManager.show(
                    CurrencyPickerModal(
                        title = pickerTitle,
                        currencies = options,
                        selectedCode = uiState.baseCurrencyCode,
                        onCurrencySelected = { onSwitch(it.code) },
                    )
                )
            }
            .padding(vertical = 8.dp),
    ) {
        CurrencyGlyph(symbol = uiState.baseCurrency?.symbol ?: uiState.baseCurrencyCode)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_base_currency_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = uiState.baseCurrency
                    ?.let { "${it.code} · ${it.name ?: it.code}" }
                    ?: uiState.baseCurrencyCode,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The way to the registry of currencies — the set the app offers, which is data the user
 * owns rather than a list the app ships.
 *
 * It sits below the archive deliberately: the archive is what a user comes to settings
 * for, and registering a currency is what they do once, when the one they need is not
 * offered yet.
 */
@Composable
private fun CurrenciesRow(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        CurrencyGlyphIcon(Icons.Default.Payments)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_currencies_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.settings_currencies_subtitle),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExchangeRatesRow(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Clipped before it is made clickable, so the ripple takes the row's corners
        // instead of running square to the edges of the content column.
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        CurrencyGlyphIcon(Icons.Default.CurrencyExchange)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_exchange_rates_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.settings_exchange_rates_subtitle),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
        )
    }
}
