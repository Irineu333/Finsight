package com.neoutils.finsight.ui.screen.dashboard

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.domain.model.Transaction

abstract class DashboardEntry {

    abstract fun NavGraphBuilder.register(
        navController: NavController,
        onOpenTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit,
    )
}
