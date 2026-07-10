package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.dashboard.DashboardRoute
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen

fun NavGraphBuilder.dashboardGraph() {
    composable<DashboardRoute> {
        AnimatedVisibilityScopeProvider {
            DashboardScreen()
        }
    }
}
