package com.neoutils.finsight.feature.dashboard.impl

import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.feature.dashboard.api.DashboardEntry
import com.neoutils.finsight.ui.navigation.dashboardGraph

internal class DashboardEntryImpl : DashboardEntry {
    context(builder: NavGraphBuilder)
    override fun register() = builder.dashboardGraph()
}
