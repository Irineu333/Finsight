package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.screen.archivedRecurring.ArchivedRecurringScreen
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen
import kotlinx.serialization.Serializable

@Serializable
data object RecurringGraph : NavGraphRoute

// Internal destination: only reached from within the recurring feature, so it lives in
// the impl rather than the api, which declares only what is navigable from outside.
@Serializable
data object ArchivedRecurringRoute : NavRoute

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

        composable<ArchivedRecurringRoute> {
            val navController = LocalNavController.current

            ArchivedRecurringScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
