package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.dashboard.api.DashboardEntry
import com.neoutils.finsight.feature.dashboard.api.DashboardGraph
import com.neoutils.finsight.feature.home.api.HomeGraph
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import org.koin.mp.KoinPlatform

fun NavGraphBuilder.homeGraph() {
    val koin = KoinPlatform.getKoin()

    navigation<HomeGraph>(startDestination = DashboardGraph) {
        koin.get<DashboardEntry>().register()

        koin.get<TransactionsEntry>().register()
    }
}
