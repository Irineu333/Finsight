package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
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
                    onOpenExchangeRateHistory = { currency ->
                        navController.navigate(ExchangeRateHistoryRoute(currency))
                    },
                    onOpenCurrencies = { navController.navigate(CurrenciesRoute) },
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
