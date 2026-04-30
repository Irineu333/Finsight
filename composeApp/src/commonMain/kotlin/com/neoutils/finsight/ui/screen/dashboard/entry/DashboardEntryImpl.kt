package com.neoutils.finsight.ui.screen.dashboard.entry

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.screen.dashboard.DashboardEntry
import com.neoutils.finsight.ui.screen.dashboard.DashboardScreen
import com.neoutils.finsight.ui.screen.home.HomeRoute

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
