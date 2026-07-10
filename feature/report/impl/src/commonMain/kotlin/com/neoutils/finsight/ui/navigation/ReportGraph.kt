package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.report.api.ReportsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.report.ReportRoute
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.ui.screen.report.config.ReportConfigScreen
import com.neoutils.finsight.ui.screen.report.toRoute
import com.neoutils.finsight.ui.screen.report.viewer.ReportViewerScreen
import com.neoutils.finsight.util.PerspectiveTabNavType
import kotlin.reflect.typeOf

fun NavGraphBuilder.reportGraph() {
    navigation<ReportsRoute>(
        startDestination = ReportRoute.Config,
    ) {
        composable<ReportRoute.Config> {
            val navController = LocalNavController.current

            ReportConfigScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToViewer = { params -> navController.navigate(params.toRoute()) },
            )
        }

        composable<ReportRoute.Viewer>(
            typeMap = mapOf(
                typeOf<PerspectiveTab>() to PerspectiveTabNavType()
            )
        ) { backStackEntry ->
            val navController = LocalNavController.current
            val route = backStackEntry.toRoute<ReportRoute.Viewer>()

            ReportViewerScreen(
                route = route,
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
