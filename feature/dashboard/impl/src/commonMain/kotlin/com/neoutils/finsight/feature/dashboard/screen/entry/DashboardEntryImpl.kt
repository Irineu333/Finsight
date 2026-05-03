package com.neoutils.finsight.feature.dashboard.screen.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.core.domain.model.Transaction
import com.neoutils.finsight.feature.dashboard.screen.DashboardEntry
import com.neoutils.finsight.feature.dashboard.screen.DashboardScreen
import com.neoutils.finsight.feature.home.route.HomeRoute

class DashboardEntryImpl : DashboardEntry() {

    override fun NavGraphBuilder.register(
        navController: NavController,
        onOpenTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit,
    ) {
        composable<HomeRoute.Dashboard> {
            DashboardScreen(
                openTransactions = onOpenTransactions,
            )
        }
    }
}
