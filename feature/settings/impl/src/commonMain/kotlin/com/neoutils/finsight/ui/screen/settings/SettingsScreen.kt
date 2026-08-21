@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.backup.api.BackupRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.settings_backup_subtitle
import com.neoutils.finsight.resources.settings_backup_title
import com.neoutils.finsight.resources.settings_base_currency_picker_title
import com.neoutils.finsight.resources.settings_base_currency_title
import com.neoutils.finsight.resources.settings_currencies_subtitle
import com.neoutils.finsight.resources.settings_currencies_title
import com.neoutils.finsight.resources.settings_exchange_rates_subtitle
import com.neoutils.finsight.resources.settings_exchange_rates_title
import com.neoutils.finsight.resources.settings_group_currency_data
import com.neoutils.finsight.resources.settings_group_data
import com.neoutils.finsight.resources.settings_group_preferences
import com.neoutils.finsight.resources.settings_screen_title
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.GlyphIcon
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyOption
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyPickerModal
import com.neoutils.finsight.ui.theme.SettingsTileTheme
import com.neoutils.finsight.ui.util.isWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings: the base currency, said once, the way to the rate archive, and the way out of
 * the app entirely — the backup file.
 *
 * Every entry is a `SettingsMenuLink` — one anatomy of glyph, title, subtitle and
 * chevron, dressed as the card the rest of the app renders (see [SettingsTileTheme]).
 *
 * Three groups, because they are three subjects, in widening scope: the base currency is a
 * display preference; the archive and the registry are data the user maintains inside the
 * app; backup is every piece of that data at once, leaving the app or coming back into it.
 * Inside the second group the archive comes first — registering a currency is what a user
 * does once. Backup is last because it is the only entry here that does not settle
 * anything about how the app behaves, and because restoring replaces everything the two
 * groups above it configure.
 *
 * The base currency is seeded from the device's locale on first run and switched here,
 * through the shared `CurrencyPickerModal` over the whole curated catalog. **The switch
 * costs nothing up front** (design D6): no confirmation and no rate demanded. Switching
 * to a currency the archive never priced degrades consolidated figures into per-currency
 * terms — defined and tested elsewhere — and it is reversible, because no stored row
 * changes.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onOpenExchangeRates: () -> Unit = {},
    onOpenCurrencies: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        analytics.logScreenView("settings")
    }

    Scaffold(
        modifier = Modifier.testTag("screen_settings"),
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
        SettingsTileTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.settings_group_preferences)) },
                ) {
                    BaseCurrencyTile(
                        uiState = uiState,
                        onSwitch = { viewModel.onAction(SettingsAction.SwitchBaseCurrency(it)) },
                    )
                }

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.settings_group_currency_data)) },
                ) {
                    ExchangeRatesTile(onClick = onOpenExchangeRates)
                    CurrenciesTile(onClick = onOpenCurrencies)
                }

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.settings_group_data)) },
                ) {
                    BackupTile(onClick = { navController.navigate(BackupRoute) })
                }
            }
        }
    }
}

@Composable
private fun BaseCurrencyTile(
    uiState: SettingsUiState,
    onSwitch: (String) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val pickerTitle = stringResource(Res.string.settings_base_currency_picker_title)

    SettingsMenuLink(
        modifier = Modifier.testTag("settings_base_currency"),
        shape = TileShape,
        icon = { CurrencyGlyph(symbol = uiState.baseCurrency?.symbol ?: uiState.baseCurrencyCode) },
        title = { Text(text = stringResource(Res.string.settings_base_currency_title)) },
        subtitle = {
            Text(
                text = uiState.baseCurrency
                    ?.let { "${it.code} · ${it.name ?: it.code}" }
                    ?: uiState.baseCurrencyCode,
                modifier = Modifier.testTag("settings_base_currency_value"),
            )
        },
        action = { Chevron() },
        onClick = {
            modalManager.show(
                CurrencyPickerModal(
                    title = pickerTitle,
                    currencies = uiState.selectableCurrencies.map {
                        CurrencyOption(
                            code = it.code,
                            symbol = it.symbol,
                            name = it.name ?: it.code
                        )
                    },
                    selectedCode = uiState.baseCurrencyCode,
                    onCurrencySelected = { onSwitch(it.code) },
                )
            )
        },
    )
}

@Composable
private fun ExchangeRatesTile(onClick: () -> Unit) {
    SettingsMenuLink(
        modifier = Modifier.testTag("settings_exchange_rates"),
        shape = TileShape,
        icon = { GlyphIcon(Icons.Default.CurrencyExchange) },
        title = { Text(text = stringResource(Res.string.settings_exchange_rates_title)) },
        subtitle = { Text(text = stringResource(Res.string.settings_exchange_rates_subtitle)) },
        action = { Chevron() },
        onClick = onClick,
    )
}

/** The registry of currencies — the set the app offers, which is data the user owns. */
@Composable
private fun CurrenciesTile(onClick: () -> Unit) {
    SettingsMenuLink(
        modifier = Modifier.testTag("settings_currencies"),
        shape = TileShape,
        icon = { GlyphIcon(Icons.Default.Payments) },
        title = { Text(text = stringResource(Res.string.settings_currencies_title)) },
        subtitle = { Text(text = stringResource(Res.string.settings_currencies_subtitle)) },
        action = { Chevron() },
        onClick = onClick,
    )
}

/**
 * The way to the backup screen, which lives in another feature: settings only names the
 * route, and the icon is the archive-with-a-turning-arrow rather than a cloud — the file
 * goes wherever the user puts it, and the app keeps no copy anywhere.
 */
@Composable
private fun BackupTile(onClick: () -> Unit) {
    SettingsMenuLink(
        modifier = Modifier.testTag("settings_backup"),
        shape = TileShape,
        icon = { GlyphIcon(Icons.Outlined.SettingsBackupRestore) },
        title = { Text(text = stringResource(Res.string.settings_backup_title)) },
        subtitle = { Text(text = stringResource(Res.string.settings_backup_subtitle)) },
        action = { Chevron() },
        onClick = onClick,
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}

/** The corner every card of this app wears. */
private val TileShape = RoundedCornerShape(12.dp)
