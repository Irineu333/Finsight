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
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.base.internal.LocalSettingsTextStyles
import com.alorma.compose.settings.ui.base.internal.LocalSettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.mcp.api.IMcpServerStateSource
import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_permission_read_only
import com.neoutils.finsight.resources.mcp_permission_read_write
import com.neoutils.finsight.resources.settings_base_currency_picker_title
import com.neoutils.finsight.resources.settings_base_currency_title
import com.neoutils.finsight.resources.settings_currencies_subtitle
import com.neoutils.finsight.resources.settings_currencies_title
import com.neoutils.finsight.resources.settings_exchange_rates_subtitle
import com.neoutils.finsight.resources.settings_exchange_rates_title
import com.neoutils.finsight.resources.settings_group_currency_data
import com.neoutils.finsight.resources.settings_group_integrations
import com.neoutils.finsight.resources.settings_group_preferences
import com.neoutils.finsight.resources.settings_mcp_state_off
import com.neoutils.finsight.resources.settings_mcp_state_on
import com.neoutils.finsight.resources.settings_mcp_state_port_in_use
import com.neoutils.finsight.resources.settings_mcp_subtitle
import com.neoutils.finsight.resources.settings_mcp_title
import com.neoutils.finsight.resources.settings_screen_title
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.CurrencyGlyphIcon
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyOption
import com.neoutils.finsight.ui.modal.currencyPicker.CurrencyPickerModal
import com.neoutils.finsight.ui.util.isWideWindow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings: the base currency, said once, and the way to the rate archive.
 *
 * Every entry is a `SettingsMenuLink` — one anatomy of glyph, title, subtitle and
 * chevron, dressed as the card the rest of the app renders (see [SettingsTileTheme]).
 *
 * Two groups, because they are two subjects: the base currency is a display preference,
 * the archive and the registry are data the user maintains. Inside the second group the
 * archive comes first — registering a currency is what a user does once.
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
    onOpenMcp: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
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

                // Offered only where the server can exist. The axis is the platform and not the
                // width of the window: a narrow desktop window still owns a port, and a wide
                // phone still owns none.
                if (isDesktop) {
                    SettingsGroup(
                        title = { Text(text = stringResource(Res.string.settings_group_integrations)) },
                    ) {
                        McpTile(onClick = onOpenMcp)
                    }
                }
            }
        }
    }
}

/**
 * What a settings tile of this app looks like, stated once: cards on `surfaceContainer`,
 * with the accent kept for the glyph rather than spent on the title. The tiles read
 * their defaults from these two locals, so a tile added later takes the same look.
 */
@Composable
private fun SettingsTileTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsTileColors provides SettingsTileDefaults.colors(
            containerColor = colorScheme.surfaceContainer,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant,
            actionColor = colorScheme.onSurfaceVariant,
            groupTitleColor = colorScheme.onSurfaceVariant,
        ),
        LocalSettingsTextStyles provides SettingsTileDefaults.textStyles(
            groupTitleStyle = typography.labelLarge,
            titleStyle = typography.titleMedium,
            subtitleStyle = typography.bodyMedium,
        ),
        content = content,
    )
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
        icon = { CurrencyGlyphIcon(Icons.Default.CurrencyExchange) },
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
        icon = { CurrencyGlyphIcon(Icons.Default.Payments) },
        title = { Text(text = stringResource(Res.string.settings_currencies_title)) },
        subtitle = { Text(text = stringResource(Res.string.settings_currencies_subtitle)) },
        action = { Chevron() },
        onClick = onClick,
    )
}

/**
 * The single path to the MCP server's switch — present whether the server is on or off, because
 * an entry that appeared only once enabled would be unreachable to whoever has not enabled it.
 */
@Composable
private fun McpTile(onClick: () -> Unit) {
    // The entry says what the server is doing without the screen having to be opened —
    // `mcp-access-control` requires it, and the reason is that this tile is the only path to the
    // capability: an entry that read the same whether the server was listening or not would make
    // "is it on?" a question you can only answer by navigating.
    val state by koinInject<IMcpServerStateSource>().state.collectAsStateWithLifecycle()

    SettingsMenuLink(
        modifier = Modifier.testTag("settings_mcp"),
        shape = TileShape,
        icon = { CurrencyGlyphIcon(Icons.Default.Hub) },
        title = { Text(text = stringResource(Res.string.settings_mcp_title)) },
        subtitle = { Text(text = mcpTileSubtitle(state)) },
        action = { Chevron() },
        onClick = onClick,
    )
}

/**
 * The three states of the server, each reading as itself on the tile.
 *
 * Failing to bind is neither of the other two: presenting it as on or as off would have the user
 * conclude the state is the one they asked for. Listening also names the level in force, because
 * "on" and "on, and allowed to write" are very different facts about this machine.
 */
@Composable
private fun mcpTileSubtitle(state: McpServerState): String = when (state) {
    is McpServerState.Stopped -> stringResource(
        Res.string.settings_mcp_state_off,
        stringResource(Res.string.settings_mcp_subtitle),
    )

    is McpServerState.Listening -> stringResource(
        Res.string.settings_mcp_state_on,
        stringResource(
            when (state.permission) {
                McpPermission.READ_ONLY -> Res.string.mcp_permission_read_only
                McpPermission.READ_WRITE -> Res.string.mcp_permission_read_write
            },
        ),
    )

    is McpServerState.PortUnavailable ->
        stringResource(Res.string.settings_mcp_state_port_in_use, state.port)
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
