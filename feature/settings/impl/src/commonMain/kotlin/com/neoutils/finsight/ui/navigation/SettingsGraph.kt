package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.feature.settings.api.CurrenciesRoute
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.settings.api.SettingsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.currencies.CurrenciesScreen
import com.neoutils.finsight.ui.screen.exchangeRateHistory.ExchangeRateHistoryRoute
import com.neoutils.finsight.ui.screen.exchangeRateHistory.ExchangeRateHistoryScreen
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesScreen
import com.neoutils.finsight.ui.screen.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatform
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.settingsGraph() {
    navigation<SettingsGraph>(
        startDestination = SettingsRoute,
    ) {
        composable<SettingsRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                SettingsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onOpenExchangeRates = { navController.navigate(ExchangeRatesRoute) },
                    onOpenCurrencies = { navController.navigate(CurrenciesRoute) },
                    onOpenMcp = { navController.navigate(McpRoute) },
                )
            }
        }

        composable<CurrenciesRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                CurrenciesScreen(
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }

        composable<ExchangeRatesRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                ExchangeRatesScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onOpenHistory = { currency ->
                        navController.navigate(ExchangeRateHistoryRoute(currency))
                    },
                )
            }
        }

        // The MCP server's screen, hosted here: Settings holds the single path to the switch,
        // and `impl ⊄ impl` means the destination arrives through the feature's own entry
        // point rather than through a function this module could call. The `NavGraphBuilder`
        // lambda is not `@Composable`, so the entry point is resolved from Koin directly.
        //
        // **Registered only on the desktop**, and by the same axis that hides the tile: the
        // server listens on a port of the desktop process and nothing equivalent exists on
        // mobile. Hiding the entry while leaving the destination registered would leave the
        // route reachable by direct navigation, which the platform axis forbids in both
        // directions.
        if (isDesktop) {
            with(KoinPlatform.getKoin().get<McpEntry>()) { register() }
        }

        // Internal to this graph: the history is reached from the archive's entry view
        // and from nowhere else, so no other feature has any reason to name it.
        composable<ExchangeRateHistoryRoute> { entry ->
            val navController = LocalNavController.current
            val route = entry.toRoute<ExchangeRateHistoryRoute>()

            AnimatedVisibilityScopeProvider {
                ExchangeRateHistoryScreen(
                    onNavigateBack = { navController.navigateUp() },
                    viewModel = koinViewModel { parametersOf(route.currency) },
                )
            }
        }
    }
}
