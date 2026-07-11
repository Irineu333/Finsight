package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.dashboard.api.DashboardGraph
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen

internal fun NavGraphBuilder.dashboardGraph() {
    navigation<DashboardGraph>(
        startDestination = DashboardRoute,
    ) {
        composable<DashboardRoute> {
            AnimatedVisibilityScopeProvider {
                DashboardScreen()
            }
        }
    }
}
