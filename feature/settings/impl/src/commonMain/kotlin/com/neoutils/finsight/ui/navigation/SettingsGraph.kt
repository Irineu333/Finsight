package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.settings.api.ExchangeRatesRoute
import com.neoutils.finsight.feature.settings.api.SettingsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesScreen
import com.neoutils.finsight.ui.screen.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsGraph : NavGraphRoute

fun NavGraphBuilder.settingsGraph() {
    navigation<SettingsGraph>(
        startDestination = SettingsRoute,
    ) {
        composable<SettingsRoute> {
            val navController = LocalNavController.current

            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onOpenExchangeRates = { navController.navigate(ExchangeRatesRoute) },
            )
        }

        composable<ExchangeRatesRoute> {
            val navController = LocalNavController.current

            ExchangeRatesScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
