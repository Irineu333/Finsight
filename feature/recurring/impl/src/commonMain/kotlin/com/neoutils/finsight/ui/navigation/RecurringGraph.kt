package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen

fun NavGraphBuilder.recurringGraph() {
    composable<RecurringRoute> {
        val navController = LocalNavController.current

        RecurringScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
