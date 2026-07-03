package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.support.api.SupportIssueRoute
import com.neoutils.finsight.feature.support.api.SupportRoute
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.support.SupportIssueScreen
import com.neoutils.finsight.ui.screen.support.SupportScreen

fun NavGraphBuilder.supportGraph(navController: NavController) {
    composable<SupportRoute> {
        AnimatedVisibilityScopeProvider {
            SupportScreen(
                onNavigateBack = { navController.navigateUp() },
                onOpenIssue = { issueId -> navController.navigate(SupportIssueRoute(issueId)) },
            )
        }
    }

    composable<SupportIssueRoute> { backStackEntry ->
        AnimatedVisibilityScopeProvider {
            val route = backStackEntry.toRoute<SupportIssueRoute>()
            SupportIssueScreen(
                issueId = route.issueId,
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
