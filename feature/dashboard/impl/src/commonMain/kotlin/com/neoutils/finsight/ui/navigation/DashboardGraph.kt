package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.dashboard.DashboardRoute
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen
import kotlinx.serialization.Serializable

@Serializable
data object DashboardGraph

fun NavGraphBuilder.dashboardGraph() {
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
