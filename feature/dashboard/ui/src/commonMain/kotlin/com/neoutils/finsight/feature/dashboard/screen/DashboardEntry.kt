package com.neoutils.finsight.feature.dashboard.screen

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.feature.transactions.model.Transaction

abstract class DashboardEntry {

    abstract fun NavGraphBuilder.register(
        navController: NavController,
        onOpenTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit,
    )
}
