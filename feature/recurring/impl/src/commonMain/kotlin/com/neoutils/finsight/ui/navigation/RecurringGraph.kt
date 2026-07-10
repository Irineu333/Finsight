package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen
import kotlinx.serialization.Serializable

@Serializable
data object RecurringGraph

fun NavGraphBuilder.recurringGraph() {
    navigation<RecurringGraph>(
        startDestination = RecurringRoute,
    ) {
        composable<RecurringRoute> {
            val navController = LocalNavController.current

            RecurringScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
