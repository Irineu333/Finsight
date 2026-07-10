package com.neoutils.finsight.ui.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import com.neoutils.finsight.ui.navigation.accountsGraph
import com.neoutils.finsight.ui.navigation.budgetsGraph
import com.neoutils.finsight.ui.navigation.categoriesGraph
import com.neoutils.finsight.ui.navigation.creditCardsGraph
import com.neoutils.finsight.ui.navigation.dashboardGraph
import com.neoutils.finsight.ui.navigation.recurringGraph
import com.neoutils.finsight.ui.navigation.reportGraph
import com.neoutils.finsight.ui.navigation.supportGraph
import com.neoutils.finsight.ui.navigation.transactionsGraph
import com.neoutils.finsight.ui.screen.dashboard.DashboardRoute
import com.neoutils.finsight.ui.screen.home.HomeRoute

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        navigation<HomeRoute>(startDestination = DashboardRoute) {
            dashboardGraph()

            transactionsGraph()
        }

        categoriesGraph()

        creditCardsGraph()

        accountsGraph()

        budgetsGraph()

        recurringGraph()

        supportGraph()

        reportGraph()
    }
}
